package com.mirkamolcode.service;

import com.mirkamolcode.dto.AdminUsage;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatisticServiceTest {
    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private PaymentHistoryRepository paymentHistoryRepository;
    @Mock
    private ExchangeRateService exchangeRateService;
    @Mock
    private CurrentUserService currentUserService;
    @InjectMocks
    private StatisticsService underTest;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("person@example.com", "hash", Set.of(Role.USER));
        ReflectionTestUtils.setField(user, "id", 1L);
    }

    @Test
    void mySummary_shouldAggregateActiveSubscriptionsAndSelectMostExpensive() {
        // Given
        Subscription entertainment = subscription("Netflix", "10", SubscriptionCategory.ENTERTAINMENT);
        Subscription productivity = subscription("Notion", "20", SubscriptionCategory.PRODUCTIVITY);
        when(currentUserService.requiredUser()).thenReturn(user);
        when(subscriptionRepository.findAll(any(Specification.class))).thenReturn(List.of(entertainment, productivity));
        when(exchangeRateService.rateToUzs(CurrencyCode.USD)).thenReturn(new BigDecimal("12500"));

        // When
        var result = underTest.mySummary();

        // Then
        assertThat(result.monthlyTotalUzs()).isEqualByComparingTo("375000.00");
        assertThat(result.mostExpensive().subscriptionName()).isEqualTo("Notion");
        assertThat(result.byCategory()).hasSize(2);
        assertThat(result.byCategory()).extracting(category -> category.category())
                .containsExactly(SubscriptionCategory.ENTERTAINMENT, SubscriptionCategory.PRODUCTIVITY);
    }

    @Test
    void mySummary_shouldReturnZeroAndNoMostExpensive_whenUserHasNoActiveSubscriptions() {
        when(currentUserService.requiredUser()).thenReturn(user);
        when(subscriptionRepository.findAll(any(Specification.class))).thenReturn(List.of());

        var result = underTest.mySummary();

        assertThat(result.monthlyTotalUzs()).isEqualByComparingTo("0.00");
        assertThat(result.mostExpensive()).isNull();
        assertThat(result.byCategory()).isEmpty();
    }

    @Test
    void monthlyDynamics_shouldRejectUnsupportedMonthRange() {
        assertThatThrownBy(() -> underTest.monthlyDynamics(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("months must be between 1 and 12");
        assertThatThrownBy(() -> underTest.monthlyDynamics(13))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void monthlyDynamics_shouldSumOnlyCurrentUsersPaymentHistory() {
        // Given
        Subscription subscription = subscription("Netflix", "10", SubscriptionCategory.ENTERTAINMENT);
        PaymentHistory first = new PaymentHistory(subscription, LocalDate.now(), new BigDecimal("10"), CurrencyCode.USD, new BigDecimal("12500"));
        PaymentHistory second = new PaymentHistory(subscription, LocalDate.now(), new BigDecimal("5"), CurrencyCode.USD, new BigDecimal("12500"));
        when(currentUserService.requiredUser()).thenReturn(user);
        when(paymentHistoryRepository.findBySubscriptionUserIdAndPaymentDateBetween(eq(1L), any(), any()))
                .thenReturn(List.of(first, second));

        // When
        var result = underTest.monthlyDynamics(1);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().costUzs()).isEqualByComparingTo("187500.00");
    }

    @Test
    void serviceUsage_shouldGroupAndSortVisibleSubscriptions() {
        // Given
        when(subscriptionRepository.findAll(any(Specification.class), eq(Sort.by("name"))))
                .thenReturn(List.of(
                        subscription("Netflix", "10", SubscriptionCategory.ENTERTAINMENT),
                        subscription("Netflix", "11", SubscriptionCategory.ENTERTAINMENT),
                        subscription("Notion", "20", SubscriptionCategory.PRODUCTIVITY)));

        // When
        List<AdminUsage> result = underTest.serviceUsage();

        // Then
        assertThat(result).containsExactly(
                new AdminUsage("Netflix", 2),
                new AdminUsage("Notion", 1));
    }

    private Subscription subscription(String name, String price, SubscriptionCategory category) {
        Subscription subscription = new Subscription(user, name, new BigDecimal(price), CurrencyCode.USD,
                BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, category, LocalDate.of(2026, 1, 10));
        ReflectionTestUtils.setField(subscription, "id", (long) Math.abs(name.hashCode()));
        return subscription;
    }
}
