package com.mirkamolcode.controller;

import com.mirkamolcode.AbstractTestConfig;
import com.mirkamolcode.dto.request.StatusChangeRequest;
import com.mirkamolcode.dto.request.SubscriptionRequest;
import com.mirkamolcode.dto.response.SubscriptionResponse;
import com.mirkamolcode.entity.LoginEvent;
import com.mirkamolcode.entity.PaymentHistory;
import com.mirkamolcode.entity.Subscription;
import com.mirkamolcode.entity.User;
import com.mirkamolcode.model.BillingFrequency;
import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.model.SubscriptionCategory;
import com.mirkamolcode.model.SubscriptionStatus;
import com.mirkamolcode.repository.LoginEventRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.reactive.server.WebTestClient;

class AdminAnalyticsControllerIT extends AbstractTestConfig {
    @Autowired
    private LoginEventRepository loginEvents;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void merchantAnalytics_shouldBeForbiddenForNormalUser() {
        // given
        String user = bearer(signIn("user@example.com"));

        // when / then
        webTestClient.get().uri(API + "/admin/analytics/merchants").header("Authorization", user).exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void categoryAnalytics_shouldBeForbiddenForNormalUser() {
        // given
        String user = bearer(signIn("user@example.com"));

        // when / then
        webTestClient.get().uri(API + "/admin/analytics/categories").header("Authorization", user).exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void categoryAnalytics_shouldSumTrackedSpendWithinRange() {
        // given
        String admin = bearer(signInAsAdmin("admin@example.com"));
        String user = bearer(signIn("user@example.com"));
        SubscriptionResponse netflix = track(user, "Netflix", SubscriptionCategory.ENTERTAINMENT);
        track(user, "Spotify", SubscriptionCategory.ENTERTAINMENT);
        pay(netflix.id(), LocalDate.now().minusDays(1), "40000");
        pay(netflix.id(), LocalDate.now().minusDays(45), "40000");

        // when / then
        webTestClient.get().uri(API + "/admin/analytics/categories?range=LAST_30_DAYS").header("Authorization", admin).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].activeSubscriptions").isEqualTo(2)
                .jsonPath("$[0].trackedSpendUzs").isEqualTo(40000.0);
        webTestClient.get().uri(API + "/admin/analytics/categories?range=ALL_TIME").header("Authorization", admin).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].trackedSpendUzs").isEqualTo(80000.0);
    }

    @Test
    void merchantAnalytics_shouldReturnEmptyList_whenNothingIsTracked() {
        // given
        String admin = bearer(signInAsAdmin("admin@example.com"));

        // when / then
        merchants(admin, "").jsonPath("$.length()").isEqualTo(0);
    }

    @Test
    void merchantAnalytics_shouldRankMerchantsByActiveSubscribers_mergingSpellings() {
        // given
        String admin = bearer(signInAsAdmin("admin@example.com"));
        String first = bearer(signIn("first@example.com"));
        String second = bearer(signIn("second@example.com"));
        String third = bearer(signIn("third@example.com"));
        track(first, "Netflix", SubscriptionCategory.ENTERTAINMENT);
        track(second, "netflix", SubscriptionCategory.ENTERTAINMENT);
        track(third, "NETFLIX", SubscriptionCategory.ENTERTAINMENT);
        track(first, "Notion", SubscriptionCategory.PRODUCTIVITY);
        track(second, "Notion", SubscriptionCategory.PRODUCTIVITY);
        track(first, "Spotify", SubscriptionCategory.ENTERTAINMENT);

        // when / then
        merchants(admin, "")
                .jsonPath("$.length()").isEqualTo(3)
                .jsonPath("$[0].slug").isEqualTo("netflix")
                .jsonPath("$[0].activeSubscribers").isEqualTo(3)
                .jsonPath("$[0].sharePercent").isEqualTo(50.0)
                .jsonPath("$[1].slug").isEqualTo("notion")
                .jsonPath("$[1].activeSubscribers").isEqualTo(2)
                .jsonPath("$[1].sharePercent").isEqualTo(33.3)
                .jsonPath("$[2].slug").isEqualTo("spotify")
                .jsonPath("$[2].activeSubscribers").isEqualTo(1)
                .jsonPath("$[2].sharePercent").isEqualTo(16.7);
    }

    @Test
    void merchantAnalytics_shouldCountNewSubscriptionsInLast7And30Days() {
        // given
        String admin = bearer(signInAsAdmin("admin@example.com"));
        String user = bearer(signIn("user@example.com"));
        track(user, "Netflix", SubscriptionCategory.ENTERTAINMENT);
        SubscriptionResponse twentyDaysOld = track(user, "Netflix", SubscriptionCategory.ENTERTAINMENT);
        SubscriptionResponse twoMonthsOld = track(user, "Netflix", SubscriptionCategory.ENTERTAINMENT);
        backdateCreation(twentyDaysOld.id(), Duration.ofDays(20));
        backdateCreation(twoMonthsOld.id(), Duration.ofDays(60));

        // when / then
        merchants(admin, "")
                .jsonPath("$[0].newLast7Days").isEqualTo(1)
                .jsonPath("$[0].newLast30Days").isEqualTo(2);
    }

    @Test
    void merchantAnalytics_shouldCountCancellationsWithinRange() {
        // given
        String admin = bearer(signInAsAdmin("admin@example.com"));
        String user = bearer(signIn("user@example.com"));
        SubscriptionResponse cancelled = track(user, "Netflix", SubscriptionCategory.ENTERTAINMENT);
        track(user, "Netflix", SubscriptionCategory.ENTERTAINMENT);
        changeStatus(user, cancelled.id(), SubscriptionStatus.CANCELLED);

        // when / then
        merchants(admin, "")
                .jsonPath("$[0].activeSubscriptions").isEqualTo(1)
                .jsonPath("$[0].cancelledInRange").isEqualTo(1);
        jdbc.update("update status_changes set effective_at = ?", Timestamp.from(Instant.now().minus(Duration.ofDays(10))));
        merchants(admin, "range=LAST_7_DAYS").jsonPath("$[0].cancelledInRange").isEqualTo(0);
        merchants(admin, "range=LAST_30_DAYS").jsonPath("$[0].cancelledInRange").isEqualTo(1);
    }

    @Test
    void merchantAnalytics_shouldIgnoreDeletedAndHiddenSubscriptions() {
        // given
        String admin = bearer(signInAsAdmin("admin@example.com"));
        String user = bearer(signIn("user@example.com"));
        track(user, "Netflix", SubscriptionCategory.ENTERTAINMENT);
        SubscriptionResponse hidden = track(user, "Spotify", SubscriptionCategory.ENTERTAINMENT);
        SubscriptionResponse deleted = track(user, "Notion", SubscriptionCategory.PRODUCTIVITY);
        webTestClient.post().uri(API + "/subscriptions/" + hidden.id() + "/hide").header("Authorization", user).exchange()
                .expectStatus().is2xxSuccessful();
        webTestClient.delete().uri(API + "/subscriptions/" + deleted.id()).header("Authorization", user).exchange()
                .expectStatus().is2xxSuccessful();

        // when / then
        merchants(admin, "")
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].slug").isEqualTo("netflix")
                .jsonPath("$[0].sharePercent").isEqualTo(100.0);
        webTestClient.get().uri(API + "/admin/analytics/categories").header("Authorization", admin).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].category").isEqualTo("ENTERTAINMENT");
    }

    @Test
    void merchantAnalytics_shouldSumTrackedSpendAndAverageItPerPayingUser_withinRange() {
        // given
        String admin = bearer(signInAsAdmin("admin@example.com"));
        String first = bearer(signIn("first@example.com"));
        String second = bearer(signIn("second@example.com"));
        SubscriptionResponse firstNetflix = track(first, "Netflix", SubscriptionCategory.ENTERTAINMENT);
        SubscriptionResponse secondNetflix = track(second, "Netflix", SubscriptionCategory.ENTERTAINMENT);
        LocalDate today = LocalDate.now();
        pay(firstNetflix.id(), today.minusDays(2), "100000");
        pay(secondNetflix.id(), today.minusDays(3), "50000");
        pay(firstNetflix.id(), today.minusDays(60), "100000");

        // when / then
        merchants(admin, "range=LAST_7_DAYS")
                .jsonPath("$[0].trackedSpendUzs").isEqualTo(150000.0);
        merchants(admin, "range=LAST_30_DAYS")
                .jsonPath("$[0].trackedSpendUzs").isEqualTo(150000.0)
                .jsonPath("$[0].averageSpendPerUserUzs").isEqualTo(75000.0);
        merchants(admin, "range=ALL_TIME")
                .jsonPath("$[0].trackedSpendUzs").isEqualTo(250000.0)
                .jsonPath("$[0].averageSpendPerUserUzs").isEqualTo(125000.0);
    }

    @Test
    void merchantAnalytics_shouldReportLoginsPerActiveSubscriber_withinRange() {
        // given
        String admin = bearer(signInAsAdmin("admin@example.com"));
        String keen = bearer(signIn("keen@example.com"));
        String idle = bearer(signIn("idle@example.com"));
        track(keen, "Netflix", SubscriptionCategory.ENTERTAINMENT);
        track(idle, "Netflix", SubscriptionCategory.ENTERTAINMENT);
        recordLogins("keen@example.com", 3, Duration.ofDays(1));
        recordLogins("keen@example.com", 4, Duration.ofDays(40));

        // when / then
        merchants(admin, "range=LAST_7_DAYS").jsonPath("$[0].loginsPerUser").isEqualTo(1.5);
        merchants(admin, "range=ALL_TIME").jsonPath("$[0].loginsPerUser").isEqualTo(3.5);
    }

    @Test
    void merchantAnalytics_shouldFilterByCategory() {
        // given
        String admin = bearer(signInAsAdmin("admin@example.com"));
        String user = bearer(signIn("user@example.com"));
        track(user, "Netflix", SubscriptionCategory.ENTERTAINMENT);
        track(user, "Notion", SubscriptionCategory.PRODUCTIVITY);

        // when / then
        merchants(admin, "category=PRODUCTIVITY")
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].slug").isEqualTo("notion")
                .jsonPath("$[0].sharePercent").isEqualTo(100.0);
    }

    @Test
    void categoryAnalytics_shouldRankCategoriesByActiveSubscribers() {
        // given
        String admin = bearer(signInAsAdmin("admin@example.com"));
        String first = bearer(signIn("first@example.com"));
        String second = bearer(signIn("second@example.com"));
        track(first, "Netflix", SubscriptionCategory.ENTERTAINMENT);
        track(second, "Spotify", SubscriptionCategory.ENTERTAINMENT);
        track(first, "Notion", SubscriptionCategory.PRODUCTIVITY);

        // when / then
        webTestClient.get().uri(API + "/admin/analytics/categories").header("Authorization", admin).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[0].category").isEqualTo("ENTERTAINMENT")
                .jsonPath("$[0].activeSubscribers").isEqualTo(2)
                .jsonPath("$[0].activeSubscriptions").isEqualTo(2)
                .jsonPath("$[0].sharePercent").isEqualTo(66.7)
                .jsonPath("$[1].category").isEqualTo("PRODUCTIVITY")
                .jsonPath("$[1].activeSubscribers").isEqualTo(1);
    }

    private WebTestClient.BodyContentSpec merchants(String authorization, String query) {
        return webTestClient.get().uri(API + "/admin/analytics/merchants?" + query)
                .header("Authorization", authorization)
                .exchange()
                .expectStatus().isOk()
                .expectBody();
    }

    private SubscriptionResponse track(String authorization, String name, SubscriptionCategory category) {
        return createSubscription(authorization, new SubscriptionRequest(name, BigDecimal.TEN, CurrencyCode.UZS,
                BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, category, LocalDate.of(2026, 1, 10)));
    }

    private void changeStatus(String authorization, Long subscriptionId, SubscriptionStatus status) {
        webTestClient.put().uri(API + "/subscriptions/" + subscriptionId + "/status")
                .header("Authorization", authorization)
                .bodyValue(new StatusChangeRequest(status))
                .exchange()
                .expectStatus().isOk();
    }

    private void pay(Long subscriptionId, LocalDate paidOn, String amountUzs) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId).orElseThrow();
        paymentHistoryRepository.save(new PaymentHistory(subscription, paidOn, new BigDecimal(amountUzs), CurrencyCode.UZS, BigDecimal.ONE));
    }

    private void recordLogins(String email, int count, Duration ago) {
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        for (int i = 0; i < count; i++) {
            loginEvents.save(new LoginEvent(user, Instant.now().minus(ago), "1.1.1.1", "ua"));
        }
    }

    private void backdateCreation(Long subscriptionId, Duration age) {
        jdbc.update("update subscriptions set created_at = ? where id = ?", Timestamp.from(Instant.now().minus(age)), subscriptionId);
    }
}
