package com.mirkamolcode.service;

import com.mirkamolcode.AbstractTestConfig;
import com.mirkamolcode.detection.EmailFixtures;
import com.mirkamolcode.entity.BillingEmail;
import com.mirkamolcode.entity.ExchangeRate;
import com.mirkamolcode.entity.GmailConnection;
import com.mirkamolcode.entity.Subscription;
import com.mirkamolcode.entity.User;
import com.mirkamolcode.integration.gmail.GmailClient;
import com.mirkamolcode.integration.gmail.GmailMessage;
import com.mirkamolcode.integration.google.GoogleOAuthClient;
import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.model.SubscriptionStatus;
import com.mirkamolcode.security.TokenCipher;
import com.mirkamolcode.service.TrialRecheck.UserCounts;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class TrialRecheckIT extends AbstractTestConfig {
    private static final FakeGmail GMAIL = new FakeGmail();
    private static final FakeGoogleOAuth GOOGLE = new FakeGoogleOAuth();
    private static final GmailMessage TRIAL = EmailFixtures.load("trial-started-14-day.json");
    private static final GmailMessage NEWSLETTER = EmailFixtures.load("shop-newsletter-save-money.json");

    @Autowired
    private TrialRecheck underTest;
    @Autowired
    private TokenCipher tokenCipher;
    @Autowired
    private JdbcTemplate jdbc;

    private User user;

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
        signIn("alex.doe@gmail.com");
        user = userRepository.findByEmailIgnoreCase("alex.doe@gmail.com").orElseThrow();
        gmailConnectionRepository.save(new GmailConnection(user, "alex.doe@gmail.com", tokenCipher.encrypt("1//refresh"), "gmail.readonly gmail.send"));
        GMAIL.mailbox(TRIAL, NEWSLETTER);
        storeAsIrrelevant(TRIAL);
        storeAsIrrelevant(NEWSLETTER);
    }

    @Test
    void run_shouldOnlyReportMissedTrials_andChangeNothing_inDryRun() {
        // when
        UserCounts counts = underTest.run(false).get(user.getId());

        // then
        assertThat(counts).isEqualTo(new UserCounts(1, 1, 0, 0));
        assertThat(subscriptionRepository.findAll()).isEmpty();
        assertThat(kindOf(TRIAL)).isEqualTo("IRRELEVANT");
    }

    @Test
    void run_shouldTrackMissedTrial_whenApplied() {
        // when
        UserCounts counts = underTest.run(true).get(user.getId());

        // then
        assertThat(counts).isEqualTo(new UserCounts(1, 0, 1, 0));
        Subscription trial = subscriptionRepository.findAllByUserIdAndMerchantKeyAndIsDeletedFalse(user.getId(), "acme-family-tree").getFirst();
        assertThat(trial.getStatus()).isEqualTo(SubscriptionStatus.TRIAL);
        assertThat(trial.getTrialEndsOn()).isEqualTo(LocalDate.of(2026, 11, 18));
        assertThat(trial.isTrialEndEstimated()).isTrue();
        assertThat(trial.isNeedsReview()).isTrue();
        assertThat(kindOf(TRIAL)).isEqualTo("TRIAL_STARTED");
    }

    @Test
    void run_shouldLeaveNonTrialEmailsUntouched() {
        // when
        underTest.run(true);

        // then
        assertThat(kindOf(NEWSLETTER)).isEqualTo("IRRELEVANT");
    }

    @Test
    void run_shouldNotDuplicateTrial_whenRunTwice() {
        // given
        underTest.run(true);

        // when
        UserCounts second = underTest.run(true).get(user.getId());

        // then
        assertThat(second).isEqualTo(new UserCounts(0, 0, 0, 1));
        assertThat(subscriptionRepository.findAll()).hasSize(1);
    }

    private void storeAsIrrelevant(GmailMessage message) {
        billingEmailRepository.save(BillingEmail.irrelevant(user, message.id(), message.receivedAt()));
    }

    private String kindOf(GmailMessage message) {
        List<String> kinds = jdbc.queryForList("select kind from billing_emails where gmail_message_id = ?", String.class, message.id());
        return kinds.getFirst();
    }
}
