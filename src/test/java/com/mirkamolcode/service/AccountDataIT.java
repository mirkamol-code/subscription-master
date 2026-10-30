package com.mirkamolcode.service;

import com.mirkamolcode.AbstractTestConfig;
import com.mirkamolcode.MutableClock;
import com.mirkamolcode.detection.EmailFixtures;
import com.mirkamolcode.dto.AuthDtos.RefreshRequest;
import com.mirkamolcode.dto.AuthDtos.TokenResponse;
import com.mirkamolcode.entity.ExchangeRate;
import com.mirkamolcode.entity.GmailConnection;
import com.mirkamolcode.entity.Subscription;
import com.mirkamolcode.entity.User;
import com.mirkamolcode.integration.gmail.GmailAttachment;
import com.mirkamolcode.integration.gmail.GmailClient;
import com.mirkamolcode.integration.gmail.GmailMessage;
import com.mirkamolcode.integration.google.GoogleOAuthClient;
import com.mirkamolcode.model.BillingFrequency;
import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.model.SubscriptionCategory;
import com.mirkamolcode.model.SubscriptionStatus;
import com.mirkamolcode.security.TokenCipher;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class AccountDataIT extends AbstractTestConfig {
    private static final FakeGmail GMAIL = new FakeGmail();
    private static final FakeGoogleOAuth GOOGLE = new FakeGoogleOAuth();
    private static final MutableClock CLOCK = new MutableClock(Instant.parse("2026-11-10T04:30:00Z"));
    private static final List<String> USER_TABLES = List.of("gmail_connections", "login_codes", "refresh_tokens", "billing_emails",
            "subscriptions", "notifications", "notification_deliveries", "push_subscriptions", "failed_gmail_messages",
            "merchant_tombstones");
    private static final List<String> SIGN_IN_TABLES = List.of("login_codes", "refresh_tokens");

    @Autowired
    private GmailSyncService syncService;
    @Autowired
    private PaymentReminderJob reminderJob;
    @Autowired
    private BillingEmailRecorder recorder;
    @Autowired
    private TokenCipher tokenCipher;
    @Autowired
    private JdbcTemplate jdbc;

    private TokenResponse aliceTokens;
    private Long aliceId;
    private Long bobId;

    @TestConfiguration
    static class Fakes {
        @Bean
        @Primary
        GmailClient fakeGmailClient() {
            return GMAIL;
        }

        @Bean
        @Primary
        GoogleOAuthClient fakeGoogleOAuthClient() {
            return GOOGLE;
        }

        @Bean
        @Primary
        Clock mutableClock() {
            return CLOCK;
        }
    }

    @BeforeEach
    void setUp() {
        GMAIL.reset();
        GOOGLE.reset();
        CLOCK.setInstant(Instant.parse("2026-11-10T04:30:00Z"));
        exchangeRateRepository.save(new ExchangeRate(CurrencyCode.USD, new BigDecimal("12850.00"), LocalDate.now()));
        aliceTokens = signIn("alice@gmail.com");
        aliceId = connectGmail("alice@gmail.com", "1//alice-refresh");
        signIn("bob@gmail.com");
        bobId = connectGmail("bob@gmail.com", "1//bob-refresh");
        fillWithData(aliceId, aliceTokens);
        fillWithData(bobId, signIn("bob@gmail.com"));
        assertThat(countRowsOf(aliceId)).allSatisfy((table, rows) -> assertThat(rows).as("setup fills " + table).isPositive());
        assertThat(countPaymentsOf(aliceId)).isPositive();
        assertThat(countStatusChangesOf(aliceId)).isPositive();
        assertThat(countAttachmentsOf(aliceId)).isPositive();
    }

    @Test
    void disconnectGmail_shouldDeleteEverythingTheAppHoldsForTheUser() {
        // given
        Map<String, Integer> bobBefore = countRowsOf(bobId);
        int bobPayments = countPaymentsOf(bobId);

        // when
        webTestClient.delete().uri(API + "/me/gmail").header("Authorization", bearer(aliceTokens)).exchange()
                .expectStatus().isOk();

        // then
        assertThat(countRowsOf(aliceId)).allSatisfy((table, rows) -> {
            if (SIGN_IN_TABLES.contains(table))
                assertThat(rows).as(table).isPositive();
            else
                assertThat(rows).as(table).isZero();
        });
        assertThat(jdbc.queryForObject("select count(*) from payment_history", Integer.class)).isEqualTo(bobPayments);
        assertThat(jdbc.queryForObject("select count(*) from status_changes", Integer.class)).isEqualTo(countStatusChangesOf(bobId));
        assertThat(jdbc.queryForObject("select count(*) from email_attachments", Integer.class)).isEqualTo(countAttachmentsOf(bobId));
        assertThat(countRowsOf(bobId)).isEqualTo(bobBefore);
    }

    @Test
    void disconnectGmail_shouldKeepTheAccountSignedIn() {
        // when
        webTestClient.delete().uri(API + "/me/gmail").header("Authorization", bearer(aliceTokens)).exchange()
                .expectStatus().isOk();

        // then
        webTestClient.get().uri(API + "/me/gmail").header("Authorization", bearer(aliceTokens)).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("NOT_CONNECTED");
    }

    @Test
    void disconnectGmail_shouldRevokeTheGoogleGrant() {
        // when
        webTestClient.delete().uri(API + "/me/gmail").header("Authorization", bearer(aliceTokens)).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.googleAccessRevoked").isEqualTo(true);

        // then
        assertThat(GOOGLE.revokedTokens()).containsExactly("1//alice-refresh");
    }

    @Test
    void disconnectGmail_shouldStillDeleteData_whenStoredTokenCannotBeDecrypted() {
        // given
        jdbc.update("update gmail_connections set encrypted_refresh_token = 'v1:corrupted' where user_id = ?", aliceId);

        // when
        webTestClient.delete().uri(API + "/me/gmail").header("Authorization", bearer(aliceTokens)).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.googleAccessRevoked").isEqualTo(false);

        // then
        assertThat(countRowsOf(aliceId).get("subscriptions")).isZero();
    }

    @Test
    void record_shouldSkipEmail_whenGmailWasDisconnectedWhileItWasBeingFetched() {
        // given
        GmailMessage fetchedBeforeDisconnect = new GmailMessage("late-receipt", "late-receipt", Instant.parse("2026-11-09T03:00:00Z"),
                "Netflix <info@account.netflix.com>", "Your Netflix membership receipt", null, "Amount charged: $22.99 per month");
        webTestClient.delete().uri(API + "/me/gmail").header("Authorization", bearer(aliceTokens)).exchange()
                .expectStatus().isOk();

        // when
        recorder.record(aliceId, fetchedBeforeDisconnect);

        // then
        assertThat(countRowsOf(aliceId).get("billing_emails")).isZero();
        assertThat(countRowsOf(aliceId).get("subscriptions")).isZero();
    }

    @Test
    void disconnectGmail_shouldStillDeleteData_whenGoogleCannotBeReached() {
        // given
        GOOGLE.failRevocations();

        // when
        webTestClient.delete().uri(API + "/me/gmail").header("Authorization", bearer(aliceTokens)).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.googleAccessRevoked").isEqualTo(false);

        // then
        assertThat(countRowsOf(aliceId).get("subscriptions")).isZero();
        assertThat(countRowsOf(aliceId).get("gmail_connections")).isZero();
    }

    @Test
    void deleteAccount_shouldRemoveTheUserAndEverythingTheyOwn() {
        // given
        Map<String, Integer> bobBefore = countRowsOf(bobId);

        // when
        webTestClient.delete().uri(API + "/me").header("Authorization", bearer(aliceTokens)).exchange()
                .expectStatus().isNoContent();

        // then
        assertThat(GOOGLE.revokedTokens()).containsExactly("1//alice-refresh");
        assertThat(jdbc.queryForObject("select count(*) from users where id = ?", Integer.class, aliceId)).isZero();
        assertThat(countRowsOf(aliceId)).allSatisfy((table, rows) -> assertThat(rows).as(table).isZero());
        assertThat(jdbc.queryForObject("select count(*) from notification_settings where user_id = ?", Integer.class, aliceId)).isZero();
        assertThat(countRowsOf(bobId)).isEqualTo(bobBefore);
    }

    @Test
    void deleteAccount_shouldInvalidateTheRefreshToken() {
        // when
        webTestClient.delete().uri(API + "/me").header("Authorization", bearer(aliceTokens)).exchange()
                .expectStatus().isNoContent();

        // then
        webTestClient.post().uri(API + "/auth/refresh").bodyValue(new RefreshRequest(aliceTokens.refreshToken())).exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void deleteAccount_shouldWork_whenGmailWasNeverConnected() {
        // given
        TokenResponse carol = signIn("carol@example.com");

        // when / then
        webTestClient.delete().uri(API + "/me").header("Authorization", bearer(carol)).exchange()
                .expectStatus().isNoContent();
        assertThat(GOOGLE.revokedTokens()).isEmpty();
    }

    private void fillWithData(Long userId, TokenResponse tokens) {
        GmailMessage invoice = new GmailMessage("adobe-" + userId, "adobe-" + userId, Instant.parse("2026-10-12T14:30:00Z"),
                "Adobe <message@adobe.com>", "Your Adobe invoice is ready", null,
                "Plan: Creative Cloud All Apps (billed monthly)\nTotal US$59.99",
                List.of(new GmailAttachment("att-" + userId, "Invoice.pdf", "application/pdf", 395)));
        GmailMessage failed = new GmailMessage("adobe-failed-" + userId, "adobe-failed-" + userId, Instant.parse("2026-11-09T03:00:00Z"),
                "Adobe <message@adobe.com>", "Your Adobe payment failed", null, "We were unable to process your payment.");
        GmailMessage broken = new GmailMessage("broken-" + userId, "broken-" + userId, Instant.parse("2026-10-20T03:00:00Z"),
                "Netflix <info@account.netflix.com>", "Your Netflix membership receipt", null, "x");
        GMAIL.mailbox(failed, invoice, EmailFixtures.load("shop-newsletter-save-money.json"), broken);
        GMAIL.failingIds.add(broken.id());
        syncService.sync(userId);
        GMAIL.failingIds.clear();
        User user = userRepository.findById(userId).orElseThrow();
        subscriptionRepository.save(new Subscription(user, "Gym", new BigDecimal("9.99"), CurrencyCode.USD,
                BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.HEALTH, LocalDate.of(2026, 10, 12),
                LocalDate.of(2026, 11, 12)));
        Subscription deleted = subscriptionRepository.save(new Subscription(user, "Spotify", new BigDecimal("10.99"), CurrencyCode.USD,
                BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.ENTERTAINMENT, LocalDate.of(2026, 10, 1)));
        deleted.linkMerchant("spotify", "spotify.com");
        subscriptionRepository.save(deleted);
        webTestClient.delete().uri(API + "/subscriptions/" + deleted.getId()).header("Authorization", bearer(tokens)).exchange()
                .expectStatus().isNoContent();
        webTestClient.put().uri(API + "/settings/notifications").header("Authorization", bearer(tokens))
                .bodyValue(Map.of("emailEnabled", true, "inAppEnabled", true, "pushEnabled", true, "daysBefore", 3, "timeZone", "Asia/Tashkent"))
                .exchange().expectStatus().isOk();
        webTestClient.post().uri(API + "/push/subscriptions").header("Authorization", bearer(tokens))
                .bodyValue(Map.of("endpoint", "https://fcm.googleapis.com/fcm/send/" + userId,
                        "keys", Map.of("p256dh", "BNcRdreALRFXTkOOUHK1EtK2wtaz5Ry4YfYCA_0QTpQtUbVlUls0VJXg7A8u-Ts1XbjhazAkj7I99e8QcYP7DkM", "auth", "tBHItJI5svbpez7KI4CCXg")))
                .exchange().expectStatus().isCreated();
        reminderJob.sendDueReminders();
    }

    private Map<String, Integer> countRowsOf(Long userId) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String table : USER_TABLES)
            counts.put(table, jdbc.queryForObject("select count(*) from " + table + " where user_id = ?", Integer.class, userId));
        return counts;
    }

    private int countPaymentsOf(Long userId) {
        return jdbc.queryForObject("select count(*) from payment_history p join subscriptions s on s.id = p.subscription_id where s.user_id = ?",
                Integer.class, userId);
    }

    private int countStatusChangesOf(Long userId) {
        return jdbc.queryForObject("select count(*) from status_changes c join subscriptions s on s.id = c.subscription_id where s.user_id = ?",
                Integer.class, userId);
    }

    private int countAttachmentsOf(Long userId) {
        return jdbc.queryForObject("select count(*) from email_attachments a join billing_emails e on e.id = a.billing_email_id where e.user_id = ?",
                Integer.class, userId);
    }

    private Long connectGmail(String email, String refreshToken) {
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        gmailConnectionRepository.save(new GmailConnection(user, email, tokenCipher.encrypt(refreshToken), "gmail.readonly gmail.send"));
        return user.getId();
    }
}
