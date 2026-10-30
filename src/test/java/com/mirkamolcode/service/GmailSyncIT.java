package com.mirkamolcode.service;

import com.mirkamolcode.AbstractTestConfig;
import com.mirkamolcode.detection.EmailFixtures;
import com.mirkamolcode.dto.AuthDtos.TokenResponse;
import com.mirkamolcode.entity.ExchangeRate;
import com.mirkamolcode.entity.GmailConnection;
import com.mirkamolcode.entity.Subscription;
import com.mirkamolcode.entity.User;
import com.mirkamolcode.integration.gmail.GmailClient;
import com.mirkamolcode.integration.gmail.GmailHistoryExpiredException;
import com.mirkamolcode.integration.gmail.GmailHistoryPage;
import com.mirkamolcode.integration.gmail.GmailMessage;
import com.mirkamolcode.integration.gmail.GmailMessagePage;
import com.mirkamolcode.integration.gmail.GmailTransientException;
import com.mirkamolcode.integration.gmail.GmailUnauthorizedException;
import com.mirkamolcode.integration.google.GoogleOAuthClient;
import com.mirkamolcode.integration.google.GoogleTokens;
import com.mirkamolcode.integration.google.InvalidGrantException;
import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.model.GmailConnectionStatus;
import com.mirkamolcode.security.TokenCipher;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import com.mirkamolcode.event.SubscriptionRetrackedEvent;

import static org.assertj.core.api.Assertions.assertThat;

@RecordApplicationEvents
class GmailSyncIT extends AbstractTestConfig {
    private static final FakeGmail GMAIL = new FakeGmail();
    private static final FakeGoogleOAuth GOOGLE = new FakeGoogleOAuth();

    private static final GmailMessage NETFLIX_OCT = EmailFixtures.load("netflix-receipt.json");
    private static final GmailMessage NETFLIX_SEP = copy(NETFLIX_OCT, "netflix-sep", "2026-09-30T09:00:00Z", "$22.99", "$19.99");
    private static final GmailMessage NETFLIX_NOV = copy(NETFLIX_OCT, "netflix-nov", "2026-11-30T09:00:00Z", "November 30, 2026", "December 30, 2026");
    private static final GmailMessage ADOBE = EmailFixtures.load("adobe-invoice-with-pdf.json");
    private static final GmailMessage SPOTIFY_CANCELLED = EmailFixtures.load("spotify-cancellation.json");
    private static final GmailMessage NEWSLETTER = EmailFixtures.load("shop-newsletter-save-money.json");
    private static final GmailMessage BANK_PROMO = EmailFixtures.load("bank-promo-cashback.json");

    @Autowired
    private GmailSyncService underTest;
    @Autowired
    private GmailSyncScheduler scheduler;
    @Autowired
    private TokenCipher tokenCipher;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ApplicationEvents events;

    private TokenResponse tokens;
    private Long userId;

    @TestConfiguration
    static class FakeGoogle {
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
    }

    @BeforeEach
    void setUp() {
        GMAIL.reset();
        GOOGLE.reset();
        exchangeRateRepository.save(new ExchangeRate(CurrencyCode.USD, new BigDecimal("12850.00"), LocalDate.now()));
        tokens = signIn("alex.doe@gmail.com");
        User user = userRepository.findByEmailIgnoreCase("alex.doe@gmail.com").orElseThrow();
        userId = user.getId();
        gmailConnectionRepository.save(new GmailConnection(user, "alex.doe@gmail.com", tokenCipher.encrypt("1//refresh"), "gmail.readonly gmail.send"));
    }

    @Test
    void sync_shouldGroupReceiptsIntoOneSubscriptionPerMerchant_withOnePaymentPerReceipt() {
        // given
        GMAIL.mailbox(NETFLIX_OCT, ADOBE, NETFLIX_SEP, SPOTIFY_CANCELLED, NEWSLETTER, BANK_PROMO);

        // when
        boolean ran = underTest.sync(userId);

        // then
        assertThat(ran).isTrue();
        List<Subscription> subscriptions = subscriptionsOfUser();
        assertThat(subscriptions).extracting(Subscription::getMerchantKey).containsExactlyInAnyOrder("netflix", "adobe");

        Subscription netflix = subscription("netflix");
        assertThat(netflix.getName()).isEqualTo("Netflix");
        assertThat(netflix.getPrice()).isEqualByComparingTo("22.99");
        assertThat(netflix.getLastPaymentDate()).isEqualTo(LocalDate.of(2026, 10, 30));
        assertThat(netflix.getNextPaymentDate()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(netflix.isNeedsReview()).isFalse();
        assertThat(paymentHistoryRepository.findBySubscriptionIdOrderByPaymentDateDesc(netflix.getId()))
                .extracting(payment -> payment.getOriginalAmount().stripTrailingZeros().toPlainString())
                .containsExactly("22.99", "19.99");

        Subscription adobe = subscription("adobe");
        assertThat(adobe.isNeedsReview()).isTrue();
        assertThat(paymentHistoryRepository.findBySubscriptionIdOrderByPaymentDateDesc(adobe.getId())).hasSize(1);

        assertThat(countRows("billing_emails")).isEqualTo(6);
        webTestClient.get().uri(API + "/gmail/sync-status").header("Authorization", bearer(tokens)).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.state").isEqualTo("IDLE")
                .jsonPath("$.processed").isEqualTo(6)
                .jsonPath("$.total").isEqualTo(6)
                .jsonPath("$.lastSyncedAt").exists()
                .jsonPath("$.error").doesNotExist();
    }

    @Test
    void sync_shouldAdoptMatchingManualSubscription_insteadOfCreatingDuplicate() {
        // given
        User user = userRepository.findById(userId).orElseThrow();
        Subscription manual = subscriptionRepository.save(new Subscription(user, "netflix", new BigDecimal("15.00"), CurrencyCode.USD,
                com.mirkamolcode.model.BillingFrequency.MONTHLY, com.mirkamolcode.model.SubscriptionStatus.ACTIVE,
                com.mirkamolcode.model.SubscriptionCategory.ENTERTAINMENT, LocalDate.of(2026, 1, 30)));
        GMAIL.mailbox(NETFLIX_OCT);

        // when
        underTest.sync(userId);

        // then
        assertThat(subscriptionsOfUser()).hasSize(1);
        Subscription adopted = subscription("netflix");
        assertThat(adopted.getId()).isEqualTo(manual.getId());
        assertThat(adopted.getPrice()).isEqualByComparingTo("22.99");
    }

    @Test
    void sync_shouldNotDuplicateSubscriptionsOrPayments_whenSameMessagesAppearAgain() {
        // given
        GMAIL.mailbox(NETFLIX_OCT, ADOBE, NETFLIX_SEP);
        underTest.sync(userId);
        GMAIL.historyAdds(NETFLIX_OCT.id(), ADOBE.id(), NETFLIX_NOV.id());
        GMAIL.deliver(NETFLIX_NOV);

        // when
        underTest.sync(userId);

        // then
        assertThat(subscriptionsOfUser()).hasSize(2);
        Subscription netflix = subscription("netflix");
        assertThat(paymentHistoryRepository.findBySubscriptionIdOrderByPaymentDateDesc(netflix.getId())).hasSize(3);
        assertThat(netflix.getLastPaymentDate()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(countRows("billing_emails")).isEqualTo(4);
        assertThat(GMAIL.fetchedIds).containsExactly(NETFLIX_OCT.id(), ADOBE.id(), NETFLIX_SEP.id(), NETFLIX_NOV.id());
    }

    @Test
    void sync_shouldStartFromHistoryIdSavedByPreviousSync() {
        // given
        GMAIL.mailbox(NETFLIX_OCT);
        GMAIL.currentHistoryId = "500";
        underTest.sync(userId);
        GMAIL.historyAdds(NETFLIX_NOV.id());
        GMAIL.deliver(NETFLIX_NOV);

        // when
        underTest.sync(userId);

        // then
        assertThat(GMAIL.historyStartIds).containsExactly("500");
        assertThat(GMAIL.listQueries).hasSize(1);
        assertThat(subscription("netflix").getLastPaymentDate()).isEqualTo(LocalDate.of(2026, 11, 30));
    }

    @Test
    void sync_shouldFallBackToListingMailSinceLastSync_whenGmailHistoryExpired() {
        // given
        GMAIL.mailbox(NETFLIX_OCT);
        underTest.sync(userId);
        Instant lastSyncedAt = gmailConnectionRepository.findByUserId(userId).orElseThrow().getLastSyncedAt();
        GMAIL.historyExpired = true;
        GMAIL.mailbox(NETFLIX_NOV, NETFLIX_OCT);

        // when
        underTest.sync(userId);

        // then
        assertThat(GMAIL.listQueries).hasSize(2);
        assertThat(GMAIL.listQueries.get(1)).contains("after:" + lastSyncedAt.minus(Duration.ofDays(1)).getEpochSecond());
        assertThat(paymentHistoryRepository.findBySubscriptionIdOrderByPaymentDateDesc(subscription("netflix").getId())).hasSize(2);
        assertThat(GMAIL.fetchedIds).containsExactly(NETFLIX_OCT.id(), NETFLIX_NOV.id());
        GmailConnection connection = gmailConnectionRepository.findByUserId(userId).orElseThrow();
        assertThat(connection.getSyncState().name()).isEqualTo("IDLE");
        assertThat(connection.getSyncError()).isNull();
    }

    @Test
    void sync_shouldContinuePastBadEmail_andRetryItOnNextSync() {
        // given
        GMAIL.mailbox(NETFLIX_OCT, ADOBE, NETFLIX_SEP);
        GMAIL.failingIds.add(ADOBE.id());

        // when
        underTest.sync(userId);

        // then
        assertThat(subscriptionsOfUser()).extracting(Subscription::getMerchantKey).containsExactly("netflix");
        assertThat(paymentHistoryRepository.findBySubscriptionIdOrderByPaymentDateDesc(subscription("netflix").getId())).hasSize(2);
        GmailConnection afterFirst = gmailConnectionRepository.findByUserId(userId).orElseThrow();
        assertThat(afterFirst.getSyncState().name()).isEqualTo("IDLE");
        assertThat(afterFirst.getSyncError()).isEqualTo("SOME_EMAILS_FAILED");
        assertThat(afterFirst.getHistoryId()).isEqualTo("100");

        // when
        GMAIL.failingIds.clear();
        underTest.sync(userId);

        // then
        assertThat(subscriptionsOfUser()).extracting(Subscription::getMerchantKey).containsExactlyInAnyOrder("netflix", "adobe");
        assertThat(paymentHistoryRepository.findBySubscriptionIdOrderByPaymentDateDesc(subscription("netflix").getId())).hasSize(2);
        assertThat(gmailConnectionRepository.findByUserId(userId).orElseThrow().getSyncError()).isNull();
    }

    @Test
    void sync_shouldKeepHiddenSubscriptionHidden_andNotRecreateIt() {
        // given
        GMAIL.mailbox(NETFLIX_OCT);
        underTest.sync(userId);
        Long netflixId = subscription("netflix").getId();
        webTestClient.post().uri(API + "/subscriptions/" + netflixId + "/hide").header("Authorization", bearer(tokens)).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.hidden").isEqualTo(true);
        GMAIL.historyAdds(NETFLIX_NOV.id());
        GMAIL.deliver(NETFLIX_NOV);

        // when
        underTest.sync(userId);

        // then
        assertThat(subscriptionsOfUser()).hasSize(1);
        assertThat(subscription("netflix").isHidden()).isTrue();
        webTestClient.get().uri(API + "/subscriptions").header("Authorization", bearer(tokens)).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.content.length()").isEqualTo(0);
        webTestClient.get().uri(API + "/subscriptions?hidden=true").header("Authorization", bearer(tokens)).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.content[0].id").isEqualTo(netflixId.intValue());
    }

    @Test
    void sync_shouldRunExactlyOnce_whenTriggeredTwiceAtTheSameMoment() throws Exception {
        // given
        GMAIL.mailbox(NETFLIX_OCT);
        GMAIL.blockListing();
        ExecutorService threads = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        // when
        Future<Boolean> first = threads.submit(() -> {
            start.await();
            return underTest.sync(userId);
        });
        Future<Boolean> second = threads.submit(() -> {
            start.await();
            return underTest.sync(userId);
        });
        start.countDown();
        GMAIL.awaitListingStarted();
        boolean secondFinishedWhileFirstRuns = first.isDone() ^ second.isDone();
        GMAIL.releaseListing();

        // then
        assertThat(secondFinishedWhileFirstRuns).isTrue();
        assertThat(List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        assertThat(GMAIL.listQueries).hasSize(1);
        threads.shutdown();
    }

    @Test
    void requestSync_shouldBeRejected_whileScheduledSyncIsRunning() throws Exception {
        // given
        GMAIL.mailbox(NETFLIX_OCT);
        GMAIL.blockListing();
        ExecutorService threads = Executors.newSingleThreadExecutor();
        Future<?> scheduled = threads.submit(scheduler::syncAllConnectedMailboxes);
        GMAIL.awaitListingStarted();

        // when / then
        webTestClient.post().uri(API + "/gmail/sync").header("Authorization", bearer(tokens)).exchange()
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.errorCode").isEqualTo("SYNC_ALREADY_RUNNING");
        webTestClient.get().uri(API + "/gmail/sync-status").header("Authorization", bearer(tokens)).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.state").isEqualTo("RUNNING");

        GMAIL.releaseListing();
        scheduled.get(5, TimeUnit.SECONDS);
        threads.shutdown();
        assertThat(GMAIL.listQueries).hasSize(1);
    }

    @Test
    void sync_shouldFailWithReconnectRequired_whenRefreshTokenIsRevokedMidSync_andResumeCleanlyLater() {
        // given
        GMAIL.mailbox(NETFLIX_OCT, NETFLIX_SEP, ADOBE);
        GMAIL.revokeAccessAfterFetches(2);
        GOOGLE.revokeAfterRefreshes(1);

        // when
        underTest.sync(userId);

        // then
        GmailConnection failed = gmailConnectionRepository.findByUserId(userId).orElseThrow();
        assertThat(failed.getSyncState().name()).isEqualTo("FAILED");
        assertThat(failed.getSyncError()).isEqualTo("RECONNECT_REQUIRED");
        assertThat(failed.getStatus()).isEqualTo(GmailConnectionStatus.DISCONNECTED);
        assertThat(paymentHistoryRepository.findBySubscriptionIdOrderByPaymentDateDesc(subscription("netflix").getId())).hasSize(2);
        webTestClient.get().uri(API + "/gmail/sync-status").header("Authorization", bearer(tokens)).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.state").isEqualTo("FAILED")
                .jsonPath("$.error").isEqualTo("RECONNECT_REQUIRED");

        // when
        GMAIL.restoreAccess();
        GOOGLE.reset();
        failed.reconnect("alex.doe@gmail.com", tokenCipher.encrypt("1//new-refresh"), "gmail.readonly gmail.send");
        gmailConnectionRepository.save(failed);
        underTest.sync(userId);

        // then
        assertThat(subscriptionsOfUser()).extracting(Subscription::getMerchantKey).containsExactlyInAnyOrder("netflix", "adobe");
        assertThat(paymentHistoryRepository.findBySubscriptionIdOrderByPaymentDateDesc(subscription("netflix").getId())).hasSize(2);
        assertThat(countRows("billing_emails")).isEqualTo(3);
        assertThat(gmailConnectionRepository.findByUserId(userId).orElseThrow().getSyncState().name()).isEqualTo("IDLE");
    }

    @Test
    void sync_shouldPersistOnlyExtractedFieldsAndAttachmentMetadata_neverBodiesOrPdfBytes() {
        // given
        GMAIL.mailbox(ADOBE, NETFLIX_OCT, NEWSLETTER);

        // when
        underTest.sync(userId);

        // then
        assertThat(jdbc.queryForList("select filename, mime_type, size_bytes, gmail_attachment_id from email_attachments"))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.get("filename")).isEqualTo("Invoice_1234567890.pdf");
                    assertThat(row.get("mime_type")).isEqualTo("application/pdf");
                    assertThat(row.get("size_bytes")).isEqualTo(48213L);
                    assertThat(row.get("gmail_attachment_id")).isEqualTo("ANGjdJ8_recorded_attachment_id_adobe");
                });
        String everythingStored = String.join("\n", jdbc.queryForList(
                "select to_jsonb(t)::text from (select * from billing_emails) t "
                        + "union all select to_jsonb(t)::text from (select * from email_attachments) t "
                        + "union all select to_jsonb(t)::text from (select * from subscriptions) t "
                        + "union all select to_jsonb(t)::text from (select * from payment_history) t", String.class));
        assertThat(everythingStored)
                .doesNotContain("Thanks for your purchase")
                .doesNotContain("Invoice number: 1234567890")
                .doesNotContain("Payment method: Visa")
                .doesNotContain("Start your monthly snack subscription")
                .doesNotContain("Save $10 on your first subscription box");
        assertThat(columnsOf("billing_emails")).containsExactlyInAnyOrder("id", "user_id", "subscription_id", "gmail_message_id",
                "thread_id", "kind", "sender", "subject", "received_at", "confidence", "created_at", "updated_at");
        assertThat(columnsOf("email_attachments")).containsExactlyInAnyOrder("id", "billing_email_id", "gmail_attachment_id",
                "filename", "mime_type", "size_bytes", "created_at", "updated_at");
        assertThat(jdbc.queryForList("select sender, subject from billing_emails where kind = 'IRRELEVANT'"))
                .allSatisfy(row -> assertThat(row.values()).containsOnlyNulls());
    }

    @Test
    void requestSync_shouldAcceptRequest_andRejectWhenGmailIsNotConnected() {
        // given
        GMAIL.mailbox(NETFLIX_OCT);

        // when / then
        webTestClient.post().uri(API + "/gmail/sync").header("Authorization", bearer(tokens)).exchange()
                .expectStatus().isAccepted();
        assertThat(subscription("netflix")).isNotNull();

        String otherUser = bearer(signIn("no-gmail@example.com"));
        webTestClient.post().uri(API + "/gmail/sync").header("Authorization", otherUser).exchange()
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.errorCode").isEqualTo("GMAIL_RECONNECT_REQUIRED");
    }

    @Test
    void findEmails_shouldListOwnBillingEmailsWithAttachments_andHideThemFromOtherUsers() {
        // given
        GMAIL.mailbox(ADOBE);
        underTest.sync(userId);
        Long adobeId = subscription("adobe").getId();

        // when / then
        webTestClient.get().uri(API + "/subscriptions/" + adobeId + "/emails").header("Authorization", bearer(tokens)).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].kind").isEqualTo("RECEIPT")
                .jsonPath("$[0].sender").isEqualTo("Adobe <message@adobe.com>")
                .jsonPath("$[0].subject").isEqualTo("Your Adobe invoice is ready")
                .jsonPath("$[0].attachments[0].filename").isEqualTo("Invoice_1234567890.pdf")
                .jsonPath("$[0].attachments[0].sizeBytes").isEqualTo(48213);

        webTestClient.get().uri(API + "/subscriptions/" + adobeId + "/emails").header("Authorization", bearer(signIn("intruder@example.com"))).exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void update_shouldClearNeedsReview_forDetectedSubscription() {
        // given
        GMAIL.mailbox(ADOBE);
        underTest.sync(userId);
        Subscription adobe = subscription("adobe");

        // when / then
        webTestClient.put().uri(API + "/subscriptions/" + adobe.getId()).header("Authorization", bearer(tokens))
                .bodyValue(new com.mirkamolcode.dto.request.SubscriptionRequest("Adobe Creative Cloud", new BigDecimal("59.99"), CurrencyCode.USD,
                        com.mirkamolcode.model.BillingFrequency.MONTHLY, com.mirkamolcode.model.SubscriptionStatus.ACTIVE,
                        com.mirkamolcode.model.SubscriptionCategory.PRODUCTIVITY, LocalDate.of(2026, 10, 12)))
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.needsReview").isEqualTo(false);
    }

    @Test
    void sync_shouldRetryPermanentlyFailingEmailAtMostThreeTimes_withoutBlockingHistory() {
        // given
        GMAIL.mailbox(NETFLIX_OCT, ADOBE);
        GMAIL.failingIds.add(ADOBE.id());
        underTest.sync(userId);

        // when
        GMAIL.historyAdds(NETFLIX_NOV.id());
        GMAIL.deliver(NETFLIX_NOV);
        underTest.sync(userId);
        underTest.sync(userId);
        underTest.sync(userId);

        // then
        assertThat(GMAIL.attempts(ADOBE.id())).isEqualTo(3);
        assertThat(subscription("netflix").getLastPaymentDate()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(GMAIL.historyStartIds).containsOnly("100");
        assertThat(gmailConnectionRepository.findByUserId(userId).orElseThrow().getSyncError()).isNull();
    }

    @Test
    void sync_shouldTreatHeaderFetchFailureAsOneBadEmail_inIncrementalMode() {
        // given
        GMAIL.mailbox(NETFLIX_OCT);
        underTest.sync(userId);
        GMAIL.historyAdds(ADOBE.id(), NETFLIX_NOV.id());
        GMAIL.deliver(ADOBE);
        GMAIL.deliver(NETFLIX_NOV);
        GMAIL.failingHeaderIds.add(ADOBE.id());

        // when
        underTest.sync(userId);

        // then
        GmailConnection connection = gmailConnectionRepository.findByUserId(userId).orElseThrow();
        assertThat(connection.getSyncState().name()).isEqualTo("IDLE");
        assertThat(connection.getSyncError()).isEqualTo("SOME_EMAILS_FAILED");
        assertThat(subscription("netflix").getLastPaymentDate()).isEqualTo(LocalDate.of(2026, 11, 30));

        // when
        GMAIL.failingHeaderIds.clear();
        GMAIL.historyAdds();
        underTest.sync(userId);

        // then
        assertThat(subscriptionsOfUser()).extracting(Subscription::getMerchantKey).containsExactlyInAnyOrder("netflix", "adobe");
    }

    @Test
    void sync_shouldKeepItsClaim_whileItIsStillMakingProgress() throws Exception {
        // given
        GMAIL.mailbox(NETFLIX_OCT, NETFLIX_SEP);
        GMAIL.blockFetchOf(NETFLIX_SEP.id());
        ExecutorService threads = Executors.newSingleThreadExecutor();
        Future<Boolean> running = threads.submit(() -> underTest.sync(userId));
        GMAIL.awaitBlockedFetch();
        jdbc.update("update gmail_connections set sync_started_at = now() - interval '2 hours' where user_id = ?", userId);

        // when
        boolean secondClaimed = underTest.sync(userId);

        // then
        assertThat(secondClaimed).isFalse();
        GMAIL.releaseFetch();
        assertThat(running.get(5, TimeUnit.SECONDS)).isTrue();
        threads.shutdown();
    }

    @Test
    void sync_shouldNotLetStaleRunOverwriteNewerRun_whenItWakesUp() throws Exception {
        // given
        GMAIL.mailbox(NETFLIX_OCT);
        GMAIL.blockListing();
        ExecutorService threads = Executors.newSingleThreadExecutor();
        Future<Boolean> stale = threads.submit(() -> underTest.sync(userId));
        GMAIL.awaitListingStarted();
        jdbc.update("update gmail_connections set sync_started_at = now() - interval '2 hours', sync_heartbeat_at = now() - interval '2 hours' where user_id = ?", userId);

        // when
        boolean newer = underTest.sync(userId);
        GmailConnection afterNewer = gmailConnectionRepository.findByUserId(userId).orElseThrow();
        GMAIL.releaseListing();
        stale.get(5, TimeUnit.SECONDS);
        threads.shutdown();

        // then
        assertThat(newer).isTrue();
        GmailConnection finalState = gmailConnectionRepository.findByUserId(userId).orElseThrow();
        assertThat(finalState.getSyncState().name()).isEqualTo("IDLE");
        assertThat(finalState.getLastSyncedAt()).isEqualTo(afterNewer.getLastSyncedAt());
        assertThat(finalState.getSyncProcessed()).isEqualTo(1);
        assertThat(countRows("billing_emails")).isEqualTo(1);
        assertThat(paymentHistoryRepository.findBySubscriptionIdOrderByPaymentDateDesc(subscription("netflix").getId())).hasSize(1);
    }

    @Test
    void sync_shouldAdoptManualSubscription_namedAfterMerchantAlias() {
        // given
        User user = userRepository.findById(userId).orElseThrow();
        Subscription manual = subscriptionRepository.save(new Subscription(user, "OpenAI", new BigDecimal("20.00"), CurrencyCode.USD,
                com.mirkamolcode.model.BillingFrequency.MONTHLY, com.mirkamolcode.model.SubscriptionStatus.ACTIVE,
                com.mirkamolcode.model.SubscriptionCategory.AI_TOOLS, LocalDate.of(2026, 1, 5)));
        GMAIL.mailbox(new GmailMessage("openai-receipt", "openai-receipt", Instant.parse("2026-10-05T03:00:00Z"),
                "OpenAI <noreply@tm.openai.com>", "Your ChatGPT Plus receipt", null,
                "Thanks for your payment. Amount paid: $20.00 per month for ChatGPT Plus."));

        // when
        underTest.sync(userId);

        // then
        assertThat(subscriptionsOfUser()).hasSize(1);
        assertThat(subscription("openai").getId()).isEqualTo(manual.getId());
    }

    @Test
    void sync_shouldNotRecreateDeletedSubscription_fromEmailsOlderThanDeletion() {
        // given
        GmailMessage olderReceipt = receiptFromNetflix("netflix-before-delete", Instant.now().minus(Duration.ofDays(20)));
        GMAIL.mailbox(receiptFromNetflix("netflix-recent", Instant.now().minus(Duration.ofDays(5))), olderReceipt);
        GMAIL.failingIds.add(olderReceipt.id());
        underTest.sync(userId);
        deleteSubscription(subscription("netflix").getId());

        // when
        GMAIL.failingIds.clear();
        jdbc.update("update gmail_connections set history_id = null where user_id = ?", userId);
        underTest.sync(userId);

        // then
        assertThat(subscriptionRepository.findByUserIdAndMerchantKeyAndIsDeletedFalse(userId, "netflix")).isEmpty();
        assertThat(events.stream(SubscriptionRetrackedEvent.class)).isEmpty();
    }

    @Test
    void sync_shouldCreateFreshSubscription_andAnnounceRetracking_whenReceiptArrivesAfterDeletion() {
        // given
        GMAIL.mailbox(receiptFromNetflix("netflix-before", Instant.now().minus(Duration.ofDays(5))));
        underTest.sync(userId);
        Long deletedId = subscription("netflix").getId();
        deleteSubscription(deletedId);
        Instant chargedAgain = Instant.now().plus(Duration.ofDays(1));
        GmailMessage newReceipt = receiptFromNetflix("netflix-after", chargedAgain);
        GMAIL.historyAdds(newReceipt.id());
        GMAIL.deliver(newReceipt);

        // when
        underTest.sync(userId);

        // then
        Subscription fresh = subscription("netflix");
        assertThat(fresh.getId()).isNotEqualTo(deletedId);
        assertThat(paymentHistoryRepository.findBySubscriptionIdOrderByPaymentDateDesc(fresh.getId())).hasSize(1);
        assertThat(events.stream(SubscriptionRetrackedEvent.class))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.userId()).isEqualTo(userId);
                    assertThat(event.subscriptionId()).isEqualTo(fresh.getId());
                    assertThat(event.merchantName()).isEqualTo("Netflix");
                    assertThat(event.chargedOn()).isEqualTo(LocalDate.ofInstant(chargedAgain, java.time.ZoneOffset.UTC));
                });
    }

    @Test
    void get_shouldExposeMerchantLogoDomain_forDetectedSubscription() {
        // given
        GMAIL.mailbox(NETFLIX_OCT);
        underTest.sync(userId);

        // when / then
        webTestClient.get().uri(API + "/subscriptions/" + subscription("netflix").getId()).header("Authorization", bearer(tokens)).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.merchantKey").isEqualTo("netflix")
                .jsonPath("$.logoDomain").isEqualTo("netflix.com");
    }

    private void deleteSubscription(Long id) {
        webTestClient.delete().uri(API + "/subscriptions/" + id).header("Authorization", bearer(tokens)).exchange()
                .expectStatus().isNoContent();
    }

    private static GmailMessage receiptFromNetflix(String id, Instant receivedAt) {
        return new GmailMessage(id, id, receivedAt, NETFLIX_OCT.from(), NETFLIX_OCT.subject(), NETFLIX_OCT.htmlBody(), null);
    }

    private List<Subscription> subscriptionsOfUser() {
        return subscriptionRepository.findAll().stream()
                .filter(subscription -> subscription.getUser().getId().equals(userId))
                .toList();
    }

    private Subscription subscription(String merchantKey) {
        return subscriptionRepository.findByUserIdAndMerchantKeyAndIsDeletedFalse(userId, merchantKey).orElseThrow();
    }

    private List<String> columnsOf(String table) {
        return jdbc.queryForList("select column_name from information_schema.columns where table_name = ?", String.class, table);
    }

    private int countRows(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    private static GmailMessage copy(GmailMessage source, String id, String receivedAt, String from, String to) {
        return new GmailMessage(id, id, Instant.parse(receivedAt), source.from(), source.subject(),
                source.htmlBody() == null ? null : source.htmlBody().replace(from, to),
                source.textBody() == null ? null : source.textBody().replace(from, to),
                source.attachments());
    }

    static final class FakeGmail implements GmailClient {
        private final ConcurrentHashMap<String, GmailMessage> messages = new ConcurrentHashMap<>();
        private volatile List<String> listedIds = List.of();
        private volatile List<String> historyAdded = List.of();
        final List<String> listQueries = new CopyOnWriteArrayList<>();
        final List<String> historyStartIds = new CopyOnWriteArrayList<>();
        final List<String> fetchedIds = new CopyOnWriteArrayList<>();
        final Set<String> failingIds = ConcurrentHashMap.newKeySet();
        final Set<String> failingHeaderIds = ConcurrentHashMap.newKeySet();
        private final ConcurrentHashMap<String, AtomicInteger> attemptsById = new ConcurrentHashMap<>();
        private final java.util.concurrent.atomic.AtomicBoolean listingBlockPending = new java.util.concurrent.atomic.AtomicBoolean();
        private volatile String blockedFetchId;
        private volatile CountDownLatch fetchBlocked = new CountDownLatch(0);
        private volatile CountDownLatch fetchReleased = new CountDownLatch(0);
        volatile boolean historyExpired;
        volatile String currentHistoryId;
        private volatile int revokeAfterFetches = -1;
        private final AtomicInteger fetches = new AtomicInteger();
        private volatile CountDownLatch listingStarted = new CountDownLatch(0);
        private volatile CountDownLatch listingReleased = new CountDownLatch(0);

        void reset() {
            messages.clear();
            listedIds = List.of();
            historyAdded = List.of();
            listQueries.clear();
            historyStartIds.clear();
            fetchedIds.clear();
            failingIds.clear();
            failingHeaderIds.clear();
            attemptsById.clear();
            listingBlockPending.set(false);
            blockedFetchId = null;
            fetchBlocked = new CountDownLatch(0);
            fetchReleased = new CountDownLatch(0);
            historyExpired = false;
            currentHistoryId = "100";
            revokeAfterFetches = -1;
            fetches.set(0);
            listingStarted = new CountDownLatch(0);
            listingReleased = new CountDownLatch(0);
        }

        void mailbox(GmailMessage... newestFirst) {
            List<String> ids = new ArrayList<>();
            for (GmailMessage message : newestFirst) {
                messages.put(message.id(), message);
                ids.add(message.id());
            }
            listedIds = List.copyOf(ids);
        }

        void deliver(GmailMessage message) {
            messages.put(message.id(), message);
        }

        void historyAdds(String... ids) {
            historyAdded = List.of(ids);
        }

        void blockListing() {
            listingStarted = new CountDownLatch(1);
            listingReleased = new CountDownLatch(1);
            listingBlockPending.set(true);
        }

        void blockFetchOf(String messageId) {
            blockedFetchId = messageId;
            fetchBlocked = new CountDownLatch(1);
            fetchReleased = new CountDownLatch(1);
        }

        void awaitBlockedFetch() throws InterruptedException {
            assertThat(fetchBlocked.await(5, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(200);
        }

        void releaseFetch() {
            fetchReleased.countDown();
        }

        int attempts(String messageId) {
            AtomicInteger count = attemptsById.get(messageId);
            return count == null ? 0 : count.get();
        }

        void awaitListingStarted() throws InterruptedException {
            assertThat(listingStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(200);
        }

        void releaseListing() {
            listingReleased.countDown();
        }

        void revokeAccessAfterFetches(int count) {
            revokeAfterFetches = count;
        }

        void restoreAccess() {
            revokeAfterFetches = -1;
        }

        @Override
        public GmailMessagePage listMessageIds(String accessToken, String query, String pageToken) {
            listQueries.add(query);
            if (listingBlockPending.compareAndSet(true, false)) {
                listingStarted.countDown();
                awaitQuietly(listingReleased);
            }
            return new GmailMessagePage(listedIds, null);
        }

        @Override
        public GmailMessage getMessage(String accessToken, String messageId) {
            attemptsById.computeIfAbsent(messageId, id -> new AtomicInteger()).incrementAndGet();
            if (messageId.equals(blockedFetchId)) {
                blockedFetchId = null;
                fetchBlocked.countDown();
                awaitQuietly(fetchReleased);
            }
            if (revokeAfterFetches >= 0 && fetches.get() >= revokeAfterFetches)
                throw new GmailUnauthorizedException("Gmail API failed with 401 authError");
            if (failingIds.contains(messageId))
                throw new GmailTransientException("Gmail API failed with 503");
            fetches.incrementAndGet();
            fetchedIds.add(messageId);
            return messages.get(messageId);
        }

        @Override
        public GmailMessage getMessageHeaders(String accessToken, String messageId) {
            if (failingHeaderIds.contains(messageId))
                throw new GmailTransientException("Gmail API failed with 500");
            GmailMessage message = messages.get(messageId);
            return new GmailMessage(message.id(), message.threadId(), message.receivedAt(), message.from(), message.subject(), null, null);
        }

        @Override
        public GmailHistoryPage listHistory(String accessToken, String startHistoryId, String pageToken) {
            historyStartIds.add(startHistoryId);
            if (historyExpired)
                throw new GmailHistoryExpiredException(startHistoryId);
            return new GmailHistoryPage(historyAdded, null, currentHistoryId);
        }

        @Override
        public String fetchCurrentHistoryId(String accessToken) {
            return currentHistoryId;
        }

        private static void awaitQuietly(CountDownLatch latch) {
            try {
                latch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    static final class FakeGoogleOAuth implements GoogleOAuthClient {
        private final AtomicInteger refreshes = new AtomicInteger();
        private volatile int revokeAfterRefreshes = -1;

        void reset() {
            refreshes.set(0);
            revokeAfterRefreshes = -1;
        }

        void revokeAfterRefreshes(int count) {
            revokeAfterRefreshes = count;
        }

        @Override
        public GoogleTokens refreshAccessToken(String refreshToken) {
            if (revokeAfterRefreshes >= 0 && refreshes.get() >= revokeAfterRefreshes)
                throw new InvalidGrantException("Token has been expired or revoked.");
            refreshes.incrementAndGet();
            return new GoogleTokens("ya29.fake-" + refreshes.get(), Duration.ofHours(1));
        }
    }
}
