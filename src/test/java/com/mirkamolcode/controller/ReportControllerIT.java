package com.mirkamolcode.controller;

import com.mirkamolcode.AbstractTestConfig;
import com.mirkamolcode.dto.request.SubscriptionRequest;
import com.mirkamolcode.model.BillingFrequency;
import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.model.SubscriptionCategory;
import com.mirkamolcode.model.SubscriptionStatus;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

class ReportControllerIT extends AbstractTestConfig {

    @Test
    void reports_shouldRejectMissingAuthentication() {
        webTestClient.get()
                .uri(API + "/reports/annual.csv")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void csv_shouldDownloadOnlyAuthenticatedUsersSubscriptions() {
        String ownerAuthorization = bearer(register("owner@example.com"));
        String otherAuthorization = bearer(register("other@example.com"));
        createSubscription(ownerAuthorization, request("Owner service"));
        createSubscription(otherAuthorization, request("Other service"));

        byte[] body = webTestClient.get()
                .uri(API + "/reports/annual.csv")
                .header("Authorization", ownerAuthorization)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.valueOf("text/csv"))
                .expectHeader().valueEquals("Content-Disposition", "attachment; filename=annual-subscription-costs.csv")
                .expectBody()
                .returnResult()
                .getResponseBody();

        String csv = new String(body, StandardCharsets.UTF_8);
        assertThat(csv).contains("Owner service").doesNotContain("Other service");
    }

    @Test
    void xlsx_shouldDownloadAValidOfficeOpenXmlPayload() {
        String authorization = bearer(register("owner@example.com"));
        createSubscription(authorization, request("Owner service"));

        byte[] body = webTestClient.get()
                .uri(API + "/reports/annual.xlsx")
                .header("Authorization", authorization)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.valueOf(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .expectHeader().valueEquals("Content-Disposition", "attachment; filename=annual-subscription-costs.xlsx")
                .expectBody()
                .returnResult()
                .getResponseBody();

        assertThat(body).isNotNull().hasSizeGreaterThan(100);
        assertThat(body[0]).isEqualTo((byte) 'P');
        assertThat(body[1]).isEqualTo((byte) 'K');
    }

    private SubscriptionRequest request(String name) {
        return new SubscriptionRequest(name, BigDecimal.TEN, CurrencyCode.UZS,
                BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE,
                SubscriptionCategory.OTHER, LocalDate.of(2026, 1, 10));
    }
}
