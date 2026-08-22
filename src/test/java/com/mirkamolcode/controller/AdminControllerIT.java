package com.mirkamolcode.controller;

import com.mirkamolcode.AbstractTestConfig;
import com.mirkamolcode.dto.request.SubscriptionRequest;
import com.mirkamolcode.model.BillingFrequency;
import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.model.SubscriptionCategory;
import com.mirkamolcode.model.SubscriptionStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AdminControllerIT extends AbstractTestConfig {

    @Test
    void serviceUsage_shouldReturnForbiddenForNormalUser() {
        String authorization = bearer(register("user@example.com"));

        webTestClient.get()
                .uri(API + "/admin/statistics/service-usage")
                .header("Authorization", authorization)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void serviceUsage_shouldReturnGlobalGroupedCountsForAdmin() {
        String firstUser = bearer(register("first@example.com"));
        String secondUser = bearer(register("second@example.com"));
        createSubscription(firstUser, request("Netflix"));
        createSubscription(secondUser, request("Netflix"));
        createSubscription(secondUser, request("Notion"));
        saveAdmin("admin@example.com");
        String adminAuthorization = bearer(login("admin@example.com"));

        webTestClient.get()
                .uri(API + "/admin/statistics/service-usage")
                .header("Authorization", adminAuthorization)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].serviceName").isEqualTo("Netflix")
                .jsonPath("$[0].subscriptionCount").isEqualTo(2)
                .jsonPath("$[1].serviceName").isEqualTo("Notion")
                .jsonPath("$[1].subscriptionCount").isEqualTo(1);
    }

    private SubscriptionRequest request(String name) {
        return new SubscriptionRequest(name, BigDecimal.TEN, CurrencyCode.UZS,
                BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE,
                SubscriptionCategory.OTHER, LocalDate.of(2026, 1, 10));
    }
}
