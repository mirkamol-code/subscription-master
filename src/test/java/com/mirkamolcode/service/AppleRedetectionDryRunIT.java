package com.mirkamolcode.service;

import com.mirkamolcode.AbstractTestConfig;
import com.mirkamolcode.detection.Confidence;
import com.mirkamolcode.detection.EmailFixtures;
import com.mirkamolcode.detection.EmailKind;
import com.mirkamolcode.entity.BillingEmail;
import com.mirkamolcode.entity.ExchangeRate;
import com.mirkamolcode.entity.GmailConnection;
import com.mirkamolcode.entity.PaymentHistory;
import com.mirkamolcode.entity.Subscription;
import com.mirkamolcode.entity.User;
import com.mirkamolcode.integration.gmail.GmailClient;
import com.mirkamolcode.integration.gmail.GmailMessage;
import com.mirkamolcode.integration.google.GoogleOAuthClient;
import com.mirkamolcode.model.BillingFrequency;
import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.model.SubscriptionCategory;
import com.mirkamolcode.model.SubscriptionStatus;
import com.mirkamolcode.repository.BillingEmailRepository;
import com.mirkamolcode.security.TokenCipher;
import com.mirkamolcode.service.AppleRedetectionReport.Decision;
import com.mirkamolcode.service.AppleRedetectionReport.Outcome;
import com.mirkamolcode.service.AppleRedetectionReport.UserPlan;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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

class AppleRedetectionDryRunIT extends AbstractTestConfig {
    private static final FakeGmail GMAIL = new FakeGmail();
    private static final FakeGoogleOAuth GOOGLE = new FakeGoogleOAuth();
    private static final List<String> WATCHED_TABLES = List.of("subscriptions", "payment_history", "billing_emails", "status_changes",
            "notifications", "merchant_tombstones", "failed_gmail_messages", "gmail_connections");

    @Autowired
    private AppleRedetectionDryRun underTest;
    @Autowired
    private BillingEmailRepository billingEmails;
    @Autowired
    private TokenCipher tokenCipher;
    @Autowired
    private JdbcTemplate jdbc;

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
    }

    @Test
    void run_shouldPredictPerServiceSubscriptions_forUserWhoseAppleEmailsAreAllReadable() {
        // given
        GmailMessage multi = EmailFixtures.load("apple-multi-item-invoice.json");
        GmailMessage telegram = EmailFixtures.load("apple-telegram-invoice.json");
        GMAIL.mailbox(multi, telegram);
        User alice = connectedUser("alice@gmail.com");
        Subscription legacy = legacyAppleSubscription(alice, false);
        legacyPayment(legacy, legacyEmail(alice, multi, legacy), "34.97");
        legacyPayment(legacy, legacyEmail(alice, telegram, legacy), "0.43");

        // when
        UserPlan plan = underTest.run().planFor(alice.getId());

        // then
        assertThat(plan.outcome()).isEqualTo(Outcome.READY);
        assertThat(plan.legacySubscriptions()).isEqualTo(1);
        assertThat(plan.legacyPayments()).isEqualTo(2);
        assertThat(plan.emails()).isEqualTo(2);
        assertThat(plan.emailsMissing()).isZero();
        assertThat(plan.services()).extracting(AppleRedetectionReport.PredictedService::name)
                .containsExactlyInAnyOrder("Telegram Messenger", "iCloud+");
        assertThat(plan.paymentsRebuilt()).isEqualTo(3);
        assertThat(plan.paymentsKeptInHistoryRow()).isZero();
        assertThat(plan.hidden()).isEqualTo(Decision.NOT_NEEDED);
    }

    @Test
    void run_shouldCarryHiddenFlag_onlyWhenAppleRowBecomesExactlyOneService() {
        // given
        GmailMessage telegram = EmailFixtures.load("apple-telegram-invoice.json");
        GmailMessage multi = EmailFixtures.load("apple-multi-item-invoice.json");
        GMAIL.mailbox(telegram, multi);
        User single = connectedUser("single@gmail.com");
        Subscription singleLegacy = legacyAppleSubscription(single, true);
        legacyPayment(singleLegacy, legacyEmail(single, telegram, singleLegacy), "3.99");
        User several = connectedUser("several@gmail.com");
        Subscription severalLegacy = legacyAppleSubscription(several, true);
        legacyPayment(severalLegacy, legacyEmail(several, multi, severalLegacy), "34.97");

        // when
        AppleRedetectionReport report = underTest.run();

        // then
        assertThat(report.planFor(single.getId()).hidden()).isEqualTo(Decision.CARRY);
        assertThat(report.planFor(several.getId()).hidden()).isEqualTo(Decision.AMBIGUOUS);
    }

    @Test
    void run_shouldReportMissingEmails_andKeepTheirPaymentsForEarlierPurchasesRow() {
        // given
        GmailMessage telegram = EmailFixtures.load("apple-telegram-invoice.json");
        GmailMessage multi = EmailFixtures.load("apple-multi-item-invoice.json");
        GMAIL.mailbox(telegram, multi);
        User bob = connectedUser("bob@gmail.com");
        Subscription legacy = legacyAppleSubscription(bob, false);
        legacyPayment(legacy, legacyEmail(bob, telegram, legacy), "3.99");
        legacyPayment(legacy, legacyEmail(bob, multi, legacy), "34.97");
        GMAIL.deleteMessage(multi.id());

        // when
        UserPlan plan = underTest.run().planFor(bob.getId());

        // then
        assertThat(plan.outcome()).isEqualTo(Outcome.MISSING_EMAILS);
        assertThat(plan.emailsMissing()).isEqualTo(1);
        assertThat(plan.paymentsRebuilt()).isEqualTo(1);
        assertThat(plan.paymentsKeptInHistoryRow()).isEqualTo(1);
    }

    @Test
    void run_shouldWaitForReconnect_whenGmailIsDisconnected() {
        // given
        GmailMessage telegram = EmailFixtures.load("apple-telegram-invoice.json");
        GMAIL.mailbox(telegram);
        User carol = connectedUser("carol@gmail.com");
        Subscription legacy = legacyAppleSubscription(carol, false);
        legacyPayment(legacy, legacyEmail(carol, telegram, legacy), "3.99");
        GmailConnection connection = gmailConnectionRepository.findByUserId(carol.getId()).orElseThrow();
        connection.disconnect();
        gmailConnectionRepository.save(connection);

        // when
        UserPlan plan = underTest.run().planFor(carol.getId());

        // then
        assertThat(plan.outcome()).isEqualTo(Outcome.WAITING_FOR_GMAIL);
        assertThat(GMAIL.fetchedIds).isEmpty();
    }

    @Test
    void run_shouldWaitForReconnect_withoutMarkingConnectionDisconnected_whenGoogleRevokedAccess() {
        // given
        GmailMessage telegram = EmailFixtures.load("apple-telegram-invoice.json");
        GMAIL.mailbox(telegram);
        User erin = connectedUser("erin@gmail.com");
        Subscription legacy = legacyAppleSubscription(erin, false);
        legacyPayment(legacy, legacyEmail(erin, telegram, legacy), "3.99");
        GOOGLE.revokeAfterRefreshes(0);

        // when
        UserPlan plan = underTest.run().planFor(erin.getId());

        // then
        assertThat(plan.outcome()).isEqualTo(Outcome.WAITING_FOR_GMAIL);
        assertThat(gmailConnectionRepository.findByUserId(erin.getId()).orElseThrow().isConnected()).isTrue();
    }

    @Test
    void run_shouldNotWriteAnythingOrSendMail() {
        // given
        GmailMessage multi = EmailFixtures.load("apple-multi-item-invoice.json");
        GMAIL.mailbox(multi);
        User alice = connectedUser("alice@gmail.com");
        Subscription legacy = legacyAppleSubscription(alice, true);
        legacyPayment(legacy, legacyEmail(alice, multi, legacy), "34.97");
        Map<String, Integer> before = countRows();

        // when
        AppleRedetectionReport report = underTest.run();

        // then
        assertThat(report.users()).hasSize(1);
        assertThat(countRows()).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from subscriptions where merchant_key = 'apple'", Integer.class)).isEqualTo(1);
        assertThat(GMAIL.sentRawMessages).isEmpty();
    }

    @Test
    void summaryLines_shouldTotalEveryUser_withoutCardOrInvoiceDetails() {
        // given
        GmailMessage multi = EmailFixtures.load("apple-multi-item-invoice.json");
        GmailMessage telegram = EmailFixtures.load("apple-telegram-invoice.json");
        GMAIL.mailbox(multi, telegram);
        User ready = connectedUser("ready@gmail.com");
        Subscription readyLegacy = legacyAppleSubscription(ready, true);
        legacyPayment(readyLegacy, legacyEmail(ready, multi, readyLegacy), "34.97");
        legacyPayment(readyLegacy, legacyEmail(ready, telegram, readyLegacy), "0.43");
        User missing = connectedUser("missing@gmail.com");
        Subscription missingLegacy = legacyAppleSubscription(missing, false);
        BillingEmail gone = legacyEmail(missing, new GmailMessage("gone-1", "gone-1", Instant.parse("2025-06-06T10:00:00Z"),
                "Apple <no_reply@email.apple.com>", "Your invoice from Apple.", null, ""), missingLegacy);
        legacyPayment(missingLegacy, gone, "9.99");
        User away = connectedUser("away@gmail.com");
        Subscription awayLegacy = legacyAppleSubscription(away, false);
        legacyPayment(awayLegacy, legacyEmail(away, telegram, awayLegacy), "3.99");
        GmailConnection awayConnection = gmailConnectionRepository.findByUserId(away.getId()).orElseThrow();
        awayConnection.disconnect();
        gmailConnectionRepository.save(awayConnection);

        // when
        List<String> summary = underTest.run().summaryLines();
        summary.forEach(line -> System.out.println("[apple-dry-run] " + line));

        // then
        assertThat(summary.getFirst()).isEqualTo("users=3 ready=1 waitingForGmail=1 missingEmails=1 legacySubscriptions=3 legacyPayments=4 "
                + "emails=4 emailsMissing=1 predictedServices=2 paymentsRebuilt=3 paymentsKeptInHistoryRow=1 oneTimeEmails=0 "
                + "unreadAppleEmails=0 confirmedAmountsCarried=0 ambiguousPayments=0 carryDecisions=0 ambiguousDecisions=1");
        assertThat(String.join("\n", summary)).doesNotContain("4242").doesNotContain("Visa").doesNotContain("MT0000TEST")
                .doesNotContain("@gmail.com");
    }

    @Test
    void run_shouldIgnoreUsersWithoutLegacyAppleRows() {
        // given
        connectedUser("dave@gmail.com");

        // when / then
        assertThat(underTest.run().users()).isEmpty();
    }

    private User connectedUser(String email) {
        signIn(email);
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        gmailConnectionRepository.save(new GmailConnection(user, email, tokenCipher.encrypt("1//" + email), "gmail.readonly gmail.send"));
        return user;
    }

    private Subscription legacyAppleSubscription(User user, boolean hidden) {
        Subscription legacy = new Subscription(user, "Apple (App Store / iCloud)", new BigDecimal("3.99"), CurrencyCode.USD,
                BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE, SubscriptionCategory.OTHER, LocalDate.of(2025, 12, 6));
        legacy.linkMerchant("apple", "apple.com");
        if (hidden)
            legacy.hide();
        return subscriptionRepository.save(legacy);
    }

    private BillingEmail legacyEmail(User user, GmailMessage message, Subscription legacy) {
        BillingEmail email = BillingEmail.relevant(user, message.id(), message.threadId(), EmailKind.RECEIPT, message.from(),
                message.subject(), message.receivedAt(), Confidence.LOW, "apple", null);
        email.attachTo(legacy);
        return billingEmails.save(email);
    }

    private void legacyPayment(Subscription legacy, BillingEmail email, String amount) {
        paymentHistoryRepository.save(new PaymentHistory(legacy, email, LocalDate.ofInstant(email.getReceivedAt(), java.time.ZoneOffset.UTC),
                new BigDecimal(amount), CurrencyCode.USD, new BigDecimal("12850.00")));
    }

    private Map<String, Integer> countRows() {
        return WATCHED_TABLES.stream().collect(java.util.stream.Collectors.toMap(table -> table,
                table -> jdbc.queryForObject("select count(*) from " + table, Integer.class)));
    }
}
