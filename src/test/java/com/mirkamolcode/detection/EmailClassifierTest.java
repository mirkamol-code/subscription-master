package com.mirkamolcode.detection;

import com.mirkamolcode.integration.gmail.GmailMessage;
import com.mirkamolcode.integration.gmail.MessageHeaders;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
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

    private static GmailMessage messageWith(String subject, MessageHeaders headers) {
        return new GmailMessage("m-1", "t-1", Instant.parse("2026-10-30T09:00:00Z"), "Sender <billing@sender.example>", subject,
                null, null, List.of(), headers);
    }
}
