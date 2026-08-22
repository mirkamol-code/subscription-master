package com.mirkamolcode.service;

import com.mirkamolcode.entity.PaymentHistory;
import com.mirkamolcode.entity.Subscription;
import com.mirkamolcode.entity.User;
import com.mirkamolcode.model.BillingFrequency;
import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.model.Role;
import com.mirkamolcode.model.SubscriptionCategory;
import com.mirkamolcode.model.SubscriptionStatus;
import com.mirkamolcode.repository.PaymentHistoryRepository;
import com.mirkamolcode.repository.SubscriptionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private PaymentHistoryRepository paymentHistoryRepository;
    @Mock
    private ExchangeRateService exchangeRateService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private NotificationService secondNotificationService;
    private PaymentService underTest;

    @BeforeEach
    void setUp() {
        underTest = new PaymentService(subscriptionRepository, paymentHistoryRepository,
                exchangeRateService, List.of(notificationService, secondNotificationService));
    }

    @Test
    void notifyDuePayments_shouldNotifyEveryActiveSubscriptionDueInTwoDays() {
        // Given
        Subscription first = subscription("Netflix", LocalDate.now().plusDays(2));
        Subscription second = subscription("GitHub", LocalDate.now().plusDays(2));
        when(subscriptionRepository.findByIsDeletedFalseAndStatusAndNextPaymentDate(
                SubscriptionStatus.ACTIVE, LocalDate.now().plusDays(2)))
                .thenReturn(List.of(first, second));

        // When
        underTest.notifyDuePayments();

        // Then
        verify(notificationService).paymentDueSoon(first);
        verify(notificationService).paymentDueSoon(second);
        verify(secondNotificationService).paymentDueSoon(first);
        verify(secondNotificationService).paymentDueSoon(second);
    }

    @Test
    void notifyDuePayments_shouldDoNothing_whenNoPaymentIsDue() {
        when(subscriptionRepository.findByIsDeletedFalseAndStatusAndNextPaymentDate(
                SubscriptionStatus.ACTIVE, LocalDate.now().plusDays(2)))
                .thenReturn(List.of());

        underTest.notifyDuePayments();

        verifyNoInteractions(notificationService, secondNotificationService);
    }

    @Test
    void recordDuePayments_shouldPersistRateSnapshotAndAdvanceBillingDate() {
        // Given
        LocalDate dueDate = LocalDate.now().minusDays(1);
        Subscription subscription = subscription("Netflix", dueDate);
        when(subscriptionRepository.findByIsDeletedFalseAndStatusAndNextPaymentDateLessThanEqual(
                SubscriptionStatus.ACTIVE, LocalDate.now())).thenReturn(List.of(subscription));
        when(exchangeRateService.rateToUzs(CurrencyCode.USD)).thenReturn(new BigDecimal("12500"));

        // When
        underTest.recordDuePayments();

        // Then
        ArgumentCaptor<PaymentHistory> captor = ArgumentCaptor.forClass(PaymentHistory.class);
        verify(paymentHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getPaymentDate()).isEqualTo(dueDate);
        assertThat(captor.getValue().getAmountUzs()).isEqualByComparingTo("162375.00");
        assertThat(subscription.getNextPaymentDate()).isEqualTo(dueDate.plusMonths(1));
    }

    @Test
    void refreshExchangeRates_shouldDelegateToExchangeRateService() {
        underTest.refreshExchangeRates();
        verify(exchangeRateService).refreshDailyRates();
    }

    private Subscription subscription(String name, LocalDate nextPaymentDate) {
        User user = new User("person@example.com", "hash", Set.of(Role.USER));
        return new Subscription(user, name, new BigDecimal("12.99"), CurrencyCode.USD,
                BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE,
                SubscriptionCategory.ENTERTAINMENT, nextPaymentDate.minusMonths(1));
    }
}
