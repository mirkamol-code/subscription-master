package com.mirkamolcode.integration.gmail;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GmailMessageMapperTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void toMessage_shouldMapListAndAuthenticationHeaders() throws Exception {
        // given
        String message = """
                {"id": "m-1", "threadId": "t-1", "internalDate": "1792929600000", "payload": {"headers": [
                  {"name": "From", "value": "GitHub <notifications@github.com>"},
                  {"name": "Subject", "value": "[owner/repo] Run failed"},
                  {"name": "Authentication-Results", "value": "mx.google.com; dkim=pass header.i=@github.com"},
                  {"name": "List-Id", "value": "owner/repo <repo.owner.github.com>"},
                  {"name": "List-Unsubscribe", "value": "<https://github.com/notifications/unsubscribe/abc>"}
                ]}}
                """;

        // when
        GmailMessage mapped = GmailMessageMapper.toMessage(JSON.readTree(message));

        // then
        assertThat(mapped.headers()).isEqualTo(new MessageHeaders(
                "owner/repo <repo.owner.github.com>",
                "<https://github.com/notifications/unsubscribe/abc>",
                "mx.google.com; dkim=pass header.i=@github.com"));
    }

    @Test
    void toMessage_shouldKeepGmailsTopmostAuthenticationResults_whenSenderAddsAnother() throws Exception {
        // given
        String message = """
                {"id": "m-1", "threadId": "t-1", "internalDate": "1792929600000", "payload": {"headers": [
                  {"name": "Authentication-Results", "value": "mx.google.com; dkim=fail header.i=@netflix.com; spf=fail smtp.mailfrom=x@spoofer.example"},
                  {"name": "From", "value": "Netflix <billing@netflix.com>"},
                  {"name": "Authentication-Results", "value": "mx.google.com; dkim=pass header.i=@netflix.com"}
                ]}}
                """;

        // when
        GmailMessage mapped = GmailMessageMapper.toMessage(JSON.readTree(message));

        // then
        assertThat(mapped.headers().authenticationResults()).startsWith("mx.google.com; dkim=fail");
    }

    @Test
    void toMessage_shouldLeaveHeadersEmpty_whenMessageHasNone() throws Exception {
        // given
        String message = """
                {"id": "m-1", "threadId": "t-1", "internalDate": "1792929600000", "payload": {"headers": [
                  {"name": "From", "value": "Netflix <billing@netflix.com>"}
                ]}}
                """;

        // when
        GmailMessage mapped = GmailMessageMapper.toMessage(JSON.readTree(message));

        // then
        assertThat(mapped.headers()).isEqualTo(MessageHeaders.none());
    }
}
