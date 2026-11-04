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
        String ownerAuthorization = bearer(signIn("owner@example.com"));
        String otherAuthorization = bearer(signIn("other@example.com"));
        createSubscription(ownerAuthorization, request("Owner service"));
        createSubscription(otherAuthorization, request("Other service"));

        byte[] body = webTestClient.get()
                .uri(API + "/reports/annual.csv")
                .header("Authorization", ownerAuthorization)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.valueOf("text/csv"))
                .expectHeader().valueEquals("Content-Disposition",
                        "attachment; filename=\"subscriptions-annual-report.csv\"; filename*=UTF-8''subscriptions-annual-report.csv")
                .expectBody()
                .returnResult()
                .getResponseBody();

        String csv = new String(body, StandardCharsets.UTF_8);
        assertThat(csv).contains("Owner service").doesNotContain("Other service");
    }

    @Test
    void xlsx_shouldDownloadAValidOfficeOpenXmlPayload() {
        String authorization = bearer(signIn("owner@example.com"));
        createSubscription(authorization, request("Owner service"));

        byte[] body = webTestClient.get()
                .uri(API + "/reports/annual.xlsx")
                .header("Authorization", authorization)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.valueOf(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .expectHeader().valueEquals("Content-Disposition",
                        "attachment; filename=\"subscriptions-annual-report.xlsx\"; filename*=UTF-8''subscriptions-annual-report.xlsx")
                .expectBody()
                .returnResult()
                .getResponseBody();

        assertThat(body).isNotNull().hasSizeGreaterThan(100);
        assertThat(body[0]).isEqualTo((byte) 'P');
        assertThat(body[1]).isEqualTo((byte) 'K');
    }

    @Test
    void csv_shouldExportOnlyChosenColumns() {
        // given
        String authorization = bearer(signIn("owner@example.com"));
        createSubscription(authorization, request("Owner service"));

        // when
        String csv = downloadCsv(authorization, "?columns=SERVICE,PRICE");

        // then
        assertThat(csv.lines()).containsExactly("Service,Price", "Owner service,10.00");
    }

    @Test
    void csv_shouldExportEveryColumn_whenNoColumnsAreGiven() {
        // given
        String authorization = bearer(signIn("owner@example.com"));

        // when
        String csv = downloadCsv(authorization, "");

        // then
        assertThat(csv.lines()).containsExactly(
                "Service,Category,Status,Billing,Price,Currency,Yearly cost,Yearly cost (UZS),Started,Next payment");
    }

    @Test
    void csv_shouldIncludeCancelledButNotHiddenSubscriptions() {
        // given
        String authorization = bearer(signIn("owner@example.com"));
        createSubscription(authorization, new SubscriptionRequest("Cancelled service", BigDecimal.TEN, CurrencyCode.UZS,
                BillingFrequency.MONTHLY, SubscriptionStatus.CANCELLED, SubscriptionCategory.OTHER, LocalDate.of(2026, 1, 10)));
        var hidden = createSubscription(authorization, request("Hidden service"));
        webTestClient.post().uri(API + "/subscriptions/" + hidden.id() + "/hide").header("Authorization", authorization).exchange()
                .expectStatus().is2xxSuccessful();

        // when
        String csv = downloadCsv(authorization, "?columns=SERVICE,STATUS");

        // then
        assertThat(csv.lines()).containsExactly("Service,Status", "Cancelled service,Cancelled");
    }

    @Test
    void csv_shouldRejectEmptyColumnSelection() {
        // given
        String authorization = bearer(signIn("owner@example.com"));

        // when / then
        webTestClient.get().uri(API + "/reports/annual.csv?columns=").header("Authorization", authorization).exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void xlsx_shouldRejectColumnOutsideTheAllowlist() {
        // given
        String authorization = bearer(signIn("owner@example.com"));

        // when / then
        webTestClient.get().uri(API + "/reports/annual.xlsx?columns=SERVICE,PASSWORD").header("Authorization", authorization).exchange()
                .expectStatus().isBadRequest();
    }

    private String downloadCsv(String authorization, String query) {
        byte[] body = webTestClient.get().uri(API + "/reports/annual.csv" + query).header("Authorization", authorization).exchange()
                .expectStatus().isOk()
                .expectBody()
                .returnResult()
                .getResponseBody();
        return new String(body, StandardCharsets.UTF_8);
    }

    private SubscriptionRequest request(String name) {
        return new SubscriptionRequest(name, BigDecimal.TEN, CurrencyCode.UZS,
                BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE,
                SubscriptionCategory.OTHER, LocalDate.of(2026, 1, 10));
    }
}
