package com.mirkamolcode.service;

import com.mirkamolcode.AbstractTestConfig;
import com.mirkamolcode.MutableClock;
import com.mirkamolcode.dto.request.SubscriptionRequest;
import com.mirkamolcode.dto.response.SubscriptionResponse;
import com.mirkamolcode.model.BillingFrequency;
import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.model.SubscriptionCategory;
import com.mirkamolcode.model.SubscriptionStatus;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.reactive.server.WebTestClient;

class AdminPaymentDigestIT extends AbstractTestConfig {
    private static final Instant TEN_AM_IN_TASHKENT = Instant.parse("2026-11-10T05:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private static final MutableClock CLOCK = new MutableClock(TEN_AM_IN_TASHKENT);

    @Autowired
    private AdminPaymentDigestJob digestJob;
    @Autowired
    private JdbcTemplate jdbc;

    private String admin;
    private String customer;

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock mutableClock() {
            return CLOCK;
        }
    }

    @BeforeEach
    void setUp() {
        CLOCK.setInstant(TEN_AM_IN_TASHKENT);
        jdbc.update("delete from notification_deliveries");
        jdbc.update("delete from notifications");
        admin = bearer(signInAsAdmin("admin@example.com"));
        customer = bearer(signIn("customer@example.com"));
    }

    @Test
    void sendDigests_shouldTellAdminsAboutPaymentsDueInNext3Days() {
        // given
        trackDue("Netflix", TODAY.plusDays(1), SubscriptionStatus.ACTIVE, "65000");
        trackDue("Spotify", TODAY.plusDays(3), SubscriptionStatus.ACTIVE, "35000");
        trackDue("Notion", TODAY.plusDays(5), SubscriptionStatus.ACTIVE, "120000");

        // when
        digestJob.sendDigests();

        // then
        adminNotifications()
                .jsonPath("$.content.length()").isEqualTo(1)
                .jsonPath("$.content[0].type").isEqualTo("ADMIN_PAYMENT_DIGEST")
                .jsonPath("$.content[0].title").isEqualTo("2 payments due in the next 3 days")
                .jsonPath("$.content[0].body").isEqualTo("100,000 UZS expected in the next 3 days · 0 overdue")
                .jsonPath("$.content[0].link").isEqualTo("/admin/payments");
        customerNotifications().jsonPath("$.content.length()").isEqualTo(0);
    }

    @Test
    void sendDigests_shouldSendOnlyOncePerDay() {
        // given
        trackDue("Netflix", TODAY.plusDays(1), SubscriptionStatus.ACTIVE, "65000");

        // when
        digestJob.sendDigests();
        CLOCK.setInstant(TEN_AM_IN_TASHKENT.plusSeconds(3600));
        digestJob.sendDigests();

        // then
        adminNotifications().jsonPath("$.totalElements").isEqualTo(1);
    }

    @Test
    void sendDigests_shouldWaitForTheReminderHourInTheAdminsTimeZone() {
        // given
        trackDue("Netflix", TODAY.plusDays(1), SubscriptionStatus.ACTIVE, "65000");
        CLOCK.setInstant(Instant.parse("2026-11-10T02:00:00Z"));

        // when
        digestJob.sendDigests();

        // then
        adminNotifications().jsonPath("$.totalElements").isEqualTo(0);
    }

    @Test
    void sendDigests_shouldStayQuiet_whenNothingIsDueOrOverdue() {
        // given
        trackDue("Notion", TODAY.plusDays(10), SubscriptionStatus.ACTIVE, "120000");

        // when
        digestJob.sendDigests();

        // then
        adminNotifications().jsonPath("$.totalElements").isEqualTo(0);
    }

    @Test
    void sendDigests_shouldMarkUrgent_whenPaymentsAreOverdue() {
        // given
        trackDue("OpenAI", TODAY.minusDays(2), SubscriptionStatus.BILLING_REQUIRED, "250000");

        // when
        digestJob.sendDigests();

        // then
        adminNotifications()
                .jsonPath("$.content[0].severity").isEqualTo("URGENT")
                .jsonPath("$.content[0].title").isEqualTo("1 payment overdue")
                .jsonPath("$.content[0].body").isEqualTo("0 UZS expected in the next 3 days · 1 overdue (250,000 UZS)");
    }

    private WebTestClient.BodyContentSpec adminNotifications() {
        return notificationsOf(admin);
    }

    private WebTestClient.BodyContentSpec customerNotifications() {
        return notificationsOf(customer);
    }

    private WebTestClient.BodyContentSpec notificationsOf(String authorization) {
        return webTestClient.get().uri(API + "/notifications").header("Authorization", authorization).exchange()
                .expectStatus().isOk()
                .expectBody();
    }

    private void trackDue(String name, LocalDate dueDate, SubscriptionStatus status, String price) {
        SubscriptionResponse created = createSubscription(customer, new SubscriptionRequest(name, new BigDecimal(price),
                CurrencyCode.UZS, BillingFrequency.MONTHLY, status, SubscriptionCategory.OTHER, dueDate.minusMonths(1)));
        jdbc.update("update subscriptions set next_payment_date = ? where id = ?", Date.valueOf(dueDate), created.id());
    }
}
