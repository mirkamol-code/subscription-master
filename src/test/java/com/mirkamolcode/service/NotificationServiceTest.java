package com.mirkamolcode.service;

import com.mirkamolcode.entity.Subscription;
import com.mirkamolcode.entity.User;
import com.mirkamolcode.model.BillingFrequency;
import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.model.Role;
import com.mirkamolcode.model.SubscriptionCategory;
import com.mirkamolcode.model.SubscriptionStatus;
import com.mirkamolcode.notification.GmailNotifier;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
    @Mock
    private JavaMailSender mailSender;

    @Test
    void gmailNotifier_shouldSendReminderToSubscriptionOwner() {
        // Given
        Subscription subscription = new Subscription(
                new User("person@example.com", "hash", Set.of(Role.USER)),
                "Netflix", new BigDecimal("12.99"), CurrencyCode.USD,
                BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE,
                SubscriptionCategory.ENTERTAINMENT, LocalDate.of(2026, 8, 23));
        GmailNotifier notifier = new GmailNotifier(mailSender, "billing@example.com");

        // When
        notifier.paymentDueSoon(subscription);

        // Then
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getFrom()).isEqualTo("billing@example.com");
        assertThat(captor.getValue().getTo()).containsExactly("person@example.com");
        assertThat(captor.getValue().getSubject()).contains("Netflix");
        assertThat(captor.getValue().getText()).contains("12.99 USD", "2026-09-23");
    }
}
