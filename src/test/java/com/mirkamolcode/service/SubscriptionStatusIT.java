package com.mirkamolcode.service;

import com.mirkamolcode.AbstractTestConfig;
import com.mirkamolcode.MutableClock;
import com.mirkamolcode.detection.EmailFixtures;
import com.mirkamolcode.dto.AuthDtos.TokenResponse;
import com.mirkamolcode.dto.request.SubscriptionRequest;
import com.mirkamolcode.entity.ExchangeRate;
import com.mirkamolcode.entity.GmailConnection;
import com.mirkamolcode.entity.Subscription;
import com.mirkamolcode.entity.User;
import com.mirkamolcode.event.SubscriptionStatusChangedEvent;
import com.mirkamolcode.integration.gmail.GmailClient;
import com.mirkamolcode.integration.gmail.GmailMessage;
import com.mirkamolcode.integration.google.GoogleOAuthClient;
import com.mirkamolcode.model.BillingFrequency;
import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.model.SubscriptionCategory;
import com.mirkamolcode.model.SubscriptionStatus;
import com.mirkamolcode.repository.StatusChangeRepository;
import com.mirkamolcode.security.TokenCipher;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import static org.assertj.core.api.Assertions.assertThat;

@RecordApplicationEvents
class SubscriptionStatusIT extends AbstractTestConfig {
    private static final FakeGmail GMAIL = new FakeGmail();
    private static final FakeGoogleOAuth GOOGLE = new FakeGoogleOAuth();
    private static final MutableClock CLOCK = new MutableClock(Instant.parse("2026-11-10T09:00:00Z"));

    private static final GmailMessage SPOTIFY_CANCELLED = EmailFixtures.load("spotify-cancellation.json");
    private static final GmailMessage SPOTIFY_RECEIPT = message("spotify-oct", "2026-10-01T08:00:00Z",
            "Spotify <no-reply@spotify.com>", "Your Spotify Premium receipt", "Thanks for your payment. Amount charged: $10.99 per month.");
    private static final GmailMessage OPENAI_FAILED = EmailFixtures.load("openai-payment-failed.json");
    private static final GmailMessage OPENAI_RECEIPT_OCT = openAiReceipt("openai-oct", "2026-10-05T03:00:00Z");
    private static final GmailMessage OPENAI_RECEIPT_NOV = openAiReceipt("openai-nov", "2026-11-06T03:00:00Z");

    @Autowired
    private GmailSyncService syncService;
    @Autowired
    private SubscriptionStatusJob statusJob;
    @Autowired
    private StatusChangeRepository statusChanges;
    @Autowired
    private TokenCipher tokenCipher;
    @Autowired
    private ApplicationEvents events;

    private TokenResponse tokens;
    private Long userId;

    @TestConfiguration
    static class FakeGoogleAndClock {
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
        CLOCK.setInstant(Instant.parse("2026-11-10T09:00:00Z"));
        exchangeRateRepository.save(new ExchangeRate(CurrencyCode.USD, new BigDecimal("12850.00"), LocalDate.now()));
        tokens = signIn("alex.doe@gmail.com");
        User user = userRepository.findByEmailIgnoreCase("alex.doe@gmail.com").orElseThrow();
        userId = user.getId();
        gmailConnectionRepository.save(new GmailConnection(user, "alex.doe@gmail.com", tokenCipher.encrypt("1//refresh"), "gmail.readonly gmail.send"));
    }

    @Test
    void sync_shouldKeepCancelledSubscriptionActiveUntilAccessEnds_evenWhenCancellationIsProcessedBeforeItsReceipt() {
        // given
        GMAIL.mailbox(SPOTIFY_CANCELLED, SPOTIFY_RECEIPT);

        // when
        syncService.sync(userId);

        // then
        Subscription spotify = subscription("spotify");
        assertThat(spotify.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(spotify.getEndsOn()).isEqualTo(LocalDate.of(2026, 12, 1));
        statusHistory(spotify.getId())
                .jsonPath("$[0].reason").isEqualTo("CANCELLATION_SCHEDULED")
                .jsonPath("$[0].endsOn").isEqualTo("2026-12-01")
                .jsonPath("$[0].emailSender").isEqualTo("Spotify <no-reply@spotify.com>")
                .jsonPath("$[0].emailReceivedAt").isEqualTo("2026-11-02T08:15:00Z")
                .jsonPath("$[0].gmailMessageId").isEqualTo(SPOTIFY_CANCELLED.id())
                .jsonPath("$[0].emailSubject").isEqualTo(SPOTIFY_CANCELLED.subject());
    }

    @Test
    void applyDailyRules_shouldNotMarkOverdue_whileAccessEndIsScheduled() {
        // given
        GMAIL.mailbox(SPOTIFY_CANCELLED, SPOTIFY_RECEIPT);
        syncService.sync(userId);
        CLOCK.setInstant(Instant.parse("2026-11-25T09:00:00Z"));

        // when
        statusJob.applyDailyRules();

        // then
        assertThat(subscription("spotify").getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void applyDailyRules_shouldCancel_whenAccessEndArrivesInUsersTimeZone() {
        // given
        GMAIL.mailbox(SPOTIFY_CANCELLED, SPOTIFY_RECEIPT);
        syncService.sync(userId);
        CLOCK.setInstant(Instant.parse("2026-11-30T20:00:00Z"));

        // when
        statusJob.applyDailyRules();

        // then
        Subscription spotify = subscription("spotify");
        assertThat(spotify.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(spotify.getEndsOn()).isNull();
        statusHistory(spotify.getId())
                .jsonPath("$[0].reason").isEqualTo("SCHEDULED_END")
                .jsonPath("$[0].source").isEqualTo("SYSTEM");
    }

    @Test
    void sync_shouldRequireBilling_whenPaymentFailureFollowsReceipt() {
        // given
        GMAIL.mailbox(OPENAI_FAILED, OPENAI_RECEIPT_OCT);

        // when
        syncService.sync(userId);

        // then
        Subscription openai = subscription("openai");
        assertThat(openai.getStatus()).isEqualTo(SubscriptionStatus.BILLING_REQUIRED);
        statusHistory(openai.getId())
                .jsonPath("$[0].toStatus").isEqualTo("BILLING_REQUIRED")
                .jsonPath("$[0].reason").isEqualTo("EMAIL_PAYMENT_FAILED")
                .jsonPath("$[0].emailSender").isEqualTo("OpenAI <noreply@tm.openai.com>")
                .jsonPath("$[0].emailReceivedAt").isEqualTo("2026-11-05T03:00:00Z");
    }

    @Test
    void sync_shouldReactivate_whenNewerReceiptFollowsPaymentFailure() {
        // given
        GMAIL.mailbox(OPENAI_FAILED, OPENAI_RECEIPT_OCT);
        syncService.sync(userId);
        deliverLater(OPENAI_RECEIPT_NOV);

        // when
        syncService.sync(userId);

        // then
        assertThat(subscription("openai").getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(events.stream(SubscriptionStatusChangedEvent.class))
                .extracting(SubscriptionStatusChangedEvent::toStatus)
                .containsExactly(SubscriptionStatus.BILLING_REQUIRED, SubscriptionStatus.ACTIVE);
    }

    @Test
    void sync_shouldIgnoreOlderPaymentFailure_thatArrivesAfterNewerReceipt() {
        // given
        GMAIL.mailbox(OPENAI_RECEIPT_NOV);
        syncService.sync(userId);
        deliverLater(OPENAI_FAILED);

        // when
        syncService.sync(userId);

        // then
        assertThat(subscription("openai").getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(statusChanges.findBySubscriptionIdOrderByEffectiveAtDescIdDesc(subscription("openai").getId())).isEmpty();
    }

    @Test
    void sync_shouldEndCancelled_whenOldReceiptsAreFollowedByCancellationWhoseAccessHasEnded() {
        // given
        GMAIL.mailbox(EmailFixtures.load("chatgpt-cancellation.json"),
                message("chatgpt-jul", "2026-07-21T03:00:00Z", "OpenAI <noreply@tm.openai.com>", "Your ChatGPT Plus receipt",
                        "Thanks for your payment. Amount charged: $20.00 per month."),
                message("chatgpt-jun", "2026-06-21T03:00:00Z", "OpenAI <noreply@tm.openai.com>", "Your ChatGPT Plus receipt",
                        "Thanks for your payment. Amount charged: $20.00 per month."));

        // when
        syncService.sync(userId);
        statusJob.applyDailyRules();

        // then
        assertThat(subscription("openai").getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
    }

    @Test
    void applyDailyRules_shouldRequireBilling_whenOldReceiptsHaveNoCancellation() {
        // given
        GMAIL.mailbox(
                message("chatgpt-jul", "2026-07-21T03:00:00Z", "OpenAI <noreply@tm.openai.com>", "Your ChatGPT Plus receipt",
                        "Thanks for your payment. Amount charged: $20.00 per month."),
                message("chatgpt-jun", "2026-06-21T03:00:00Z", "OpenAI <noreply@tm.openai.com>", "Your ChatGPT Plus receipt",
                        "Thanks for your payment. Amount charged: $20.00 per month."));

        // when
        syncService.sync(userId);
        statusJob.applyDailyRules();

        // then
        assertThat(subscription("openai").getStatus()).isEqualTo(SubscriptionStatus.BILLING_REQUIRED);
    }

    @Test
    void sync_shouldCancel_whenCancellationGivesNoAccessEndDate() {
        // given
        GmailMessage cancelledNow = message("spotify-now", "2026-11-02T08:15:00Z", "Spotify <no-reply@spotify.com>",
                "Your Premium subscription has been canceled", "Your Premium subscription has been canceled effective immediately.");
        GMAIL.mailbox(cancelledNow, SPOTIFY_RECEIPT);

        // when
        syncService.sync(userId);

        // then
        assertThat(subscription("spotify").getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        statusHistory(subscription("spotify").getId())
                .jsonPath("$[0].fromStatus").isEqualTo("ACTIVE")
                .jsonPath("$[0].toStatus").isEqualTo("CANCELLED")
                .jsonPath("$[0].reason").isEqualTo("EMAIL_CANCELLATION")
                .jsonPath("$[0].source").isEqualTo("EMAIL")
                .jsonPath("$[0].effectiveAt").isEqualTo("2026-11-02T08:15:00Z")
                .jsonPath("$[0].emailSender").isEqualTo("Spotify <no-reply@spotify.com>");
    }

    @Test
    void sync_shouldReplayUnattachedReceipt_soNewestEmailWinsWhenSubscriptionAppearsLater() {
        // given
        GmailMessage receiptWithoutAmount = message("openai-no-amount", "2026-11-10T03:00:00Z", "OpenAI <noreply@tm.openai.com>",
                "Your ChatGPT Plus receipt", "Thanks for your payment. See your account page for details.");
        GmailMessage cancelled = message("openai-cancel", "2026-11-01T03:00:00Z", "OpenAI <noreply@tm.openai.com>",
                "Your ChatGPT Plus subscription has been canceled", "Your subscription has been canceled.");
        GMAIL.mailbox(receiptWithoutAmount, cancelled, OPENAI_RECEIPT_OCT);
        GMAIL.failingIds.add(OPENAI_RECEIPT_OCT.id());
        syncService.sync(userId);
        GMAIL.failingIds.clear();

        // when
        syncService.sync(userId);

        // then
        assertThat(subscription("openai").getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void sync_shouldKeepManualCancel_whenOlderReceiptArrives() {
        // given
        GMAIL.mailbox(OPENAI_RECEIPT_OCT);
        syncService.sync(userId);
        changeStatus(subscription("openai").getId(), "CANCELLED");
        deliverLater(openAiReceipt("openai-nov-8", "2026-11-08T03:00:00Z"));

        // when
        syncService.sync(userId);

        // then
        assertThat(subscription("openai").getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
    }

    @Test
    void sync_shouldReactivateManualCancel_whenNewerReceiptArrives() {
        // given
        GMAIL.mailbox(OPENAI_RECEIPT_OCT);
        syncService.sync(userId);
        Long openaiId = subscription("openai").getId();
        changeStatus(openaiId, "CANCELLED");
        CLOCK.setInstant(Instant.parse("2026-11-16T09:00:00Z"));
        deliverLater(openAiReceipt("openai-nov-15", "2026-11-15T03:00:00Z"));

        // when
        syncService.sync(userId);

        // then
        assertThat(subscription("openai").getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        statusHistory(openaiId)
                .jsonPath("$[0].reason").isEqualTo("EMAIL_RECEIPT")
                .jsonPath("$[0].fromStatus").isEqualTo("CANCELLED")
                .jsonPath("$[1].reason").isEqualTo("USER");
        assertThat(events.stream(SubscriptionStatusChangedEvent.class))
                .singleElement()
                .satisfies(event -> assertThat(event.toStatus()).isEqualTo(SubscriptionStatus.ACTIVE));
    }

    @Test
    void sync_shouldReactivate_whenReceiptPaidAfterDueDateSyncsAfterOverdueFlip() {
        // given
        GMAIL.mailbox(OPENAI_RECEIPT_OCT);
        syncService.sync(userId);
        CLOCK.setInstant(Instant.parse("2026-11-12T09:00:00Z"));
        statusJob.applyDailyRules();
        assertThat(subscription("openai").getStatus()).isEqualTo(SubscriptionStatus.BILLING_REQUIRED);
        deliverLater(openAiReceipt("openai-nov-9", "2026-11-09T03:00:00Z"));

        // when
        syncService.sync(userId);

        // then
        assertThat(subscription("openai").getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void applyDailyRules_shouldNotMarkOverdue_whenReceiptWithoutAmountArrivedAfterDueDate() {
        // given
        GMAIL.mailbox(OPENAI_RECEIPT_OCT);
        syncService.sync(userId);
        deliverLater(message("openai-nov-no-amount", "2026-11-06T03:00:00Z", "OpenAI <noreply@tm.openai.com>",
                "Your ChatGPT Plus receipt", "Thanks for your payment. See your account page for details."));
        syncService.sync(userId);
        CLOCK.setInstant(Instant.parse("2026-11-20T09:00:00Z"));

        // when
        statusJob.applyDailyRules();

        // then
        Subscription openai = subscription("openai");
        assertThat(openai.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(openai.getNextPaymentDate()).isEqualTo(LocalDate.of(2026, 12, 6));
        assertThat(openai.isNeedsReview()).isTrue();
    }

    @Test
    void applyDailyRules_shouldNotUndoManualActive_afterOverdueFlip() {
        // given
        Subscription subscription = subscriptionDueOn(userId, LocalDate.of(2026, 10, 31));
        statusJob.applyDailyRules();
        changeStatus(subscription.getId(), "ACTIVE");
        CLOCK.setInstant(Instant.parse("2026-11-10T10:00:00Z"));

        // when
        statusJob.applyDailyRules();

        // then
        assertThat(statusOf(subscription)).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void applyDailyRules_shouldMarkOverdue_atMidnightOfUsersTimeZone_notUtc() {
        // given
        Subscription tashkent = subscriptionDueOn(userId, LocalDate.of(2026, 10, 31));
        Subscription utc = subscriptionDueOn(userInZone("utc@example.com", "UTC"), LocalDate.of(2026, 10, 31));

        // when / then
        assertStatusAt("2026-11-05T18:59:00Z", tashkent, SubscriptionStatus.ACTIVE);
        assertStatusAt("2026-11-05T19:01:00Z", tashkent, SubscriptionStatus.BILLING_REQUIRED);
        assertThat(statusOf(utc)).isEqualTo(SubscriptionStatus.ACTIVE);
        assertStatusAt("2026-11-06T00:01:00Z", utc, SubscriptionStatus.BILLING_REQUIRED);
    }

    @Test
    void applyDailyRules_shouldMarkOverdue_atMidnightOfExtremeTimeZones() {
        // given
        Subscription plusFourteen = subscriptionDueOn(userInZone("kiritimati@example.com", "Pacific/Kiritimati"), LocalDate.of(2026, 10, 31));
        Subscription minusTwelve = subscriptionDueOn(userInZone("baker@example.com", "Etc/GMT+12"), LocalDate.of(2026, 10, 31));

        // when / then
        assertStatusAt("2026-11-05T09:59:00Z", plusFourteen, SubscriptionStatus.ACTIVE);
        assertStatusAt("2026-11-05T10:01:00Z", plusFourteen, SubscriptionStatus.BILLING_REQUIRED);
        assertStatusAt("2026-11-06T11:59:00Z", minusTwelve, SubscriptionStatus.ACTIVE);
        assertStatusAt("2026-11-06T12:01:00Z", minusTwelve, SubscriptionStatus.BILLING_REQUIRED);
    }

    @Test
    void applyDailyRules_shouldLeaveSubscriptionActive_whenReceiptArrivedWithinGracePeriod() {
        // given
        Subscription subscription = subscriptionDueOn(userId, LocalDate.of(2026, 10, 31));
        subscription.recordPayment(LocalDate.of(2026, 11, 3), new BigDecimal("9.99"), CurrencyCode.USD,
                BillingFrequency.MONTHLY, null, com.mirkamolcode.detection.Confidence.HIGH);
        subscriptionRepository.save(subscription);

        // when
        statusJob.applyDailyRules();

        // then
        assertThat(statusOf(subscription)).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void applyDailyRules_shouldRecordOverdueOnlyOnce_whenRunRepeatedly() {
        // given
        Subscription subscription = subscriptionDueOn(userId, LocalDate.of(2026, 10, 31));

        // when
        statusJob.applyDailyRules();
        statusJob.applyDailyRules();

        // then
        statusHistory(subscription.getId())
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].reason").isEqualTo("OVERDUE")
                .jsonPath("$[0].source").isEqualTo("SYSTEM")
                .jsonPath("$[0].effectiveAt").isEqualTo("2026-10-30T19:00:00Z");
        assertThat(events.stream(SubscriptionStatusChangedEvent.class)).hasSize(1);
    }

    @Test
    void changeStatus_shouldRecordUserChange() {
        // given
        Subscription subscription = subscriptionDueOn(userId, LocalDate.of(2026, 11, 30));

        // when
        changeStatus(subscription.getId(), "CANCELLED");

        // then
        statusHistory(subscription.getId())
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].toStatus").isEqualTo("CANCELLED")
                .jsonPath("$[0].source").isEqualTo("USER")
                .jsonPath("$[0].emailSender").doesNotExist();
    }

    @Test
    void update_shouldRouteStatusChangeThroughStatusRules_whenVersionMatches() {
        // given
        Subscription subscription = subscriptionDueOn(userId, LocalDate.of(2026, 11, 30));

        // when
        webTestClient.put().uri(API + "/subscriptions/" + subscription.getId()).header("Authorization", bearer(tokens))
                .bodyValue(editRequest(SubscriptionStatus.CANCELLED, subscription.getVersion()))
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("CANCELLED");

        // then
        statusHistory(subscription.getId())
                .jsonPath("$[0].toStatus").isEqualTo("CANCELLED")
                .jsonPath("$[0].source").isEqualTo("USER");
    }

    @Test
    void update_shouldRejectStatusChange_whenVersionIsMissing() {
        // given
        Subscription subscription = subscriptionDueOn(userId, LocalDate.of(2026, 11, 30));

        // when / then
        webTestClient.put().uri(API + "/subscriptions/" + subscription.getId()).header("Authorization", bearer(tokens))
                .bodyValue(editRequest(SubscriptionStatus.CANCELLED, null))
                .exchange()
                .expectStatus().isEqualTo(409);
        assertThat(statusOf(subscription)).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void changeStatus_shouldNotExposeOrChangeAnotherUsersSubscription() {
        // given
        Subscription subscription = subscriptionDueOn(userId, LocalDate.of(2026, 11, 30));
        String intruder = bearer(signIn("intruder@example.com"));

        // when / then
        webTestClient.put().uri(API + "/subscriptions/" + subscription.getId() + "/status").header("Authorization", intruder)
                .bodyValue(Map.of("status", "CANCELLED")).exchange()
                .expectStatus().isNotFound();
        webTestClient.get().uri(API + "/subscriptions/" + subscription.getId() + "/status-history").header("Authorization", intruder).exchange()
                .expectStatus().isNotFound();
        assertThat(statusOf(subscription)).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    private org.springframework.test.web.reactive.server.WebTestClient.BodyContentSpec statusHistory(Long subscriptionId) {
        return webTestClient.get().uri(API + "/subscriptions/" + subscriptionId + "/status-history").header("Authorization", bearer(tokens))
                .exchange()
                .expectStatus().isOk()
                .expectBody();
    }

    private void deliverLater(GmailMessage message) {
        GMAIL.historyAdds(message.id());
        GMAIL.deliver(message);
    }

    private void assertStatusAt(String instant, Subscription subscription, SubscriptionStatus expected) {
        CLOCK.setInstant(Instant.parse(instant));
        statusJob.applyDailyRules();
        assertThat(statusOf(subscription)).isEqualTo(expected);
    }

    private Long userInZone(String email, String zone) {
        User user = userRepository.save(new User(email, Set.of(com.mirkamolcode.model.Role.USER)));
        user.changeTimeZone(ZoneId.of(zone));
        return userRepository.save(user).getId();
    }

    private static SubscriptionRequest editRequest(SubscriptionStatus status, Long version) {
        return new SubscriptionRequest("Gym", new BigDecimal("9.99"), CurrencyCode.USD, BillingFrequency.MONTHLY,
                status, SubscriptionCategory.HEALTH, LocalDate.of(2026, 1, 30), version);
    }

    private void changeStatus(Long subscriptionId, String status) {
        webTestClient.put().uri(API + "/subscriptions/" + subscriptionId + "/status").header("Authorization", bearer(tokens))
                .bodyValue(Map.of("status", status)).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo(status);
    }

    private Subscription subscriptionDueOn(Long ownerId, LocalDate nextPaymentDate) {
        User owner = userRepository.findById(ownerId).orElseThrow();
        return subscriptionRepository.save(new Subscription(owner, "Gym", new BigDecimal("9.99"), CurrencyCode.USD,
                BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.HEALTH,
                nextPaymentDate.minusMonths(1), nextPaymentDate));
    }

    private SubscriptionStatus statusOf(Subscription subscription) {
        return subscriptionRepository.findById(subscription.getId()).orElseThrow().getStatus();
    }

    private Subscription subscription(String merchantKey) {
        return subscriptionRepository.findByUserIdAndMerchantKeyAndIsDeletedFalse(userId, merchantKey).orElseThrow();
    }

    private static GmailMessage openAiReceipt(String id, String receivedAt) {
        return message(id, receivedAt, "OpenAI <noreply@tm.openai.com>", "Your ChatGPT Plus receipt",
                "Thanks for your payment. Amount paid: $20.00 per month for ChatGPT Plus.");
    }

    private static GmailMessage message(String id, String receivedAt, String from, String subject, String text) {
        return new GmailMessage(id, id, Instant.parse(receivedAt), from, subject, null, text);
    }
}
