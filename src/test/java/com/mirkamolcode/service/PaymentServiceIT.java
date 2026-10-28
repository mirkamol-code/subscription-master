package com.mirkamolcode.service;

import com.mirkamolcode.AbstractTestConfig;
import com.mirkamolcode.entity.ExchangeRate;
import com.mirkamolcode.entity.Subscription;
import com.mirkamolcode.entity.User;
import com.mirkamolcode.model.BillingFrequency;
import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.model.Role;
import com.mirkamolcode.model.SubscriptionCategory;
import com.mirkamolcode.model.SubscriptionStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentServiceIT extends AbstractTestConfig {

    @Autowired
    private PaymentService underTest;

    @Test
    void recordDuePayments_shouldSkipSubscriptionsWithoutARate_andRecordThemOnTheNextRun() {
        // given CBU is unreachable in the test profile, no USD rate is stored, and two subscriptions are due
        User user = userRepository.save(new User("payer@example.com", "hash", Set.of(Role.USER)));
        LocalDate due = LocalDate.now().minusDays(1);
        Subscription uzs = subscriptionRepository.save(dueSubscription(user, "UZS service", CurrencyCode.UZS, due));
        Subscription usd = subscriptionRepository.save(dueSubscription(user, "USD service", CurrencyCode.USD, due));

        // when
        underTest.recordDuePayments();

        // then the UZS payment is recorded and the USD one is left for later
        assertThat(paymentHistoryRepository.findBySubscriptionIdOrderByPaymentDateDesc(uzs.getId())).hasSize(1);
        assertThat(paymentHistoryRepository.findBySubscriptionIdOrderByPaymentDateDesc(usd.getId())).isEmpty();
        assertThat(subscriptionRepository.findById(usd.getId()).orElseThrow().getNextPaymentDate()).isEqualTo(due);

        // when a USD rate is available for the next run
        exchangeRateRepository.save(new ExchangeRate(CurrencyCode.USD, new BigDecimal("12000.00"), LocalDate.now()));
        underTest.recordDuePayments();

        // then the USD payment is recorded once, and the UZS one is not recorded again
        assertThat(paymentHistoryRepository.findBySubscriptionIdOrderByPaymentDateDesc(usd.getId())).hasSize(1);
        assertThat(paymentHistoryRepository.findBySubscriptionIdOrderByPaymentDateDesc(uzs.getId())).hasSize(1);
    }

    private Subscription dueSubscription(User user, String name, CurrencyCode currency, LocalDate nextPaymentDate) {
        return new Subscription(user, name, new BigDecimal("10.00"), currency, BillingFrequency.MONTHLY,
                SubscriptionStatus.ACTIVE, SubscriptionCategory.ENTERTAINMENT, nextPaymentDate.minusMonths(1), nextPaymentDate);
    }
}
