package com.mirkamolcode.detection;

import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GmailQueryTest {
    private final GmailQuery underTest = new GmailQuery(EmailFixtures.detectionProperties());

    @Test
    void buildLookbackQuery_shouldLookBackThirteenMonths_toSeeAnnualRenewals() {
        // when
        String query = underTest.buildLookbackQuery();

        // then
        assertThat(query).endsWith(" newer_than:13m");
    }

    @Test
    void buildLookbackQuery_shouldCoverReceiptWording() {
        // when
        String query = underTest.buildLookbackQuery();

        // then
        assertThat(query).contains("receipt", "invoice", "\"payment received\"", "\"your subscription\"", "renewal");
    }

    @Test
    void buildLookbackQuery_shouldIncludeMerchantAndProcessorSenders() {
        // when
        String query = underTest.buildLookbackQuery();

        // then
        assertThat(query).contains("anthropic.com", "stripe.com", "paddle.com", "payments-noreply@google.com",
                "googleplay-noreply@google.com");
        assertThat(query).doesNotContain(" OR  OR ", "( OR", "OR )");
    }

    @Test
    void buildReceivedAfterQuery_shouldUseEpochSeconds() {
        // when
        String query = underTest.buildReceivedAfterQuery(Instant.parse("2026-10-01T00:00:00Z"));

        // then
        assertThat(query).endsWith(" after:1790812800");
    }
}
