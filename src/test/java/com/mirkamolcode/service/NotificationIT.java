package com.mirkamolcode.service;

import com.mirkamolcode.AbstractTestConfig;
import com.mirkamolcode.MutableClock;
import com.mirkamolcode.dto.AuthDtos.TokenResponse;
import com.mirkamolcode.entity.ExchangeRate;
import com.mirkamolcode.entity.GmailConnection;
import com.mirkamolcode.entity.Subscription;
import com.mirkamolcode.entity.User;
import com.mirkamolcode.integration.gmail.GmailClient;
import com.mirkamolcode.integration.gmail.GmailMessage;
import com.mirkamolcode.integration.google.GoogleOAuthClient;
import com.mirkamolcode.integration.push.PushResult;
import com.mirkamolcode.integration.push.PushSender;
import com.mirkamolcode.integration.push.PushTarget;
import com.mirkamolcode.model.BillingFrequency;
import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.model.SubscriptionCategory;
import com.mirkamolcode.model.SubscriptionStatus;
import com.mirkamolcode.security.TokenCipher;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationIT extends AbstractTestConfig {
    private static final FakeGmail GMAIL = new FakeGmail();
    private static final FakeGoogleOAuth GOOGLE = new FakeGoogleOAuth();
    private static final FakePushSender PUSH = new FakePushSender();
    private static final MutableClock CLOCK = new MutableClock(Instant.parse("2026-11-10T04:30:00Z"));

    @Autowired
    private PaymentReminderJob reminderJob;
    @Autowired
    private SubscriptionStatusJob statusJob;
    @Autowired
    private GmailSyncService syncService;
    @Autowired
    private TokenCipher tokenCipher;

    private TokenResponse tokens;
    private Long userId;

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
        PushSender fakePushSender() {
            return PUSH;
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
        PUSH.reset();
        CLOCK.setInstant(Instant.parse("2026-11-10T04:30:00Z"));
        exchangeRateRepository.save(new ExchangeRate(CurrencyCode.USD, new BigDecimal("12850.00"), LocalDate.now()));
        tokens = signIn("alex.doe@gmail.com");
        User user = userRepository.findByEmailIgnoreCase("alex.doe@gmail.com").orElseThrow();
        userId = user.getId();
        gmailConnectionRepository.save(new GmailConnection(user, "alex.doe@gmail.com", tokenCipher.encrypt("1//refresh"), "gmail.readonly gmail.send"));
    }

    @Test
    void sendDueReminders_shouldNotifyInAppAndByEmail_whenPaymentIsWithinDaysBefore() {
        // given
        Subscription netflix = subscriptionDueOn("Netflix", LocalDate.of(2026, 11, 13));

        // when
        reminderJob.sendDueReminders();

        // then
        notifications()
                .jsonPath("$.content.length()").isEqualTo(1)
                .jsonPath("$.content[0].type").isEqualTo("PAYMENT_DUE")
                .jsonPath("$.content[0].severity").isEqualTo("INFO")
                .jsonPath("$.content[0].title").isEqualTo("Netflix renews on 13 Nov 2026")
                .jsonPath("$.content[0].subscriptionId").isEqualTo(netflix.getId().intValue())
                .jsonPath("$.content[0].read").isEqualTo(false);
        assertThat(GMAIL.sentRawMessages).singleElement().satisfies(raw -> assertThat(decodeHtml(raw))
                .contains("Netflix").contains("22.99 USD").contains("13 Nov 2026")
                .contains("http://localhost:5173/subscriptions/" + netflix.getId()));
    }

    @Test
    void sendDueReminders_shouldNotifyOnlyOncePerBillingCycle() {
        // given
        subscriptionDueOn("Netflix", LocalDate.of(2026, 11, 13));

        // when
        reminderJob.sendDueReminders();
        CLOCK.setInstant(Instant.parse("2026-11-11T04:30:00Z"));
        reminderJob.sendDueReminders();

        // then
        notifications().jsonPath("$.totalElements").isEqualTo(1);
        assertThat(GMAIL.sentRawMessages).hasSize(1);
    }

    @Test
    void sendDueReminders_shouldNotifyAgain_forTheNextBillingCycle() {
        // given
        Subscription netflix = subscriptionDueOn("Netflix", LocalDate.of(2026, 11, 13));
        reminderJob.sendDueReminders();
        netflix.recordPayment(LocalDate.of(2026, 11, 13), new BigDecimal("22.99"), CurrencyCode.USD,
                BillingFrequency.MONTHLY, null, com.mirkamolcode.detection.Confidence.HIGH);
        subscriptionRepository.save(netflix);
        CLOCK.setInstant(Instant.parse("2026-12-11T04:30:00Z"));

        // when
        reminderJob.sendDueReminders();

        // then
        notifications().jsonPath("$.totalElements").isEqualTo(2)
                .jsonPath("$.content[0].title").isEqualTo("Netflix renews on 13 Dec 2026");
    }

    @Test
    void sendDueReminders_shouldRespectUsersDaysBeforeSetting() {
        // given
        subscriptionDueOn("Netflix", LocalDate.of(2026, 11, 13));
        updateSettings(Map.of("emailEnabled", true, "inAppEnabled", true, "pushEnabled", false, "daysBefore", 1, "timeZone", "Asia/Tashkent"));

        // when
        reminderJob.sendDueReminders();

        // then
        notifications().jsonPath("$.totalElements").isEqualTo(0);

        // when
        CLOCK.setInstant(Instant.parse("2026-11-12T04:30:00Z"));
        reminderJob.sendDueReminders();

        // then
        notifications().jsonPath("$.totalElements").isEqualTo(1);
    }

    @Test
    void sendDueReminders_shouldWaitUntilNineInTheMorningOfUsersTimeZone() {
        // given
        subscriptionDueOn("Netflix", LocalDate.of(2026, 11, 13));
        CLOCK.setInstant(Instant.parse("2026-11-10T03:59:00Z"));

        // when
        reminderJob.sendDueReminders();

        // then
        notifications().jsonPath("$.totalElements").isEqualTo(0);

        // when
        CLOCK.setInstant(Instant.parse("2026-11-10T04:01:00Z"));
        reminderJob.sendDueReminders();

        // then
        notifications().jsonPath("$.totalElements").isEqualTo(1);
    }

    @Test
    void sendDueReminders_shouldSkipDisabledChannels() {
        // given
        subscriptionDueOn("Netflix", LocalDate.of(2026, 11, 13));
        updateSettings(Map.of("emailEnabled", false, "inAppEnabled", true, "pushEnabled", false, "daysBefore", 3, "timeZone", "Asia/Tashkent"));

        // when
        reminderJob.sendDueReminders();

        // then
        notifications().jsonPath("$.totalElements").isEqualTo(1);
        assertThat(GMAIL.sentRawMessages).isEmpty();
    }

    @Test
    void sendDueReminders_shouldSkipHiddenEndingAndInactiveSubscriptions() {
        // given
        Subscription hidden = subscriptionDueOn("Hidden", LocalDate.of(2026, 11, 13));
        hidden.hide();
        Subscription ending = subscriptionDueOn("Ending", LocalDate.of(2026, 11, 13));
        ending.applyStatus(SubscriptionStatus.ACTIVE, LocalDate.of(2026, 11, 20), Instant.parse("2026-11-01T00:00:00Z"));
        Subscription cancelled = subscriptionDueOn("Cancelled", LocalDate.of(2026, 11, 13));
        cancelled.applyStatus(SubscriptionStatus.CANCELLED, null, Instant.parse("2026-11-01T00:00:00Z"));
        subscriptionRepository.saveAll(List.of(hidden, ending, cancelled));

        // when
        reminderJob.sendDueReminders();

        // then
        notifications().jsonPath("$.totalElements").isEqualTo(0);
        assertThat(GMAIL.sentRawMessages).isEmpty();
    }

    @Test
    void sendDueReminders_shouldSendWebPush_whenEnabledAndBrowserIsSubscribed() {
        // given
        subscriptionDueOn("Netflix", LocalDate.of(2026, 11, 13));
        enablePushOnly();
        subscribePush("https://fcm.googleapis.com/fcm/send/abc");

        // when
        reminderJob.sendDueReminders();

        // then
        assertThat(PUSH.deliveries).singleElement().satisfies(delivery -> {
            assertThat(delivery.endpoint()).isEqualTo("https://fcm.googleapis.com/fcm/send/abc");
            assertThat(delivery.payload()).contains("\"title\":\"Netflix renews on 13 Nov 2026\"");
        });
    }

    @Test
    void sendDueReminders_shouldStopPushingToBrowser_afterPushServiceSaysItExpired() {
        // given
        subscriptionDueOn("Netflix", LocalDate.of(2026, 11, 13));
        enablePushOnly();
        subscribePush("https://fcm.googleapis.com/fcm/send/expired");
        PUSH.expiredEndpoints.add("https://fcm.googleapis.com/fcm/send/expired");
        reminderJob.sendDueReminders();
        subscriptionDueOn("Spotify", LocalDate.of(2026, 11, 12));

        // when
        reminderJob.sendDueReminders();

        // then
        assertThat(PUSH.attemptedEndpoints).containsExactly("https://fcm.googleapis.com/fcm/send/expired");
    }

    @Test
    void sendDueReminders_shouldStillNotifyInApp_andKeepGmailConnected_whenSendScopeIsMissing() {
        // given
        subscriptionDueOn("Netflix", LocalDate.of(2026, 11, 13));
        GMAIL.rejectSending = true;

        // when
        reminderJob.sendDueReminders();

        // then
        notifications().jsonPath("$.totalElements").isEqualTo(1);
        gmailStatus().jsonPath("$.status").isEqualTo("CONNECTED");
    }

    @Test
    void sendDueReminders_shouldMarkGmailDisconnected_whenTokenIsRevokedWhileSending() {
        // given
        subscriptionDueOn("Netflix", LocalDate.of(2026, 11, 13));
        GMAIL.revokeOnSend = true;
        GOOGLE.revokeAfterRefreshes(1);

        // when
        reminderJob.sendDueReminders();

        // then
        notifications().jsonPath("$.totalElements").isEqualTo(1);
        gmailStatus().jsonPath("$.status").isEqualTo("DISCONNECTED");
    }

    @Test
    void onStatusChanged_shouldNotifyUrgently_whenRecentPaymentFailureArrives() {
        // given
        GMAIL.mailbox(message("openai-failed", "2026-11-09T03:00:00Z", "OpenAI <noreply@tm.openai.com>",
                        "Your ChatGPT Plus payment failed", "We were unable to process your payment of $20.00."),
                openAiReceipt("openai-oct", "2026-10-05T03:00:00Z"));

        // when
        syncService.sync(userId);

        // then
        notifications()
                .jsonPath("$.content[0].type").isEqualTo("BILLING_REQUIRED")
                .jsonPath("$.content[0].severity").isEqualTo("URGENT")
                .jsonPath("$.content[0].title").isEqualTo("Payment problem with ChatGPT");
        assertThat(GMAIL.sentRawMessages).singleElement().satisfies(raw -> assertThat(decodeHtml(raw)).contains("ChatGPT"));
    }

    @Test
    void onStatusChanged_shouldNotNotify_whenSubscriptionIsHidden() {
        // given
        GMAIL.mailbox(openAiReceipt("openai-oct", "2026-10-05T03:00:00Z"));
        syncService.sync(userId);
        webTestClient.post().uri(API + "/subscriptions/" + openAiSubscriptionId() + "/hide").header("Authorization", bearer(tokens)).exchange()
                .expectStatus().isOk();
        GMAIL.historyAdds("openai-failed");
        GMAIL.deliver(message("openai-failed", "2026-11-09T03:00:00Z", "OpenAI <noreply@tm.openai.com>",
                "Your ChatGPT Plus payment failed", "We were unable to process your payment of $20.00."));

        // when
        syncService.sync(userId);

        // then
        notifications().jsonPath("$.totalElements").isEqualTo(0);
        assertThat(GMAIL.sentRawMessages).isEmpty();
    }

    @Test
    void onStatusChanged_shouldNotNotify_whenEmailIsOlderThanThreeDays() {
        // given
        GMAIL.mailbox(message("openai-failed-old", "2026-06-09T03:00:00Z", "OpenAI <noreply@tm.openai.com>",
                        "Your ChatGPT Plus payment failed", "We were unable to process your payment of $20.00."),
                openAiReceipt("openai-may", "2026-05-05T03:00:00Z"));

        // when
        syncService.sync(userId);

        // then
        notifications().jsonPath("$.totalElements").isEqualTo(0);
        assertThat(GMAIL.sentRawMessages).isEmpty();
    }

    @Test
    void onStatusChanged_shouldAlertUrgently_whenSubscriptionJustBecameOverdue() {
        // given
        subscriptionDueOn("Gym", LocalDate.of(2026, 11, 3));

        // when
        statusJob.applyDailyRules();

        // then
        notifications()
                .jsonPath("$.content[0].type").isEqualTo("BILLING_REQUIRED")
                .jsonPath("$.content[0].body").value(body -> assertThat((String) body).startsWith("We expected a payment for Gym on 3 Nov 2026"));
    }

    @Test
    void onStatusChanged_shouldNotAlert_whenLongLapsedSubscriptionIsMarkedOverdue() {
        // given
        subscriptionDueOn("Old gym", LocalDate.of(2025, 11, 1));

        // when
        statusJob.applyDailyRules();

        // then
        notifications().jsonPath("$.totalElements").isEqualTo(0);
        assertThat(GMAIL.sentRawMessages).isEmpty();
    }

    @Test
    void onStatusChanged_shouldNotifyReactivation_whenNewerReceiptBeatsManualCancel() {
        // given
        GMAIL.mailbox(openAiReceipt("openai-oct", "2026-10-05T03:00:00Z"));
        syncService.sync(userId);
        webTestClient.put().uri(API + "/subscriptions/" + openAiSubscriptionId() + "/status").header("Authorization", bearer(tokens))
                .bodyValue(Map.of("status", "CANCELLED")).exchange().expectStatus().isOk();
        GMAIL.historyAdds("openai-nov");
        GMAIL.deliver(openAiReceipt("openai-nov", "2026-11-10T05:00:00Z"));

        // when
        syncService.sync(userId);

        // then
        notifications()
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.content[0].type").isEqualTo("REACTIVATED")
                .jsonPath("$.content[0].title").isEqualTo("ChatGPT is active again");
    }

    @Test
    void onRetracked_shouldTellUser_whenDeletedSubscriptionChargesAgain() {
        // given
        GMAIL.mailbox(openAiReceipt("openai-oct", "2026-10-05T03:00:00Z"));
        syncService.sync(userId);
        webTestClient.delete().uri(API + "/subscriptions/" + openAiSubscriptionId()).header("Authorization", bearer(tokens)).exchange()
                .expectStatus().isNoContent();
        CLOCK.setInstant(Instant.parse("2026-11-12T04:30:00Z"));
        GMAIL.historyAdds("openai-again");
        GMAIL.deliver(openAiReceipt("openai-again", "2026-11-12T03:00:00Z"));

        // when
        syncService.sync(userId);

        // then
        notifications()
                .jsonPath("$.content[0].type").isEqualTo("RETRACKED")
                .jsonPath("$.content[0].body").isEqualTo("ChatGPT charged you again on 12 Nov 2026, so we're tracking it again.");
    }

    @Test
    void markRead_shouldLowerUnreadCount() {
        // given
        twoRemindersSent();

        // when
        webTestClient.post().uri(API + "/notifications/" + firstNotificationId() + "/read").header("Authorization", bearer(tokens)).exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.read").isEqualTo(true);

        // then
        unreadCount(1);
    }

    @Test
    void markAllRead_shouldClearUnreadCount() {
        // given
        twoRemindersSent();

        // when
        webTestClient.post().uri(API + "/notifications/read-all").header("Authorization", bearer(tokens)).exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.updated").isEqualTo(2);

        // then
        unreadCount(0);
    }

    @Test
    void markRead_shouldReturnNotFound_forAnotherUsersNotification() {
        // given
        twoRemindersSent();

        // when / then
        webTestClient.post().uri(API + "/notifications/" + firstNotificationId() + "/read").header("Authorization", bearer(signIn("intruder@example.com")))
                .exchange().expectStatus().isNotFound();
        unreadCount(2);
    }

    @Test
    void get_shouldReturnDefaultNotificationSettings() {
        // when / then
        webTestClient.get().uri(API + "/settings/notifications").header("Authorization", bearer(tokens)).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.emailEnabled").isEqualTo(true)
                .jsonPath("$.inAppEnabled").isEqualTo(true)
                .jsonPath("$.pushEnabled").isEqualTo(false)
                .jsonPath("$.daysBefore").isEqualTo(3)
                .jsonPath("$.timeZone").isEqualTo("Asia/Tashkent");
    }

    @Test
    void update_shouldRejectDaysBeforeOutsideRange() {
        // when / then
        webTestClient.put().uri(API + "/settings/notifications").header("Authorization", bearer(tokens))
                .bodyValue(settings(true, true, false, 0, "Asia/Tashkent"))
                .exchange().expectStatus().isBadRequest();
    }

    @Test
    void update_shouldRejectUnknownTimeZone() {
        // when / then
        webTestClient.put().uri(API + "/settings/notifications").header("Authorization", bearer(tokens))
                .bodyValue(settings(true, true, false, 3, "Mars/Olympus"))
                .exchange().expectStatus().isBadRequest();
    }

    @Test
    void publicKey_shouldReturnVapidPublicKey() {
        // when / then
        webTestClient.get().uri(API + "/push/vapid-public-key").header("Authorization", bearer(tokens)).exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.publicKey").isEqualTo("test-vapid-public-key");
    }

    @Test
    void subscribe_shouldRejectNonHttpsEndpoint() {
        // when / then
        webTestClient.post().uri(API + "/push/subscriptions").header("Authorization", bearer(tokens))
                .bodyValue(Map.of("endpoint", "http://insecure.example/push", "keys", Map.of("p256dh", "p", "auth", "a")))
                .exchange().expectStatus().isBadRequest();
    }

    @Test
    void unsubscribe_shouldStopPushDeliveriesToThatBrowser() {
        // given
        subscriptionDueOn("Netflix", LocalDate.of(2026, 11, 13));
        enablePushOnly();
        subscribePush("https://fcm.googleapis.com/fcm/send/abc");

        // when
        webTestClient.method(HttpMethod.DELETE).uri(API + "/push/subscriptions").header("Authorization", bearer(tokens))
                .bodyValue(Map.of("endpoint", "https://fcm.googleapis.com/fcm/send/abc"))
                .exchange().expectStatus().isNoContent();
        reminderJob.sendDueReminders();

        // then
        assertThat(PUSH.attemptedEndpoints).isEmpty();
    }

    private void twoRemindersSent() {
        subscriptionDueOn("Netflix", LocalDate.of(2026, 11, 13));
        subscriptionDueOn("Spotify", LocalDate.of(2026, 11, 12));
        reminderJob.sendDueReminders();
    }

    private int firstNotificationId() {
        AtomicInteger id = new AtomicInteger();
        notifications().jsonPath("$.content[0].id").value(value -> id.set((Integer) value));
        return id.get();
    }

    private Long openAiSubscriptionId() {
        return subscriptionRepository.findByUserIdAndMerchantKeyAndIsDeletedFalse(userId, "openai").orElseThrow().getId();
    }

    private void enablePushOnly() {
        updateSettings(settings(false, false, true, 3, "Asia/Tashkent"));
    }

    private static Map<String, Object> settings(boolean email, boolean inApp, boolean push, int daysBefore, String timeZone) {
        return Map.of("emailEnabled", email, "inAppEnabled", inApp, "pushEnabled", push, "daysBefore", daysBefore, "timeZone", timeZone);
    }

    private org.springframework.test.web.reactive.server.WebTestClient.BodyContentSpec gmailStatus() {
        return webTestClient.get().uri(API + "/me/gmail").header("Authorization", bearer(tokens)).exchange()
                .expectStatus().isOk().expectBody();
    }

    private org.springframework.test.web.reactive.server.WebTestClient.BodyContentSpec notifications() {
        return webTestClient.get().uri(API + "/notifications").header("Authorization", bearer(tokens)).exchange()
                .expectStatus().isOk().expectBody();
    }

    private void unreadCount(int expected) {
        webTestClient.get().uri(API + "/notifications/unread-count").header("Authorization", bearer(tokens)).exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.count").isEqualTo(expected);
    }

    private void updateSettings(Map<String, Object> settings) {
        webTestClient.put().uri(API + "/settings/notifications").header("Authorization", bearer(tokens)).bodyValue(settings)
                .exchange().expectStatus().isOk();
    }

    private void subscribePush(String endpoint) {
        webTestClient.post().uri(API + "/push/subscriptions").header("Authorization", bearer(tokens))
                .bodyValue(Map.of("endpoint", endpoint, "keys", Map.of("p256dh", "BNcRdreALRFXTkOOUHK1EtK2wtaz5Ry4YfYCA_0QTpQtUbVlUls0VJXg7A8u-Ts1XbjhazAkj7I99e8QcYP7DkM", "auth", "tBHItJI5svbpez7KI4CCXg")))
                .exchange().expectStatus().isCreated();
    }

    private Subscription subscriptionDueOn(String name, LocalDate nextPaymentDate) {
        User owner = userRepository.findById(userId).orElseThrow();
        return subscriptionRepository.save(new Subscription(owner, name, new BigDecimal("22.99"), CurrencyCode.USD,
                BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.ENTERTAINMENT,
                nextPaymentDate.minusMonths(1), nextPaymentDate));
    }

    private static String decodeHtml(String raw) {
        String mime = new String(Base64.getUrlDecoder().decode(raw), StandardCharsets.UTF_8);
        String body = mime.substring(mime.indexOf("\r\n\r\n") + 4).replace("\r\n", "");
        return new String(Base64.getDecoder().decode(body), StandardCharsets.UTF_8);
    }

    private static GmailMessage openAiReceipt(String id, String receivedAt) {
        return message(id, receivedAt, "OpenAI <noreply@tm.openai.com>", "Your ChatGPT Plus receipt",
                "Thanks for your payment. Amount paid: $20.00 per month for ChatGPT Plus.");
    }

    private static GmailMessage message(String id, String receivedAt, String from, String subject, String text) {
        return new GmailMessage(id, id, Instant.parse(receivedAt), from, subject, null, text);
    }

    static final class FakePushSender implements PushSender {
        final List<PushTarget> deliveries = new CopyOnWriteArrayList<>();
        final List<String> attemptedEndpoints = new CopyOnWriteArrayList<>();
        final List<String> expiredEndpoints = new CopyOnWriteArrayList<>();

        void reset() {
            deliveries.clear();
            attemptedEndpoints.clear();
            expiredEndpoints.clear();
        }

        @Override
        public String publicKey() {
            return "test-vapid-public-key";
        }

        @Override
        public PushResult send(PushTarget target) {
            attemptedEndpoints.add(target.endpoint());
            if (expiredEndpoints.contains(target.endpoint()))
                return PushResult.EXPIRED;
            deliveries.add(target);
            return PushResult.DELIVERED;
        }
    }
}
