package com.mirkamolcode.controller;

import com.mirkamolcode.AbstractTestConfig;
import com.mirkamolcode.dto.request.SubscriptionRequest;
import com.mirkamolcode.dto.response.SubscriptionResponse;
import com.mirkamolcode.entity.User;
import com.mirkamolcode.model.BillingFrequency;
import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.model.SubscriptionCategory;
import com.mirkamolcode.model.SubscriptionStatus;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.reactive.server.WebTestClient;

class AdminPaymentControllerIT extends AbstractTestConfig {
    private static final ZoneId ADMIN_ZONE = ZoneId.of(User.DEFAULT_TIME_ZONE);

    @Autowired
    private JdbcTemplate jdbc;

    private LocalDate today;
    private String admin;
    private String customer;

    @BeforeEach
    void setUp() {
        today = LocalDate.now(ADMIN_ZONE);
        admin = bearer(signInAsAdmin("admin@example.com"));
        customer = bearer(signIn("customer@example.com"));
        jdbc.update("update users set display_name = 'Casey Customer' where email = 'customer@example.com'");
    }

    @Test
    void due_shouldBeForbiddenForNormalUser() {
        // when / then
        webTestClient.get().uri(API + "/admin/payments/due?from=" + today + "&to=" + today.plusDays(7))
                .header("Authorization", customer).exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void due_shouldListRenewingPaymentsInRange_withUserAmountAndBucket() {
        // given
        trackDue("Netflix", today, SubscriptionStatus.ACTIVE, "65000");
        trackDue("Spotify", today.plusDays(2), SubscriptionStatus.ACTIVE, "30000");
        trackDue("Notion", today.plusDays(5), SubscriptionStatus.ACTIVE, "120000");
        trackDue("Canva", today.plusDays(20), SubscriptionStatus.ACTIVE, "90000");
        trackDue("Dropbox", today.plusDays(1), SubscriptionStatus.CANCELLED, "50000");

        // when / then
        due(today, today.plusDays(10), "")
                .jsonPath("$.today").isEqualTo(today.toString())
                .jsonPath("$.timeZone").isEqualTo(User.DEFAULT_TIME_ZONE)
                .jsonPath("$.entries.length()").isEqualTo(3)
                .jsonPath("$.entries[0].merchant").isEqualTo("Netflix")
                .jsonPath("$.entries[0].kind").isEqualTo("PAYMENT")
                .jsonPath("$.entries[0].dueDate").isEqualTo(today.toString())
                .jsonPath("$.entries[0].amount").isEqualTo(65000.0)
                .jsonPath("$.entries[0].amountUzs").isEqualTo(65000.0)
                .jsonPath("$.entries[0].bucket").isEqualTo("TODAY")
                .jsonPath("$.entries[0].userName").isEqualTo("Casey Customer")
                .jsonPath("$.entries[0].userEmail").isEqualTo("customer@example.com")
                .jsonPath("$.entries[1].bucket").isEqualTo("NEXT_3_DAYS")
                .jsonPath("$.entries[2].bucket").isEqualTo("NEXT_7_DAYS");
    }

    @Test
    void due_shouldShowAccessEnd_insteadOfAPaymentThatWillNotHappen() {
        // given
        SubscriptionResponse ending = trackDue("Netflix", today.plusDays(10), SubscriptionStatus.ACTIVE, "65000");
        jdbc.update("update subscriptions set ends_on = ? where id = ?", Date.valueOf(today.plusDays(3)), ending.id());

        // when / then
        due(today, today.plusDays(14), "")
                .jsonPath("$.entries.length()").isEqualTo(1)
                .jsonPath("$.entries[0].kind").isEqualTo("ACCESS_END")
                .jsonPath("$.entries[0].dueDate").isEqualTo(today.plusDays(3).toString())
                .jsonPath("$.entries[0].amount").doesNotExist();
    }

    @Test
    void due_shouldMarkBillingRequiredAsOverdue_andHideHiddenSubscriptions() {
        // given
        trackDue("OpenAI", today.plusDays(2), SubscriptionStatus.BILLING_REQUIRED, "250000");
        SubscriptionResponse hidden = trackDue("Spotify", today.plusDays(2), SubscriptionStatus.ACTIVE, "30000");
        webTestClient.post().uri(API + "/subscriptions/" + hidden.id() + "/hide").header("Authorization", customer).exchange()
                .expectStatus().is2xxSuccessful();

        // when / then
        due(today, today.plusDays(7), "")
                .jsonPath("$.entries.length()").isEqualTo(1)
                .jsonPath("$.entries[0].merchant").isEqualTo("OpenAI")
                .jsonPath("$.entries[0].bucket").isEqualTo("OVERDUE");
    }

    @Test
    void due_shouldNotCallAMissedActivePaymentOverdue_beforeTheStatusRulesDo() {
        // given
        trackDue("Netflix", today.minusDays(1), SubscriptionStatus.ACTIVE, "65000");

        // when / then
        due(today.minusDays(7), today, "")
                .jsonPath("$.entries[0].bucket").isEqualTo("PASSED");
        webTestClient.get().uri(API + "/admin/payments/upcoming").header("Authorization", admin).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.entries.length()").isEqualTo(0);
    }

    @Test
    void due_shouldNeverMarkAnAccessEndOverdue() {
        // given
        SubscriptionResponse ended = trackDue("OpenAI", today.plusDays(20), SubscriptionStatus.BILLING_REQUIRED, "250000");
        jdbc.update("update subscriptions set ends_on = ? where id = ?", Date.valueOf(today.minusDays(2)), ended.id());

        // when / then
        due(today.minusDays(7), today, "")
                .jsonPath("$.entries.length()").isEqualTo(1)
                .jsonPath("$.entries[0].kind").isEqualTo("ACCESS_END")
                .jsonPath("$.entries[0].bucket").isEqualTo("PASSED");
    }

    @Test
    void due_shouldFilterByMerchantAndStatus() {
        // given
        trackDue("Netflix", today.plusDays(1), SubscriptionStatus.ACTIVE, "65000");
        trackDue("Spotify", today.plusDays(1), SubscriptionStatus.BILLING_REQUIRED, "30000");

        // when / then
        due(today, today.plusDays(7), "&merchant=netflix")
                .jsonPath("$.entries.length()").isEqualTo(1)
                .jsonPath("$.entries[0].merchant").isEqualTo("Netflix");
        due(today, today.plusDays(7), "&status=BILLING_REQUIRED")
                .jsonPath("$.entries.length()").isEqualTo(1)
                .jsonPath("$.entries[0].merchant").isEqualTo("Spotify");
    }

    @Test
    void due_shouldRejectInvalidRanges() {
        // when / then
        webTestClient.get().uri(API + "/admin/payments/due?from=" + today + "&to=" + today.minusDays(1))
                .header("Authorization", admin).exchange()
                .expectStatus().isBadRequest();
        webTestClient.get().uri(API + "/admin/payments/due?from=" + today + "&to=" + today.plusDays(200))
                .header("Authorization", admin).exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void upcoming_shouldListOverdueThenNext7DaysByDate_withTotals() {
        // given
        trackDue("Notion", today.plusDays(6), SubscriptionStatus.ACTIVE, "120000");
        trackDue("Netflix", today, SubscriptionStatus.ACTIVE, "65000");
        trackDue("Canva", today.plusDays(7), SubscriptionStatus.ACTIVE, "90000");
        trackDue("OpenAI", today.minusDays(2), SubscriptionStatus.BILLING_REQUIRED, "250000");

        // when / then
        webTestClient.get().uri(API + "/admin/payments/upcoming").header("Authorization", admin).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.entries.length()").isEqualTo(3)
                .jsonPath("$.entries[0].merchant").isEqualTo("OpenAI")
                .jsonPath("$.entries[0].bucket").isEqualTo("OVERDUE")
                .jsonPath("$.entries[1].merchant").isEqualTo("Netflix")
                .jsonPath("$.entries[2].merchant").isEqualTo("Notion")
                .jsonPath("$.totalUzs").isEqualTo(185000.0)
                .jsonPath("$.overdueTotalUzs").isEqualTo(250000.0);
    }

    private WebTestClient.BodyContentSpec due(LocalDate from, LocalDate to, String filters) {
        return webTestClient.get().uri(API + "/admin/payments/due?from=" + from + "&to=" + to + filters)
                .header("Authorization", admin)
                .exchange()
                .expectStatus().isOk()
                .expectBody();
    }

    private SubscriptionResponse trackDue(String name, LocalDate dueDate, SubscriptionStatus status, String price) {
        SubscriptionResponse created = createSubscription(customer, new SubscriptionRequest(name, new BigDecimal(price),
                CurrencyCode.UZS, BillingFrequency.MONTHLY, status, SubscriptionCategory.OTHER, dueDate.minusMonths(1)));
        jdbc.update("update subscriptions set next_payment_date = ? where id = ?", Date.valueOf(dueDate), created.id());
        return created;
    }
}
