package com.mirkamolcode.detection;

import com.mirkamolcode.integration.gmail.GmailMessage;
import com.mirkamolcode.integration.gmail.MessageHeaders;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class EmailClassifierTest {
    private final EmailClassifier underTest = new EmailClassifier(EmailFixtures.detectionProperties());

    @ParameterizedTest
    @ValueSource(strings = {
            "Re: Your Netflix membership receipt",
            "RE: Your Netflix membership receipt",
            "Fwd: Your receipt from Anthropic",
            "FW: Your receipt from Anthropic",
            "Fw: Invoice 2026-10",
            "[owner/repo] Run failed: Deploy Workflow - main",
            "[mirkamol-code/subscription-master] Release v1.2.0"
    })
    void isConversation_shouldFlagRepliesForwardsAndRepositoryThreads(String subject) {
        // given
        GmailMessage message = messageWith(subject, MessageHeaders.none());

        // when
        boolean conversation = underTest.isConversation(message);

        // then
        assertThat(conversation).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Your receipt from Anthropic",
            "Receipt [12/05] for your Spotify Premium",
            "Your invoice is ready: Real estate fees"
    })
    void isConversation_shouldNotFlagTransactionalSubjects(String subject) {
        // given
        GmailMessage message = messageWith(subject, MessageHeaders.none());

        // when
        boolean conversation = underTest.isConversation(message);

        // then
        assertThat(conversation).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Your receipt from Anthropic"})
    void isConversation_shouldFlagMailingListMessages(String subject) {
        // given
        GmailMessage message = messageWith(subject, new MessageHeaders("owner/repo <repo.owner.github.com>", null, null));

        // when
        boolean conversation = underTest.isConversation(message);

        // then
        assertThat(conversation).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Your Claude Pro subscription was canceled",
            "Your ChatGPT Plus subscription cancelled",
            "Your plan won't renew",
            "Your Google One plan will not renew",
            "Your Gemini plan ended",
            "Your plan has ended"
    })
    void classify_shouldDetectCancellationWording(String subject) {
        // given
        GmailMessage message = messageWith(subject, MessageHeaders.none());

        // when
        EmailKind kind = underTest.classify(message);

        // then
        assertThat(kind).isEqualTo(EmailKind.CANCELLATION);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "If you cancel, your plan won't renew and you keep access until the end of the period.",
            "You can cancel anytime. Once cancelled, your subscription will not renew.",
            "Need help? Here's how to cancel: your plan won't renew after you turn off auto-renew.",
            "Want to cancel? Your membership has ended only when you confirm in settings."
    })
    void classify_shouldNotTreatConditionalCancellationWordingAsCancellation(String body) {
        // given
        GmailMessage message = messageWith("Your Spotify Premium update", body);

        // when
        EmailKind kind = underTest.classify(message);

        // then
        assertThat(kind).isNotEqualTo(EmailKind.CANCELLATION);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Your Premium subscription has been canceled. If you change your mind, you can cancel anytime before renewing again.",
            "We've canceled your plan. To cancel other plans, visit settings."
    })
    void classify_shouldKeepClearCancellation_evenWhenEmailAlsoExplainsHowToCancel(String body) {
        // given
        GmailMessage message = messageWith("Your subscription has been cancelled", body);

        // when
        EmailKind kind = underTest.classify(message);

        // then
        assertThat(kind).isEqualTo(EmailKind.CANCELLATION);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Thanks for starting your free trial", "Your Acme Family Tree free trial", "Welcome to your free trial"})
    void hasBillingSubject_shouldLetTrialEmailsThrough(String subject) {
        // when
        boolean billing = underTest.hasBillingSubject(messageWith(subject, MessageHeaders.none()));

        // then
        assertThat(billing).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Your Netflix price is changing", "Important: price increase for your plan", "A price update for your membership"})
    void classify_shouldDetectPriceChangeNotices(String subject) {
        // given
        GmailMessage message = messageWith(subject, "Your monthly price will change from $15.49 to $17.99.");

        // when
        EmailKind kind = underTest.classify(message);

        // then
        assertThat(kind).isEqualTo(EmailKind.PRICE_CHANGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Your receipt from Anthropic",
            "Payment received for your Google One plan",
            "Your invoice for October",
            "Your subscription renewal"
    })
    void classify_shouldTreatReceiptWordingAsReceipt(String subject) {
        // given
        GmailMessage message = messageWith(subject, MessageHeaders.none());

        // when
        EmailKind kind = underTest.classify(message);

        // then
        assertThat(kind).isEqualTo(EmailKind.RECEIPT);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Billing information for your workspace",
            "Your plan is waiting",
            "Subscription payment due soon: Netflix",
            "Upcoming payment for Spotify Premium"
    })
    void classify_shouldIgnoreSubjectsWithoutTransaction(String subject) {
        // given
        GmailMessage message = messageWith(subject, MessageHeaders.none());

        // when
        EmailKind kind = underTest.classify(message);

        // then
        assertThat(kind).isEqualTo(EmailKind.IRRELEVANT);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            Invoice 000000000002                    | The invoice amount of € 7.50 is due.
            Your invoice from Acme Hosting          | Thanks, here is your invoice.
            Your billing statement for October      | Amount: $12.00
            Payment receipt #8812                   | We received your payment of $12.00
            """)
    void isRecurringInvoiceCandidate_shouldAcceptInvoicesAndBillingReceipts(String subject, String body) {
        // when / then
        assertThat(underTest.isRecurringInvoiceCandidate(messageWith(subject, body))).isTrue();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            Invoice 000000000004                    | Purchase order 4411 is referenced on this invoice.
            Your invoice from Acme Hosting          | Order ID: MT0000TEST. In order to avoid late fees, pay by the due date.
            """)
    void isRecurringInvoiceCandidate_shouldAcceptInvoices_thatOnlyMentionOrderNumbers(String subject, String body) {
        // when / then
        assertThat(underTest.isRecurringInvoiceCandidate(messageWith(subject, body))).isTrue();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            Your Tuesday evening trip with Uber           | Total $12.40. Thanks for riding.
            Your Amazon.com order receipt #112-1234567    | Your order has shipped.
            Your receipt from City Museum #5512-0091      | Ticket x2, total $30.00
            Order confirmation #4471                      | Invoice attached. Your items will be delivered soon.
            Refund for invoice 2026-10                    | Your refund of $9.00 has been issued.
            Your food order receipt                       | Invoice total $18.20 for your delivery.
            Invoice from Shop                             | Sold by Seller Ltd via Marketplace. Total $40.00
            """)
    void isRecurringInvoiceCandidate_shouldRejectOrdersRidesRefundsAndMarketplaceReceipts(String subject, String body) {
        // when / then
        assertThat(underTest.isRecurringInvoiceCandidate(messageWith(subject, body))).isFalse();
    }

    private static GmailMessage messageWith(String subject, String body) {
        return new GmailMessage("m-1", "t-1", Instant.parse("2026-10-30T09:00:00Z"), "Sender <billing@sender.example>", subject,
                null, body);
    }

    private static GmailMessage messageWith(String subject, MessageHeaders headers) {
        return new GmailMessage("m-1", "t-1", Instant.parse("2026-10-30T09:00:00Z"), "Sender <billing@sender.example>", subject,
                null, null, List.of(), headers);
    }
}
