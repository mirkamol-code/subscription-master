package com.mirkamolcode.detection;

import com.mirkamolcode.integration.gmail.GmailMessage;
import com.mirkamolcode.integration.gmail.MessageHeaders;
import com.mirkamolcode.model.BillingFrequency;
import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.model.SubscriptionCategory;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BillingEmailAnalyzerTest {
    private final DetectionProperties properties = EmailFixtures.detectionProperties();
    private final BillingEmailAnalyzer underTest = new BillingEmailAnalyzer(
            new EmailClassifier(properties),
            new MerchantResolver(properties),
            new SenderVerifier(properties),
            List.of(new HeuristicParser(), new NetflixParser(), new SchemaOrgParser()));

    @Test
    void analyze_shouldReadSenderSpecificReceipt_withHighConfidence() {
        // when
        BillingEmailAnalysis analysis = underTest.analyze(EmailFixtures.load("netflix-receipt.json"));

        // then
        assertThat(analysis.kind()).isEqualTo(EmailKind.RECEIPT);
        assertThat(analysis.merchant().key()).isEqualTo("netflix");
        assertThat(analysis.merchant().displayName()).isEqualTo("Netflix");
        assertThat(analysis.merchant().category()).isEqualTo(SubscriptionCategory.ENTERTAINMENT);
        assertThat(analysis.billing().amount()).isEqualByComparingTo("22.99");
        assertThat(analysis.billing().currency()).isEqualTo(CurrencyCode.USD);
        assertThat(analysis.billing().cycle()).isEqualTo(BillingFrequency.MONTHLY);
        assertThat(analysis.billing().paymentDate()).isEqualTo(LocalDate.of(2026, 10, 30));
        assertThat(analysis.billing().nextBillingDate()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(analysis.billing().confidence()).isEqualTo(Confidence.HIGH);
    }

    @Test
    void analyze_shouldPreferSchemaOrgMarkup_overHeuristics() {
        // when
        BillingEmailAnalysis analysis = underTest.analyze(EmailFixtures.load("notion-receipt-schemaorg.json"));

        // then
        assertThat(analysis.kind()).isEqualTo(EmailKind.RECEIPT);
        assertThat(analysis.merchant().key()).isEqualTo("notion");
        assertThat(analysis.billing().amount()).isEqualByComparingTo("10.00");
        assertThat(analysis.billing().cycle()).isEqualTo(BillingFrequency.MONTHLY);
        assertThat(analysis.billing().paymentDate()).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(analysis.billing().confidence()).isEqualTo(Confidence.HIGH);
    }

    @Test
    void analyze_shouldFallBackToHeuristics_andComputeNextDateFromCycle() {
        // when
        BillingEmailAnalysis analysis = underTest.analyze(EmailFixtures.load("adobe-invoice-with-pdf.json"));

        // then
        assertThat(analysis.kind()).isEqualTo(EmailKind.RECEIPT);
        assertThat(analysis.merchant().key()).isEqualTo("adobe");
        assertThat(analysis.billing().amount()).isEqualByComparingTo("59.99");
        assertThat(analysis.billing().cycle()).isEqualTo(BillingFrequency.MONTHLY);
        assertThat(analysis.billing().paymentDate()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(analysis.billing().nextBillingDate()).isEqualTo(LocalDate.of(2026, 11, 12));
        assertThat(analysis.billing().confidence()).isEqualTo(Confidence.LOW);
    }

    @Test
    void analyze_shouldDetectCancellation_withAccessEndDate() {
        // when
        BillingEmailAnalysis analysis = underTest.analyze(EmailFixtures.load("spotify-cancellation.json"));

        // then
        assertThat(analysis.kind()).isEqualTo(EmailKind.CANCELLATION);
        assertThat(analysis.merchant().key()).isEqualTo("spotify");
        assertThat(analysis.accessEndsOn()).isEqualTo(LocalDate.of(2026, 12, 1));
    }

    @Test
    void analyze_shouldInferYearOfAccessEndDate_whenEmailOmitsIt() {
        // given
        GmailMessage message = EmailFixtures.authenticated(new GmailMessage("m-1", "t-1", Instant.parse("2026-11-02T09:00:00Z"),
                "Netflix <info@account.netflix.com>", "Your membership has been cancelled", null,
                "We're sorry to see you go. Your plan ends on Nov 20 and you won't be charged again."));

        // when
        BillingEmailAnalysis analysis = underTest.analyze(message);

        // then
        assertThat(analysis.kind()).isEqualTo(EmailKind.CANCELLATION);
        assertThat(analysis.accessEndsOn()).isEqualTo(LocalDate.of(2026, 11, 20));
    }

    @Test
    void analyze_shouldRollAccessEndDateIntoNextYear_whenMonthAlreadyPassed() {
        // given
        GmailMessage message = EmailFixtures.authenticated(new GmailMessage("m-1", "t-1", Instant.parse("2026-12-28T09:00:00Z"),
                "Netflix <info@account.netflix.com>", "Your membership has been cancelled", null,
                "Your membership has been cancelled. You can keep watching until Jan 15."));

        // when
        BillingEmailAnalysis analysis = underTest.analyze(message);

        // then
        assertThat(analysis.accessEndsOn()).isEqualTo(LocalDate.of(2027, 1, 15));
    }

    @Test
    void analyze_shouldLeaveAccessEndDateEmpty_whenCancellationTakesEffectNow() {
        // given
        GmailMessage message = EmailFixtures.authenticated(new GmailMessage("m-1", "t-1", Instant.parse("2026-11-02T09:00:00Z"),
                "Spotify <no-reply@spotify.com>", "Your subscription has been cancelled", null,
                "Your subscription has been cancelled effective immediately."));

        // when
        BillingEmailAnalysis analysis = underTest.analyze(message);

        // then
        assertThat(analysis.accessEndsOn()).isNull();
    }

    @Test
    void analyze_shouldDetectPaymentFailure() {
        // when
        BillingEmailAnalysis analysis = underTest.analyze(EmailFixtures.load("openai-payment-failed.json"));

        // then
        assertThat(analysis.kind()).isEqualTo(EmailKind.PAYMENT_FAILED);
        assertThat(analysis.merchant().key()).isEqualTo("openai");
    }

    @Test
    void analyze_shouldNameUnknownMerchantAfterSender_withLowConfidence() {
        // when
        BillingEmailAnalysis analysis = underTest.analyze(EmailFixtures.load("acme-receipt-unknown-sender.json"));

        // then
        assertThat(analysis.kind()).isEqualTo(EmailKind.RECEIPT);
        assertThat(analysis.merchant().key()).isEqualTo("acme-cloud");
        assertThat(analysis.merchant().displayName()).isEqualTo("Acme Cloud");
        assertThat(analysis.merchant().category()).isEqualTo(SubscriptionCategory.OTHER);
        assertThat(analysis.billing().amount()).isEqualByComparingTo("5.00");
        assertThat(analysis.billing().confidence()).isEqualTo(Confidence.LOW);
    }

    @Test
    void analyze_shouldIgnoreNewsletter_thatMentionsMoneyAndSubscription() {
        // when
        BillingEmailAnalysis analysis = underTest.analyze(EmailFixtures.load("shop-newsletter-save-money.json"));

        // then
        assertThat(analysis.kind()).isEqualTo(EmailKind.IRRELEVANT);
    }

    @Test
    void analyze_shouldIgnoreBankPromo_thatMentionsSubscriptionsAndPrices() {
        // when
        BillingEmailAnalysis analysis = underTest.analyze(EmailFixtures.load("bank-promo-cashback.json"));

        // then
        assertThat(analysis.kind()).isEqualTo(EmailKind.IRRELEVANT);
    }

    @Test
    void analyze_shouldIgnoreReceiptLikeEmailFromUnknownSender_whenItHasNoAmount() {
        // given
        GmailMessage message = EmailFixtures.authenticated(new GmailMessage("m-1", "t-1", Instant.parse("2026-10-30T09:00:00Z"),
                "Random App <hello@random.example>", "Your plan is waiting", null,
                "Come back and finish setting up your plan."));

        // when
        BillingEmailAnalysis analysis = underTest.analyze(message);

        // then
        assertThat(analysis.kind()).isEqualTo(EmailKind.IRRELEVANT);
    }

    @Test
    void analyze_shouldMarkReceiptForReview_whenKnownMerchantReceiptCannotBeParsed() {
        // given
        GmailMessage message = EmailFixtures.authenticated(new GmailMessage("m-1", "t-1", Instant.parse("2026-10-30T09:00:00Z"),
                "Netflix <info@account.netflix.com>", "Your Netflix membership receipt", null,
                "Thanks for being a member. See your account page for details."));

        // when
        BillingEmailAnalysis analysis = underTest.analyze(message);

        // then
        assertThat(analysis.kind()).isEqualTo(EmailKind.RECEIPT);
        assertThat(analysis.billing().amount()).isNull();
        assertThat(analysis.billing().cycle()).isEqualTo(BillingFrequency.MONTHLY);
        assertThat(analysis.billing().confidence()).isEqualTo(Confidence.LOW);
    }

    @Test
    void analyze_shouldIgnoreOneOffRideReceipt_fromUnknownSender() {
        // when
        BillingEmailAnalysis analysis = underTest.analyze(EmailFixtures.load("uber-trip-receipt.json"));

        // then
        assertThat(analysis.kind()).isEqualTo(EmailKind.IRRELEVANT);
    }

    @Test
    void analyze_shouldIgnoreOneOffOrderReceipt_fromRetailerWithoutBillingCycle() {
        // when
        BillingEmailAnalysis analysis = underTest.analyze(EmailFixtures.load("amazon-order-receipt.json"));

        // then
        assertThat(analysis.kind()).isEqualTo(EmailKind.IRRELEVANT);
    }

    @Test
    void analyze_shouldKeepReceiptAsReceipt_whenFooterMentionsUpdatingPaymentMethod() {
        // given
        GmailMessage message = EmailFixtures.authenticated(new GmailMessage("m-1", "t-1", Instant.parse("2026-10-30T09:00:00Z"),
                "Netflix <info@account.netflix.com>", "Your Netflix membership receipt", null,
                "Thanks for your payment. Amount charged: $22.99 per month. "
                        + "If this charge looks wrong or your card changed, update your payment method in Account settings. "
                        + "Having a billing issue? Visit the Help Center."));

        // when
        BillingEmailAnalysis analysis = underTest.analyze(message);

        // then
        assertThat(analysis.kind()).isEqualTo(EmailKind.RECEIPT);
    }

    @Test
    void analyze_shouldStillDetectPaymentFailure_whenOnlyBodySaysSo() {
        // given
        GmailMessage message = EmailFixtures.authenticated(new GmailMessage("m-1", "t-1", Instant.parse("2026-10-30T09:00:00Z"),
                "Spotify <no-reply@spotify.com>", "Action needed on your account", null,
                "We couldn't process your payment for Premium. Please update your payment method within 7 days."));

        // when
        BillingEmailAnalysis analysis = underTest.analyze(message);

        // then
        assertThat(analysis.kind()).isEqualTo(EmailKind.PAYMENT_FAILED);
    }

    @Test
    void analyze_shouldIgnoreGithubPullRequestNotification_evenWhenItQuotesPaymentsAndAmounts() {
        // when
        BillingEmailAnalysis analysis = underTest.analyze(EmailFixtures.load("github-pr-notification.json"));

        // then
        assertThat(analysis.kind()).isEqualTo(EmailKind.IRRELEVANT);
    }

    @Test
    void isCandidate_shouldRejectMailingListThread_fromKnownSender() {
        // given
        GmailMessage notification = EmailFixtures.load("github-pr-notification.json");

        // when
        boolean candidate = underTest.isCandidate(notification);

        // then
        assertThat(candidate).isFalse();
    }

    @Test
    void isCandidate_shouldRejectReply_toBillingThread() {
        // given
        GmailMessage reply = EmailFixtures.authenticated(new GmailMessage("m-1", "t-1", Instant.parse("2026-10-30T09:00:00Z"),
                "Netflix <info@account.netflix.com>", "RE: Your Netflix membership receipt", null, null));

        // when
        boolean candidate = underTest.isCandidate(reply);

        // then
        assertThat(candidate).isFalse();
    }

    @Test
    void analyze_shouldIgnoreMerchantLookalike_sentFromPersonalMailbox() {
        // when
        BillingEmailAnalysis analysis = underTest.analyze(EmailFixtures.load("netflix-due-soon-from-gmail.json"));

        // then
        assertThat(analysis.kind()).isEqualTo(EmailKind.IRRELEVANT);
    }

    @Test
    void isCandidate_shouldRejectPersonalMailbox_atHeaderStage() {
        // given
        GmailMessage headers = EmailFixtures.load("netflix-due-soon-from-gmail.json");

        // when
        boolean candidate = underTest.isCandidate(headers);

        // then
        assertThat(candidate).isFalse();
    }

    @Test
    void analyze_shouldIgnoreReceipt_whenSenderFailsAuthentication() {
        // given
        GmailMessage message = new GmailMessage("m-1", "t-1", Instant.parse("2026-10-30T09:00:00Z"),
                "Netflix <info@account.netflix.com>", "Your Netflix membership receipt", null,
                "Thanks for your payment. Amount charged: $22.99 per month.", List.of(),
                new MessageHeaders(null, null, "mx.google.com; dkim=fail header.i=@netflix.com; spf=fail smtp.mailfrom=info@account.netflix.com"));

        // when
        BillingEmailAnalysis analysis = underTest.analyze(message);

        // then
        assertThat(analysis.kind()).isEqualTo(EmailKind.IRRELEVANT);
    }

    @Test
    void analyze_shouldIgnoreFreePlanOnboarding_evenWhenItShowsZeroCharge() {
        // when
        BillingEmailAnalysis analysis = underTest.analyze(EmailFixtures.load("copilot-free-access.json"));

        // then
        assertThat(analysis.kind()).isEqualTo(EmailKind.IRRELEVANT);
    }

    @Test
    void analyze_shouldIgnoreNewsletter_withUnsubscribeLinkAndNoTransaction() {
        // when
        BillingEmailAnalysis analysis = underTest.analyze(EmailFixtures.load("notion-newsletter.json"));

        // then
        assertThat(analysis.kind()).isEqualTo(EmailKind.IRRELEVANT);
    }

    @Test
    void analyze_shouldIgnorePromotionalSubscriptionEmail_whenOnlyTheBodyAdvertises() {
        // given
        GmailMessage message = EmailFixtures.authenticated(new GmailMessage("m-1", "t-1", Instant.parse("2026-10-30T09:00:00Z"),
                "Spotify <no-reply@spotify.com>", "Your subscription, upgraded", null,
                "Upgrade to Premium Family and save 25%: $16.99 per month.", List.of(),
                new MessageHeaders(null, "<https://www.spotify.com/unsubscribe>", null)));

        // when
        BillingEmailAnalysis analysis = underTest.analyze(message);

        // then
        assertThat(analysis.kind()).isEqualTo(EmailKind.IRRELEVANT);
    }

    @Test
    void analyze_shouldKeepReceipt_whenItAlsoCarriesUnsubscribeLinkAndPromoFooter() {
        // given
        GmailMessage message = EmailFixtures.authenticated(new GmailMessage("m-1", "t-1", Instant.parse("2026-10-30T09:00:00Z"),
                "Spotify <no-reply@spotify.com>", "Your Spotify Premium receipt", null,
                "Thanks for your payment. Amount charged: $10.99 per month. Upgrade to Family and save.", List.of(),
                new MessageHeaders(null, "<https://www.spotify.com/unsubscribe>", null)));

        // when
        BillingEmailAnalysis analysis = underTest.analyze(message);

        // then
        assertThat(analysis.kind()).isEqualTo(EmailKind.RECEIPT);
        assertThat(analysis.billing().amount()).isEqualByComparingTo("10.99");
    }

    @Test
    void analyze_shouldIgnorePaymentDueReminder_fromRealMerchant() {
        // given
        GmailMessage message = EmailFixtures.authenticated(new GmailMessage("m-1", "t-1", Instant.parse("2026-10-30T09:00:00Z"),
                "Netflix <info@account.netflix.com>", "Upcoming renewal: your Netflix payment is due soon", null,
                "Your membership renews on November 5, 2026. We will charge $15.49 to your card on file."));

        // when
        BillingEmailAnalysis analysis = underTest.analyze(message);

        // then
        assertThat(analysis.kind()).isEqualTo(EmailKind.IRRELEVANT);
    }

    @Test
    void analyze_shouldKeepRenewalReceipt_thatAlsoNamesTheNextRenewalDate() {
        // given
        GmailMessage message = EmailFixtures.authenticated(new GmailMessage("m-1", "t-1", Instant.parse("2026-10-30T09:00:00Z"),
                "Netflix <info@account.netflix.com>", "Your Netflix membership receipt", null,
                "Thanks for your payment. Amount charged: $15.49 per month. Your membership renews on November 30, 2026."));

        // when
        BillingEmailAnalysis analysis = underTest.analyze(message);

        // then
        assertThat(analysis.kind()).isEqualTo(EmailKind.RECEIPT);
    }

    @Test
    void analyze_shouldNotTreatZeroAmountAsPrice() {
        // given
        GmailMessage message = EmailFixtures.authenticated(new GmailMessage("m-1", "t-1", Instant.parse("2026-10-30T09:00:00Z"),
                "Netflix <info@account.netflix.com>", "Your Netflix membership receipt", null,
                "Thanks for your payment. Amount charged: $0.00 this month thanks to your gift card."));

        // when
        BillingEmailAnalysis analysis = underTest.analyze(message);

        // then
        assertThat(analysis.isPayment()).isFalse();
    }
}
