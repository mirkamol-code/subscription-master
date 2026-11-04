package com.mirkamolcode.detection;

import com.mirkamolcode.integration.gmail.GmailMessage;
import com.mirkamolcode.integration.gmail.MessageHeaders;
import com.mirkamolcode.model.BillingFrequency;
import com.mirkamolcode.model.BillingPlatform;
import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.model.PaymentMethodType;
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
            List.of(new HeuristicParser(), new NetflixParser(), new SchemaOrgParser()),
            List.of(new AppleInvoiceParser()));

    @Test
    void analyze_shouldReadTrialEndPriceAndCycle_fromTrialStartedEmail() {
        // when
        BillingEmailAnalysis analysis = underTest.analyze(netflixTrial("Your free trial has started",
                "Welcome! Your free trial ends on December 4, 2026. After your trial, you'll be charged $15.49 per month."));

        // then
        assertThat(analysis.kind()).isEqualTo(EmailKind.TRIAL_STARTED);
        assertThat(analysis.merchant().key()).isEqualTo("netflix");
        assertThat(analysis.trial().endsOn()).isEqualTo(LocalDate.of(2026, 12, 4));
        assertThat(analysis.trial().priceAfterTrial().amount()).isEqualByComparingTo("15.49");
        assertThat(analysis.trial().priceAfterTrial().currency()).isEqualTo(CurrencyCode.USD);
        assertThat(analysis.trial().cycle()).isEqualTo(BillingFrequency.MONTHLY);
    }

    @Test
    void analyze_shouldLeaveTrialPriceAndCycleEmpty_whenEmailDoesNotStateThem() {
        // when
        BillingEmailAnalysis analysis = underTest.analyze(netflixTrial("Your free trial has started",
                "Welcome! Your free trial ends on December 4, 2026."));

        // then
        assertThat(analysis.kind()).isEqualTo(EmailKind.TRIAL_STARTED);
        assertThat(analysis.trial().endsOn()).isEqualTo(LocalDate.of(2026, 12, 4));
        assertThat(analysis.trial().priceAfterTrial()).isNull();
        assertThat(analysis.trial().cycle()).isNull();
    }

    @Test
    void analyze_shouldIgnoreZeroChargeOfTrial_andKeepPriceAfterTrial() {
        // when
        BillingEmailAnalysis analysis = underTest.analyze(netflixTrial("Your free trial has started",
                "Today's charge: $0.00. Your trial ends on December 4, 2026, then $15.49 per month."));

        // then
        assertThat(analysis.trial().priceAfterTrial().amount()).isEqualByComparingTo("15.49");
    }

    @Test
    void analyze_shouldNotGuessTrialPrice_whenEmailShowsSeveralPrices() {
        // when
        BillingEmailAnalysis analysis = underTest.analyze(netflixTrial("Your free trial has started",
                "Your trial ends on December 4, 2026. Plans: Basic $7.99 per month, Premium $22.99 per month."));

        // then
        assertThat(analysis.trial().priceAfterTrial()).isNull();
    }

    @Test
    void analyze_shouldIgnoreFreeTrialMarketing() {
        // when
        BillingEmailAnalysis analysis = underTest.analyze(netflixTrial("Start your free trial today",
                "Try Netflix free for 30 days. Join now."));

        // then
        assertThat(analysis.kind()).isNotEqualTo(EmailKind.TRIAL_STARTED);
        assertThat(analysis.isPayment()).isFalse();
    }

    @Test
    void analyze_shouldIgnoreTrialFromUnknownSender() {
        // when
        BillingEmailAnalysis analysis = underTest.analyze(EmailFixtures.authenticated(new GmailMessage("m-1", "t-1",
                Instant.parse("2026-11-04T09:00:00Z"), "Acme <hello@acme.example>", "Your free trial has started", null,
                "Your free trial ends on December 4, 2026. Then $5.00 per month.")));

        // then
        assertThat(analysis.kind()).isEqualTo(EmailKind.IRRELEVANT);
    }

    @Test
    void analyze_shouldReadOldPriceNewPriceAndEffectiveDate_fromPriceChangeNotice() {
        // when
        BillingEmailAnalysis analysis = underTest.analyze(netflixNotice(
                "Your monthly price will change from $15.49 to $17.99 starting December 15, 2026."));

        // then
        assertThat(analysis.kind()).isEqualTo(EmailKind.PRICE_CHANGE);
        assertThat(analysis.merchant().key()).isEqualTo("netflix");
        assertThat(analysis.priceChange().oldAmount()).isEqualByComparingTo("15.49");
        assertThat(analysis.priceChange().newAmount()).isEqualByComparingTo("17.99");
        assertThat(analysis.priceChange().currency()).isEqualTo(CurrencyCode.USD);
        assertThat(analysis.priceChange().effectiveOn()).isEqualTo(LocalDate.of(2026, 12, 15));
    }

    @Test
    void analyze_shouldLeaveMissingPriceChangeDetailsEmpty_insteadOfGuessing() {
        // when
        BillingEmailAnalysis analysis = underTest.analyze(netflixNotice("We're updating our prices. See your account for details."));

        // then
        assertThat(analysis.kind()).isEqualTo(EmailKind.PRICE_CHANGE);
        assertThat(analysis.priceChange().oldAmount()).isNull();
        assertThat(analysis.priceChange().newAmount()).isNull();
        assertThat(analysis.priceChange().currency()).isNull();
        assertThat(analysis.priceChange().effectiveOn()).isNull();
    }

    @Test
    void analyze_shouldNotTreatCurrencySwitchAsPriceChange() {
        // when
        BillingEmailAnalysis analysis = underTest.analyze(netflixNotice(
                "Your monthly price will change from $15.49 to €16.99 starting December 15, 2026."));

        // then
        assertThat(analysis.kind()).isEqualTo(EmailKind.PRICE_CHANGE);
        assertThat(analysis.priceChange()).isNull();
    }

    @Test
    void analyze_shouldIgnorePriceChangeNotice_fromUnknownSender() {
        // when
        BillingEmailAnalysis analysis = underTest.analyze(EmailFixtures.authenticated(new GmailMessage("m-1", "t-1",
                Instant.parse("2026-11-10T09:00:00Z"), "Acme Hosting <billing@acmehost.example>", "Your price is changing", null,
                "Your monthly price will change from $5.00 to $6.00 starting December 1, 2026.")));

        // then
        assertThat(analysis.kind()).isEqualTo(EmailKind.IRRELEVANT);
    }

    @Test
    void analyze_shouldDetectHetznerInvoice_whenEmailNeverSaysSubscription() {
        // when
        BillingEmailAnalysis analysis = underTest.analyze(EmailFixtures.load("hetzner-invoice.json"));

        // then
        assertThat(analysis.kind()).isEqualTo(EmailKind.RECEIPT);
        assertThat(analysis.merchant().key()).isEqualTo("hetzner");
        assertThat(analysis.merchant().displayName()).isEqualTo("Hetzner");
        assertThat(analysis.billing().amount()).isEqualByComparingTo("10.09");
        assertThat(analysis.billing().currency()).isEqualTo(CurrencyCode.USD);
        assertThat(analysis.billing().cycle()).isEqualTo(BillingFrequency.MONTHLY);
        assertThat(analysis.billing().paymentDate()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(analysis.merchant().category()).isEqualTo(SubscriptionCategory.INFRASTRUCTURE);
        assertThat(analysis.charges().getFirst().paymentMethod())
                .isEqualTo(new PaymentMethod(PaymentMethodType.CARD, null, null));
        assertThat(analysis.charges().getFirst().platform()).isNull();
        assertThat(analysis.charges().getFirst().references())
                .isEqualTo(new InvoiceReferences("000000000001", "K0000000001"));
    }

    @Test
    void analyze_shouldOnlyHoldUnknownSenderInvoiceForRepeat_evenWhenItMentionsBillingPeriodAndAutomaticDebit() {
        // given
        GmailMessage message = EmailFixtures.authenticated(new GmailMessage("m-1", "t-1", Instant.parse("2026-10-12T05:08:00Z"),
                "Acme Hosting <billing@acmehost.example>", "Invoice 000000000002", null,
                "The invoice amount of € 7.50 will be debited from your account. The billing period is the previous month."));

        // when
        BillingEmailAnalysis analysis = underTest.analyze(message);

        // then
        assertThat(analysis.kind()).isEqualTo(EmailKind.RECEIPT);
        assertThat(analysis.awaitingRepeat()).isTrue();
        assertThat(analysis.billing().amount()).isEqualByComparingTo("7.50");
        assertThat(analysis.billing().currency()).isEqualTo(CurrencyCode.EUR);
    }

    @Test
    void analyze_shouldKeepAppleInvoiceUnderPlatformKey_whenLayoutCannotBeParsed() {
        // given
        GmailMessage message = EmailFixtures.authenticated(new GmailMessage("m-1", "t-1", Instant.parse("2026-12-07T13:20:00Z"),
                "Apple <no_reply@email.apple.com>", "Your invoice from Apple.", null,
                "Rechnung\nSome Game Plus\nAbo verlängert sich am 7. Januar 2027\nTotal US$3,99"));

        // when
        BillingEmailAnalysis analysis = underTest.analyze(message);

        // then
        assertThat(analysis.merchant().key()).isEqualTo("apple-unparsed");
        assertThat(analysis.merchant().key()).isNotEqualTo("apple");
        assertThat(analysis.charges().getFirst().platform()).isEqualTo(BillingPlatform.APPLE);
        assertThat(analysis.billing().confidence()).isEqualTo(Confidence.LOW);
    }

    @Test
    void analyze_shouldHoldUnknownSenderInvoiceForRepeat_whenItHasNoRecurringWording() {
        // given
        GmailMessage message = EmailFixtures.authenticated(new GmailMessage("m-1", "t-1", Instant.parse("2026-10-12T05:08:00Z"),
                "Acme Hosting <billing@acmehost.example>", "Invoice 000000000003 (K0000000009)", null,
                "Please find your invoice attached. Amount: € 7.50"));

        // when
        BillingEmailAnalysis analysis = underTest.analyze(message);

        // then
        assertThat(analysis.kind()).isEqualTo(EmailKind.RECEIPT);
        assertThat(analysis.awaitingRepeat()).isTrue();
        assertThat(analysis.merchant().known()).isFalse();
        assertThat(analysis.billing().amount()).isEqualByComparingTo("7.50");
        assertThat(analysis.charges().getFirst().references()).isEqualTo(new InvoiceReferences("000000000003", "K0000000009"));
    }

    @Test
    void analyze_shouldNotHoldOneOffReceiptsForRepeat() {
        // when / then
        assertThat(underTest.analyze(EmailFixtures.load("uber-trip-receipt.json")).kind()).isEqualTo(EmailKind.IRRELEVANT);
        assertThat(underTest.analyze(EmailFixtures.load("stripe-one-off-receipt.json")).kind()).isEqualTo(EmailKind.IRRELEVANT);
    }

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
    void analyze_shouldEndAccessOnTheEmailDate_whenCancellationTakesEffectImmediately() {
        // given
        GmailMessage message = EmailFixtures.authenticated(new GmailMessage("m-1", "t-1", Instant.parse("2026-11-02T09:00:00Z"),
                "Spotify <no-reply@spotify.com>", "Your subscription has been cancelled", null,
                "Your subscription has been cancelled effective immediately."));

        // when
        BillingEmailAnalysis analysis = underTest.analyze(message);

        // then
        assertThat(analysis.accessEndsOn()).isEqualTo(LocalDate.of(2026, 11, 2));
    }

    @Test
    void analyze_shouldLeaveAccessEndDateEmpty_whenCancellationNamesNoDate() {
        // given
        GmailMessage message = EmailFixtures.authenticated(new GmailMessage("m-1", "t-1", Instant.parse("2026-11-02T09:00:00Z"),
                "Spotify <no-reply@spotify.com>", "Your subscription has been cancelled", null,
                "Your subscription has been cancelled. We're sorry to see you go."));

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

    @Test
    void analyze_shouldResolveMerchantFromStripeReceiptBody() {
        // when
        BillingEmailAnalysis analysis = underTest.analyze(EmailFixtures.load("stripe-claude-receipt.json"));

        // then
        assertThat(analysis.isPayment()).isTrue();
        assertThat(analysis.merchant().key()).isEqualTo("anthropic");
        assertThat(analysis.merchant().displayName()).isEqualTo("Claude");
        assertThat(analysis.merchant().category()).isEqualTo(SubscriptionCategory.AI_TOOLS);
        assertThat(analysis.billing().amount()).isEqualByComparingTo("20.00");
        assertThat(analysis.billing().currency()).isEqualTo(CurrencyCode.USD);
    }

    @Test
    void analyze_shouldResolveGoogleOnePlan_fromGooglePlayReceipt() {
        // when
        BillingEmailAnalysis analysis = underTest.analyze(EmailFixtures.load("google-play-google-one-receipt.json"));

        // then
        assertThat(analysis.isPayment()).isTrue();
        assertThat(analysis.merchant().key()).isEqualTo("google-one");
        assertThat(analysis.merchant().logoDomain()).isEqualTo("one.google.com");
        assertThat(analysis.billing().amount()).isEqualByComparingTo("19.99");
        assertThat(analysis.billing().cycle()).isEqualTo(BillingFrequency.MONTHLY);
    }

    @Test
    void analyze_shouldResolveAppStoreSubscription_toTheAppsMerchant() {
        // when
        BillingEmailAnalysis analysis = underTest.analyze(EmailFixtures.load("apple-claude-receipt.json"));

        // then
        assertThat(analysis.isPayment()).isTrue();
        assertThat(analysis.merchant().key()).isEqualTo("anthropic");
    }

    @Test
    void analyze_shouldResolveAppleOwnedService_whenAppleReceiptNamesNoOtherMerchant() {
        // given
        GmailMessage message = EmailFixtures.authenticated(new GmailMessage("m-1", "t-1", Instant.parse("2026-11-02T09:00:00Z"),
                "Apple <no_reply@email.apple.com>", "Your receipt from Apple.", null,
                "iCloud+ with 200 GB (Monthly)\nRenews 2 Dec 2026\n$2.99\nTotal $2.99"));

        // when
        BillingEmailAnalysis analysis = underTest.analyze(message);

        // then
        assertThat(analysis.merchant().key()).isEqualTo("icloud");
        assertThat(analysis.merchant().displayName()).isEqualTo("iCloud+");
    }

    @Test
    void analyze_shouldNameUnknownMerchantFromProcessorReceipt() {
        // when
        BillingEmailAnalysis analysis = underTest.analyze(EmailFixtures.load("stripe-unknown-product-receipt.json"));

        // then
        assertThat(analysis.isPayment()).isTrue();
        assertThat(analysis.merchant().key()).isEqualTo("cursor");
        assertThat(analysis.merchant().displayName()).isEqualTo("Cursor");
        assertThat(analysis.merchant().known()).isFalse();
    }

    @Test
    void analyze_shouldIgnoreOneOffProcessorReceipt_withoutRecurringSignal() {
        // when
        BillingEmailAnalysis analysis = underTest.analyze(EmailFixtures.load("stripe-one-off-receipt.json"));

        // then
        assertThat(analysis.kind()).isEqualTo(EmailKind.IRRELEVANT);
    }

    @Test
    void analyze_shouldIgnoreKnownMerchantName_inSubjectFromUnrelatedDomain() {
        // given
        GmailMessage message = EmailFixtures.authenticated(new GmailMessage("m-1", "t-1", Instant.parse("2026-11-02T09:00:00Z"),
                "Billing Desk <billing@streaming-payments.example>", "Your Netflix receipt", null,
                "Thanks for your payment. Amount charged: $15.49 per month."));

        // when
        BillingEmailAnalysis analysis = underTest.analyze(message);

        // then
        assertThat(analysis.kind()).isEqualTo(EmailKind.IRRELEVANT);
    }

    @Test
    void analyze_shouldDetectChatGptCancellation_withAccessEnd() {
        // when
        BillingEmailAnalysis analysis = underTest.analyze(EmailFixtures.load("chatgpt-cancellation.json"));

        // then
        assertThat(analysis.kind()).isEqualTo(EmailKind.CANCELLATION);
        assertThat(analysis.merchant().key()).isEqualTo("openai");
        assertThat(analysis.accessEndsOn()).isEqualTo(LocalDate.of(2026, 8, 21));
    }

    @Test
    void analyze_shouldAcceptUnknownMerchantReceipt_withSubscriptionWordingButNoCycle() {
        // given
        GmailMessage message = EmailFixtures.authenticated(new GmailMessage("m-1", "t-1", Instant.parse("2026-11-02T09:00:00Z"),
                "Linear <billing@linear.app>", "Your receipt from Linear", null,
                "Thanks for your payment. Linear Standard subscription. Total: $8.00"));

        // when
        BillingEmailAnalysis analysis = underTest.analyze(message);

        // then
        assertThat(analysis.isPayment()).isTrue();
        assertThat(analysis.billing().confidence()).isEqualTo(Confidence.LOW);
    }

    @Test
    void isCandidate_shouldAcceptProcessorSender_withPlainSubject() {
        // given
        GmailMessage headers = EmailFixtures.authenticated(new GmailMessage("m-1", "t-1", Instant.parse("2026-11-02T09:00:00Z"),
                "Google Payments <payments-noreply@google.com>", "Google One: payment details", null, null));

        // when
        boolean candidate = underTest.isCandidate(headers);

        // then
        assertThat(candidate).isTrue();
    }

    private static GmailMessage netflixTrial(String subject, String body) {
        return EmailFixtures.authenticated(new GmailMessage("m-1", "t-1", Instant.parse("2026-11-04T09:00:00Z"),
                "Netflix <info@account.netflix.com>", subject, null, body));
    }

    private static GmailMessage netflixNotice(String body) {
        return EmailFixtures.authenticated(new GmailMessage("m-1", "t-1", Instant.parse("2026-11-10T09:00:00Z"),
                "Netflix <info@account.netflix.com>", "Your Netflix price is changing", null, body));
    }
}
