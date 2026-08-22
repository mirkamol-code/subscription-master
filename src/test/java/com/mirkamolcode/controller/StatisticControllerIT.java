package com.mirkamolcode.controller;

import com.mirkamolcode.AbstractTestConfig;
import com.mirkamolcode.dto.request.SubscriptionRequest;
import com.mirkamolcode.dto.response.SubscriptionResponse;
import com.mirkamolcode.entity.PaymentHistory;
import com.mirkamolcode.entity.Subscription;
import com.mirkamolcode.model.BillingFrequency;
import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.model.SubscriptionCategory;
import com.mirkamolcode.model.SubscriptionStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class StatisticControllerIT extends AbstractTestConfig {

    @Test
    void statistics_shouldRejectMissingAuthentication() {
        webTestClient.get()
                .uri(API + "/statistics/summary")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void summary_shouldIncludeOnlyAuthenticatedUsersActiveSubscriptions() {
        String ownerAuthorization = bearer(register("owner@example.com"));
        String otherAuthorization = bearer(register("other@example.com"));
        createSubscription(ownerAuthorization, request("Owner service", "10.00"));
        createSubscription(otherAuthorization, request("Other service", "999.00"));

        webTestClient.get()
                .uri(API + "/statistics/summary")
                .header("Authorization", ownerAuthorization)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.monthlyTotalUzs").isEqualTo(10.0)
                .jsonPath("$.mostExpensive.subscriptionName").isEqualTo("Owner service")
                .jsonPath("$.byCategory[0].monthlyCostUzs").isEqualTo(10.0);
    }

    @Test
    void monthlyDynamics_shouldReturnCurrentUsersPaymentHistory_andValidateRange() {
        String authorization = bearer(register("owner@example.com"));
        SubscriptionResponse created = createSubscription(authorization, request("Owner service", "10.00"));
        Subscription subscription = subscriptionRepository.findById(created.id()).orElseThrow();
        paymentHistoryRepository.save(new PaymentHistory(
                subscription, LocalDate.now(), new BigDecimal("10"), CurrencyCode.UZS, BigDecimal.ONE));

        webTestClient.get()
                .uri(API + "/statistics/monthly-dynamics?months=1")
                .header("Authorization", authorization)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].costUzs").isEqualTo(10.0);

        webTestClient.get()
                .uri(API + "/statistics/monthly-dynamics?months=13")
                .header("Authorization", authorization)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("BAD_REQUEST");
    }

    private SubscriptionRequest request(String name, String price) {
        return new SubscriptionRequest(name, new BigDecimal(price), CurrencyCode.UZS,
                BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE,
                SubscriptionCategory.ENTERTAINMENT, LocalDate.of(2026, 1, 10));
    }
}
