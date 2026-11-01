package com.mirkamolcode.controller;

import com.mirkamolcode.AbstractTestConfig;
import com.mirkamolcode.dto.request.SubscriptionRequest;
import com.mirkamolcode.entity.LoginEvent;
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
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class AdminUserControllerIT extends AbstractTestConfig {
    private static final ZoneId ADMIN_ZONE = ZoneId.of(User.DEFAULT_TIME_ZONE);

    @Autowired
    private LoginEventRepository loginEvents;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void adminEndpoints_shouldRequireAuthentication() {
        // when / then
        webTestClient.get().uri(API + "/admin/users").exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void adminEndpoints_shouldBeForbiddenForNormalUser() {
        // given
        String user = bearer(signIn("user@example.com"));

        // when / then
        webTestClient.get().uri(API + "/admin/users").header("Authorization", user).exchange()
                .expectStatus().isForbidden();
        webTestClient.get().uri(API + "/admin/overview").header("Authorization", user).exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void adminEndpoints_shouldBeForbidden_onceAdminIsDemoted_evenWithUnexpiredToken() {
        // given
        String first = bearer(signInAsAdmin("first-admin@example.com"));
        String second = bearer(signInAsAdmin("second-admin@example.com"));
        Long secondId = idOf("second-admin@example.com");

        // when
        changeRole(first, secondId, "USER").expectStatus().isOk();

        // then
        webTestClient.get().uri(API + "/admin/users").header("Authorization", second).exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void changeRole_shouldPromoteUserToAdmin_effectiveWithTheirCurrentToken() {
        // given
        String admin = bearer(signInAsAdmin("boss@example.com"));
        String worker = bearer(signIn("worker@example.com"));
        Long workerId = idOf("worker@example.com");

        // when
        changeRole(admin, workerId, "ADMIN")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.role").isEqualTo("ADMIN");

        // then
        webTestClient.get().uri(API + "/admin/users").header("Authorization", worker).exchange()
                .expectStatus().isOk();
    }

    @Test
    void changeRole_shouldRefuseToDemoteLastAdmin() {
        // given
        String admin = bearer(signInAsAdmin("only-admin@example.com"));
        Long adminId = idOf("only-admin@example.com");

        // when / then
        changeRole(admin, adminId, "USER")
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("LAST_ADMIN");
        webTestClient.get().uri(API + "/admin/users").header("Authorization", admin).exchange()
                .expectStatus().isOk();
    }

    @Test
    void changeRole_shouldAllowAdminToDemoteThemself_whenAnotherAdminRemains() {
        // given
        String admin = bearer(signInAsAdmin("leaving-admin@example.com"));
        signInAsAdmin("staying-admin@example.com");
        Long adminId = idOf("leaving-admin@example.com");

        // when / then
        changeRole(admin, adminId, "USER")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.role").isEqualTo("USER");
    }

    @Test
    void changeRole_shouldReturnNotFound_forUnknownUser() {
        // given
        String admin = bearer(signInAsAdmin("admin@example.com"));

        // when / then
        changeRole(admin, 999_999L, "ADMIN").expectStatus().isNotFound();
    }

    @Test
    void listUsers_shouldSearchByNameOrEmail_caseInsensitively() {
        // given
        String admin = bearer(signInAsAdmin("admin@example.com"));
        signIn("alice@example.com");
        signIn("bob@example.com");
        jdbc.update("update users set display_name = 'Bobby Tables' where email = 'bob@example.com'");

        // when / then
        listUsers(admin, "search=ALICE")
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.content[0].email").isEqualTo("alice@example.com");
        listUsers(admin, "search=tables")
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.content[0].displayName").isEqualTo("Bobby Tables");
    }

    @Test
    void listUsers_shouldFilterByJoinedWithinDays() {
        // given
        String admin = bearer(signInAsAdmin("admin@example.com"));
        signIn("new@example.com");
        signIn("old@example.com");
        backdateSignup("old@example.com", Duration.ofDays(40));
        backdateSignup("admin@example.com", Duration.ofDays(40));

        // when / then
        listUsers(admin, "joinedWithinDays=30")
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.content[0].email").isEqualTo("new@example.com");
    }

    @Test
    void listUsers_shouldFilterByLoggedInWithinDays() {
        // given
        String admin = bearer(signInAsAdmin("admin@example.com"));
        signIn("active@example.com");
        signIn("dormant@example.com");
        setLastLogin("active@example.com", Instant.now().minus(Duration.ofHours(2)));
        setLastLogin("dormant@example.com", Instant.now().minus(Duration.ofDays(20)));

        // when / then
        listUsers(admin, "loggedInWithinDays=7")
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.content[0].email").isEqualTo("active@example.com");
    }

    @Test
    void listUsers_shouldFilterByStatusAndMerchant_andSummariseSubscriptions() {
        // given
        String admin = bearer(signInAsAdmin("admin@example.com"));
        String netflixFan = bearer(signIn("netflix-fan@example.com"));
        String notionFan = bearer(signIn("notion-fan@example.com"));
        createSubscription(netflixFan, request("Netflix", SubscriptionStatus.ACTIVE, LocalDate.of(2026, 1, 10)));
        createSubscription(netflixFan, request("Spotify", SubscriptionStatus.BILLING_REQUIRED, LocalDate.of(2026, 1, 10)));
        createSubscription(notionFan, request("Notion", SubscriptionStatus.ACTIVE, LocalDate.of(2026, 1, 10)));

        // when / then
        listUsers(admin, "merchant=netflix")
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.content[0].email").isEqualTo("netflix-fan@example.com")
                .jsonPath("$.content[0].activeSubscriptions").isEqualTo(1)
                .jsonPath("$.content[0].status").isEqualTo("BILLING_REQUIRED")
                .jsonPath("$.content[0].merchantNames[0]").isEqualTo("Netflix")
                .jsonPath("$.content[0].merchantNames[1]").isEqualTo("Spotify");
        listUsers(admin, "status=BILLING_REQUIRED")
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.content[0].email").isEqualTo("netflix-fan@example.com");
    }

    @Test
    void listUsers_shouldNameEachMerchantOnce_whenSpellingsDiffer() {
        // given
        String admin = bearer(signInAsAdmin("admin@example.com"));
        String customer = bearer(signIn("doubled@example.com"));
        createSubscription(customer, request("Netflix", SubscriptionStatus.ACTIVE, LocalDate.of(2026, 1, 10)));
        createSubscription(customer, request("Netflix", SubscriptionStatus.ACTIVE, LocalDate.of(2026, 2, 10)));
        createSubscription(customer, request("netflix", SubscriptionStatus.ACTIVE, LocalDate.of(2026, 3, 10)));

        // when / then
        listUsers(admin, "search=doubled")
                .jsonPath("$.content[0].activeSubscriptions").isEqualTo(3)
                .jsonPath("$.content[0].merchantNames.length()").isEqualTo(1)
                .jsonPath("$.content[0].merchantNames[0]").isEqualTo("Netflix");
    }

    @Test
    void listUsers_shouldSortAndPage() {
        // given
        String admin = bearer(signInAsAdmin("a-admin@example.com"));
        signIn("b@example.com");
        signIn("c@example.com");

        // when / then
        listUsers(admin, "sort=email,desc&size=2&page=0")
                .jsonPath("$.totalElements").isEqualTo(3)
                .jsonPath("$.totalPages").isEqualTo(2)
                .jsonPath("$.content.length()").isEqualTo(2)
                .jsonPath("$.content[0].email").isEqualTo("c@example.com")
                .jsonPath("$.content[1].email").isEqualTo("b@example.com");
        listUsers(admin, "sort=email,desc&size=2&page=1")
                .jsonPath("$.content[0].email").isEqualTo("a-admin@example.com")
                .jsonPath("$.content[0].role").isEqualTo("ADMIN");
    }

    @Test
    void listUsers_shouldRejectUnknownSortField() {
        // given
        String admin = bearer(signInAsAdmin("admin@example.com"));

        // when / then
        webTestClient.get().uri(API + "/admin/users?sort=googleSubject").header("Authorization", admin).exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void userDetail_shouldReturnProfileSubscriptionsAndPayments() {
        // given
        String admin = bearer(signInAsAdmin("admin@example.com"));
        String customer = bearer(signIn("customer@example.com"));
        createSubscription(customer, request("Netflix", SubscriptionStatus.ACTIVE, LocalDate.of(2026, 1, 10)));
        Long customerId = idOf("customer@example.com");

        // when / then
        webTestClient.get().uri(API + "/admin/users/" + customerId).header("Authorization", admin).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.email").isEqualTo("customer@example.com")
                .jsonPath("$.role").isEqualTo("USER")
                .jsonPath("$.createdAt").isNotEmpty();
        webTestClient.get().uri(API + "/admin/users/" + customerId + "/subscriptions").header("Authorization", admin).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].name").isEqualTo("Netflix")
                .jsonPath("$[0].merchant").isEqualTo("netflix")
                .jsonPath("$[0].deleted").isEqualTo(false);
        webTestClient.get().uri(API + "/admin/users/" + customerId + "/payments").header("Authorization", admin).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content").isArray();
    }

    @Test
    void overview_shouldCountSignupsSubscribersLoginsAndDuePayments() {
        // given
        String admin = bearer(signInAsAdmin("admin@example.com"));
        String fresh = bearer(signIn("fresh@example.com"));
        signIn("week-old@example.com");
        signIn("month-old@example.com");
        backdateSignup("week-old@example.com", Duration.ofDays(5));
        backdateSignup("month-old@example.com", Duration.ofDays(20));
        backdateSignup("admin@example.com", Duration.ofDays(60));
        LocalDate today = LocalDate.now(ADMIN_ZONE);
        createSubscription(fresh, request("Netflix", SubscriptionStatus.ACTIVE, today.plusDays(3).minusMonths(1)));
        createSubscription(fresh, request("Notion", SubscriptionStatus.ACTIVE, today.plusDays(20).minusMonths(1)));
        createSubscription(fresh, request("Spotify", SubscriptionStatus.CANCELLED, today.plusDays(2).minusMonths(1)));
        User freshUser = userRepository.findByEmailIgnoreCase("fresh@example.com").orElseThrow();
        loginEvents.save(new LoginEvent(freshUser, Instant.now().minus(Duration.ofHours(3)), "1.1.1.1", "ua"));
        loginEvents.save(new LoginEvent(freshUser, Instant.now().minus(Duration.ofHours(30)), "1.1.1.1", "ua"));

        // when / then
        webTestClient.get().uri(API + "/admin/overview").header("Authorization", admin).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.signupsToday").isEqualTo(1)
                .jsonPath("$.signupsLast7Days").isEqualTo(2)
                .jsonPath("$.signupsLast30Days").isEqualTo(3)
                .jsonPath("$.activeSubscribers").isEqualTo(1)
                .jsonPath("$.loginsLast24Hours").isEqualTo(1)
                .jsonPath("$.paymentsDueNext7Days").isEqualTo(1)
                .jsonPath("$.paymentsDueNext7DaysTotalUzs").isEqualTo(10.0);
    }

    @Test
    void merchants_shouldListDistinctMerchantsCaseInsensitively() {
        // given
        String admin = bearer(signInAsAdmin("admin@example.com"));
        String first = bearer(signIn("first@example.com"));
        String second = bearer(signIn("second@example.com"));
        createSubscription(first, request("Netflix", SubscriptionStatus.ACTIVE, LocalDate.of(2026, 1, 10)));
        createSubscription(second, request("netflix", SubscriptionStatus.ACTIVE, LocalDate.of(2026, 1, 10)));
        createSubscription(second, request("YouTube Premium", SubscriptionStatus.ACTIVE, LocalDate.of(2026, 1, 10)));

        // when / then
        webTestClient.get().uri(API + "/admin/merchants").header("Authorization", admin).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[0].slug").isEqualTo("netflix")
                .jsonPath("$[1].slug").isEqualTo("youtube-premium")
                .jsonPath("$[1].name").isEqualTo("YouTube Premium");
    }

    private org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec changeRole(String authorization, Long userId, String role) {
        return webTestClient.put().uri(API + "/admin/users/" + userId + "/role")
                .header("Authorization", authorization)
                .bodyValue(Map.of("role", role))
                .exchange();
    }

    private org.springframework.test.web.reactive.server.WebTestClient.BodyContentSpec listUsers(String authorization, String query) {
        return webTestClient.get().uri(API + "/admin/users?" + query)
                .header("Authorization", authorization)
                .exchange()
                .expectStatus().isOk()
                .expectBody();
    }

    private Long idOf(String email) {
        return userRepository.findByEmailIgnoreCase(email).orElseThrow().getId();
    }

    private void backdateSignup(String email, Duration age) {
        jdbc.update("update users set created_at = ? where email = ?", Timestamp.from(Instant.now().minus(age)), email);
    }

    private void setLastLogin(String email, Instant at) {
        jdbc.update("update users set last_login_at = ? where email = ?", Timestamp.from(at), email);
    }

    private SubscriptionRequest request(String name, SubscriptionStatus status, LocalDate startDate) {
        return new SubscriptionRequest(name, BigDecimal.TEN, CurrencyCode.UZS,
                BillingFrequency.MONTHLY, status, SubscriptionCategory.OTHER, startDate);
    }
}
