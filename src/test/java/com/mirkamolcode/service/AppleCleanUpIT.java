package com.mirkamolcode.service;

import com.mirkamolcode.AbstractTestConfig;
import com.mirkamolcode.detection.Confidence;
import com.mirkamolcode.detection.EmailFixtures;
import com.mirkamolcode.detection.EmailKind;
import com.mirkamolcode.dto.request.SubscriptionRequest;
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
import com.mirkamolcode.service.AppleCleanUp.UserOutcome;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class AppleCleanUpIT extends AbstractTestConfig {
    private static final FakeGmail GMAIL = new FakeGmail();
    private static final FakeGoogleOAuth GOOGLE = new FakeGoogleOAuth();

    @Autowired
    private AppleCleanUp underTest;
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
    void apply_shouldBackUpAndRebuildOneRowPerService_whenEveryAppleEmailIsReadable() {
        // given
        GmailMessage multi = EmailFixtures.load("apple-multi-item-invoice.json");
        GmailMessage telegram = EmailFixtures.load("apple-telegram-invoice.json");
        GMAIL.mailbox(multi, telegram);
        User alice = connectedUser("alice@gmail.com");
        Subscription legacy = legacyAppleSubscription(alice, false);
        legacyPayment(legacy, legacyEmail(alice, multi, legacy), "34.97");
        legacyPayment(legacy, legacyEmail(alice, telegram, legacy), "0.43");

        // when
        UserOutcome outcome = underTest.apply().get(alice.getId());

        // then
        assertThat(outcome).isEqualTo(UserOutcome.REBUILT);
        assertThat(subscriptionRepository.findById(legacy.getId()).orElseThrow().isDeleted()).isTrue();
        assertThat(liveMerchantKeys(alice)).containsExactlyInAnyOrder("telegram-messenger", "icloud");
        assertThat(backedUpRows(alice, "subscriptions")).isEqualTo(1);
        assertThat(backedUpRows(alice, "payment_history")).isEqualTo(2);
        assertThat(backedUpRows(alice, "billing_emails")).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from failed_gmail_messages", Integer.class)).isZero();
    }

    @Test
    void apply_shouldWait_whileAppleEmailsAreMissing_andRebuildFromReadableOnesOnFifthAttempt() {
        // given
        GmailMessage telegram = EmailFixtures.load("apple-telegram-invoice.json");
        GmailMessage deleted = EmailFixtures.load("apple-claude-receipt.json");
        GMAIL.mailbox(telegram);
        User bob = connectedUser("bob@gmail.com");
        Subscription legacy = legacyAppleSubscription(bob, false);
        legacyPayment(legacy, legacyEmail(bob, telegram, legacy), "0.43");
        legacyPayment(legacy, legacyEmail(bob, deleted, legacy), "20.00");

        // when
        for (int attempt = 1; attempt <= 4; attempt++)
            assertThat(underTest.apply().get(bob.getId())).isEqualTo(UserOutcome.WAITING_FOR_MISSING_EMAILS);

        // then
        assertThat(subscriptionRepository.findById(legacy.getId()).orElseThrow().isDeleted()).isFalse();

        // when
        UserOutcome fifth = underTest.apply().get(bob.getId());

        // then
        assertThat(fifth).isEqualTo(UserOutcome.REBUILT);
        Subscription earlier = subscriptionRepository.findAllByUserIdAndMerchantKeyAndIsDeletedFalse(bob.getId(),
                AppleCleanUp.EARLIER_PURCHASES_KEY).getFirst();
        assertThat(earlier.getName()).isEqualTo("Apple (earlier purchases)");
        assertThat(earlier.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(earlier.isReadOnly()).isTrue();
        assertThat(paymentHistoryRepository.findBySubscriptionIdOrderByPaymentDateDesc(earlier.getId()))
                .extracting(payment -> payment.getOriginalAmount().stripTrailingZeros().toPlainString())
                .containsExactly("20");
        assertThat(liveMerchantKeys(bob)).contains("telegram-messenger");
    }

    @Test
    void apply_shouldKeepGoingForOtherUsers_whenOneUsersGmailFails() {
        // given
        GmailMessage telegram = EmailFixtures.load("apple-telegram-invoice.json");
        GmailMessage multi = EmailFixtures.load("apple-multi-item-invoice.json");
        GMAIL.mailbox(telegram, multi);
        GMAIL.failingIds.add(multi.id());
        User failing = connectedUser("failing@gmail.com");
        Subscription failingLegacy = legacyAppleSubscription(failing, false);
        legacyPayment(failingLegacy, legacyEmail(failing, multi, failingLegacy), "34.97");
        User fine = connectedUser("fine@gmail.com");
        Subscription fineLegacy = legacyAppleSubscription(fine, false);
        legacyPayment(fineLegacy, legacyEmail(fine, telegram, fineLegacy), "0.43");

        // when
        var outcomes = underTest.apply();

        // then
        assertThat(outcomes.get(failing.getId())).isEqualTo(UserOutcome.FAILED);
        assertThat(subscriptionRepository.findById(failingLegacy.getId()).orElseThrow().isDeleted()).isFalse();
        assertThat(backedUpRows(failing, "subscriptions")).isZero();
        assertThat(outcomes.get(fine.getId())).isEqualTo(UserOutcome.REBUILT);
    }

    @Test
    void apply_shouldChangeNothing_whileGmailIsDisconnected() {
        // given
        GmailMessage telegram = EmailFixtures.load("apple-telegram-invoice.json");
        GMAIL.mailbox(telegram);
        User carol = connectedUser("carol@gmail.com");
        Subscription legacy = legacyAppleSubscription(carol, false);
        legacyPayment(legacy, legacyEmail(carol, telegram, legacy), "0.43");
        jdbc.update("update gmail_connections set status = 'DISCONNECTED' where user_id = ?", carol.getId());

        // when
        UserOutcome outcome = underTest.apply().get(carol.getId());

        // then
        assertThat(outcome).isEqualTo(UserOutcome.WAITING_FOR_GMAIL);
        assertThat(subscriptionRepository.findById(legacy.getId()).orElseThrow().isDeleted()).isFalse();
        assertThat(backedUpRows(carol, "subscriptions")).isZero();
    }

    @Test
    void apply_shouldCarryHiddenFlag_whenAppleRowBecomesExactlyOneService() {
        // given
        GmailMessage telegram = EmailFixtures.load("apple-telegram-invoice.json");
        GMAIL.mailbox(telegram);
        User dave = connectedUser("dave@gmail.com");
        Subscription legacy = legacyAppleSubscription(dave, true);
        legacyPayment(legacy, legacyEmail(dave, telegram, legacy), "0.43");

        // when
        underTest.apply();

        // then
        Subscription rebuilt = subscriptionRepository.findAllByUserIdAndMerchantKeyAndIsDeletedFalse(dave.getId(), "telegram-messenger")
                .getFirst();
        assertThat(rebuilt.isHidden()).isTrue();
    }

    @Test
    void update_shouldRejectChangesToEarlierPurchasesRow() {
        // given
        GmailMessage deleted = EmailFixtures.load("apple-claude-receipt.json");
        User erin = connectedUser("erin@gmail.com");
        Subscription legacy = legacyAppleSubscription(erin, false);
        legacyPayment(legacy, legacyEmail(erin, deleted, legacy), "20.00");
        for (int attempt = 1; attempt <= 5; attempt++)
            underTest.apply();
        Subscription earlier = subscriptionRepository.findAllByUserIdAndMerchantKeyAndIsDeletedFalse(erin.getId(),
                AppleCleanUp.EARLIER_PURCHASES_KEY).getFirst();

        // when / then
        webTestClient.put().uri(API + "/subscriptions/" + earlier.getId()).header("Authorization", bearer(signIn("erin@gmail.com")))
                .bodyValue(new SubscriptionRequest("Renamed", BigDecimal.TEN, CurrencyCode.USD, BillingFrequency.MONTHLY,
                        SubscriptionStatus.CANCELLED, SubscriptionCategory.OTHER, LocalDate.of(2025, 12, 6)))
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    private List<String> liveMerchantKeys(User user) {
        return subscriptionRepository.findAll().stream()
                .filter(subscription -> subscription.getUser().getId().equals(user.getId()) && !subscription.isDeleted())
                .map(Subscription::getMerchantKey)
                .toList();
    }

    private int backedUpRows(User user, String table) {
        return jdbc.queryForObject("select count(*) from apple_clean_up_backups where user_id = ? and table_name = ?", Integer.class,
                user.getId(), table);
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
        legacy.linkMerchant(LegacyAppleRows.LEGACY_APPLE_KEY, "apple.com");
        if (hidden)
            legacy.hide();
        return subscriptionRepository.save(legacy);
    }

    private BillingEmail legacyEmail(User user, GmailMessage message, Subscription legacy) {
        BillingEmail email = BillingEmail.relevant(user, message.id(), message.threadId(), EmailKind.RECEIPT, message.from(),
                message.subject(), message.receivedAt(), Confidence.LOW, LegacyAppleRows.LEGACY_APPLE_KEY, null);
        email.attachTo(legacy);
        return billingEmails.save(email);
    }

    private void legacyPayment(Subscription legacy, BillingEmail email, String amount) {
        paymentHistoryRepository.save(new PaymentHistory(legacy, email, LocalDate.ofInstant(email.getReceivedAt(), ZoneOffset.UTC),
                new BigDecimal(amount), CurrencyCode.USD, new BigDecimal("12850.00")));
    }
}
