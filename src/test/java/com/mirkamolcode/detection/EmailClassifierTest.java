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

    private static GmailMessage messageWith(String subject, MessageHeaders headers) {
        return new GmailMessage("m-1", "t-1", Instant.parse("2026-10-30T09:00:00Z"), "Sender <billing@sender.example>", subject,
                null, null, List.of(), headers);
    }
}
