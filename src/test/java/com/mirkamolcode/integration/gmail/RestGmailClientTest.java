package com.mirkamolcode.integration.gmail;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.mirkamolcode.exception.AttachmentTooLargeException;
import com.mirkamolcode.exception.GoogleApiUnavailableException;
import io.github.resilience4j.retry.RetryRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestGmailClientTest {
    private static final String TOKEN = "ya29.access";
    private static final String MESSAGES = "/gmail/v1/users/me/messages";

    private WireMockServer gmail;
    private RestGmailClient underTest;

    @BeforeEach
    void setUp() {
        gmail = new WireMockServer(wireMockConfig().dynamicPort());
        gmail.start();
        underTest = new RestGmailClient(RestClient.builder(), RetryRegistry.ofDefaults(),
                gmail.baseUrl(), Duration.ofSeconds(2), Duration.ofSeconds(3), Duration.ofMillis(5), 25L * 1024 * 1024);
    }

    @AfterEach
    void tearDown() {
        gmail.stop();
    }

    @Test
    void listMessageIds_shouldSendQueryAndToken_andReturnIdsWithNextPage() {
        // given
        gmail.stubFor(get(urlPathEqualTo(MESSAGES)).willReturn(okJson(recorded("google/gmail-messages-list-page1.json"))));

        // when
        GmailMessagePage page = underTest.listMessageIds(TOKEN, "subject:receipt newer_than:1y", null);

        // then
        assertThat(page.messageIds()).containsExactly("18c2a1f0netflix01", "18c2a1f0adobe0001");
        assertThat(page.nextPageToken()).isEqualTo("09876543210987654321");
        gmail.verify(getRequestedFor(urlPathEqualTo(MESSAGES))
                .withHeader("Authorization", equalTo("Bearer " + TOKEN))
                .withQueryParam("q", equalTo("subject:receipt newer_than:1y"))
                .withQueryParam("maxResults", equalTo("500"))
                .withoutQueryParam("pageToken"));
    }

    @Test
    void listMessageIds_shouldPassPageToken_andReturnLastPageWithoutToken() {
        // given
        gmail.stubFor(get(urlPathEqualTo(MESSAGES)).withQueryParam("pageToken", equalTo("09876543210987654321"))
                .willReturn(okJson(recorded("google/gmail-messages-list-page2.json"))));

        // when
        GmailMessagePage page = underTest.listMessageIds(TOKEN, "subject:receipt", "09876543210987654321");

        // then
        assertThat(page.messageIds()).containsExactly("18c2a1f0spotify01");
        assertThat(page.nextPageToken()).isNull();
    }

    @Test
    void listMessageIds_shouldReturnEmptyPage_whenMailboxHasNoMatches() {
        // given
        gmail.stubFor(get(urlPathEqualTo(MESSAGES)).willReturn(okJson(recorded("google/gmail-messages-list-empty.json"))));

        // when
        GmailMessagePage page = underTest.listMessageIds(TOKEN, "subject:receipt", null);

        // then
        assertThat(page.messageIds()).isEmpty();
        assertThat(page.nextPageToken()).isNull();
    }

    @Test
    void getMessage_shouldFetchFullFormat_andMapHeadersBodiesAndAttachmentMetadata() {
        // given
        gmail.stubFor(get(urlPathEqualTo(MESSAGES + "/18c2a1f0adobe0001")).withQueryParam("format", equalTo("full"))
                .willReturn(okJson(recorded("emails/adobe-invoice-with-pdf.json"))));

        // when
        GmailMessage message = underTest.getMessage(TOKEN, "18c2a1f0adobe0001");

        // then
        assertThat(message.id()).isEqualTo("18c2a1f0adobe0001");
        assertThat(message.from()).isEqualTo("Adobe <message@adobe.com>");
        assertThat(message.subject()).isEqualTo("Your Adobe invoice is ready");
        assertThat(message.receivedAt()).isEqualTo(Instant.parse("2026-10-12T14:30:00Z"));
        assertThat(message.bodyText()).contains("Total US$59.99");
        assertThat(message.attachments()).containsExactly(new GmailAttachment(
                "ANGjdJ8_recorded_attachment_id_adobe", "Invoice_1234567890.pdf", "application/pdf", 48213));
    }

    @Test
    void getMessageHeaders_shouldFetchOnlyFromAndSubject_withoutBody() {
        // given
        gmail.stubFor(get(urlPathEqualTo(MESSAGES + "/18c2a1f0netflix01"))
                .withQueryParam("format", equalTo("metadata"))
                .withQueryParam("metadataHeaders", havingExactly("From", "Subject"))
                .willReturn(okJson(recorded("google/gmail-message-metadata.json"))));

        // when
        GmailMessage headers = underTest.getMessageHeaders(TOKEN, "18c2a1f0netflix01");

        // then
        assertThat(headers.from()).isEqualTo("Netflix <info@account.netflix.com>");
        assertThat(headers.subject()).isEqualTo("Your Netflix membership receipt");
        assertThat(headers.receivedAt()).isEqualTo(Instant.parse("2026-10-30T09:00:00Z"));
        assertThat(headers.bodyText()).isEmpty();
    }

    @Test
    void getAttachment_shouldDecodeBase64UrlData_intoOriginalBytes() throws Exception {
        // given
        gmail.stubFor(get(urlPathEqualTo(MESSAGES + "/18c2a1f0adobe0001/attachments/ANGjdJ8_recorded"))
                .willReturn(okJson(recorded("google/gmail-attachment.json"))));

        // when
        byte[] content = underTest.getAttachment(TOKEN, "18c2a1f0adobe0001", "ANGjdJ8_recorded");

        // then
        byte[] expected;
        try (InputStream in = RestGmailClientTest.class.getResourceAsStream("/google/invoice-sample.pdf")) {
            expected = in.readAllBytes();
        }
        assertThat(content).isEqualTo(expected);
        gmail.verify(getRequestedFor(urlPathEqualTo(MESSAGES + "/18c2a1f0adobe0001/attachments/ANGjdJ8_recorded"))
                .withHeader("Authorization", equalTo("Bearer " + TOKEN)));
    }

    @Test
    void getAttachment_shouldThrowNotFound_whenMessageOrAttachmentIsGone() {
        // given
        gmail.stubFor(get(urlPathEqualTo(MESSAGES + "/gone/attachments/a1")).willReturn(aResponse().withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody(recorded("google/gmail-history-expired-404.json"))));

        // when / then
        assertThatThrownBy(() -> underTest.getAttachment(TOKEN, "gone", "a1")).isInstanceOf(GmailNotFoundException.class);
    }

    @Test
    void getAttachment_shouldRefuseToDecode_whenGmailReportsSizeAboveLimit() {
        // given
        gmail.stubFor(get(urlPathEqualTo(MESSAGES + "/m1/attachments/big"))
                .willReturn(okJson("{\"size\": 30000000, \"data\": \"AAAA\"}")));

        // when / then
        assertThatThrownBy(() -> underTest.getAttachment(TOKEN, "m1", "big")).isInstanceOf(AttachmentTooLargeException.class);
    }

    @Test
    void getAttachment_shouldRefuseToDecode_whenGmailOmitsSizeButDataIsAboveLimit() {
        // given
        String oversizedData = "A".repeat(36_000_000);
        gmail.stubFor(get(urlPathEqualTo(MESSAGES + "/m1/attachments/no-size"))
                .willReturn(okJson("{\"data\": \"" + oversizedData + "\"}")));

        // when / then
        assertThatThrownBy(() -> underTest.getAttachment(TOKEN, "m1", "no-size")).isInstanceOf(AttachmentTooLargeException.class);
    }

    @Test
    void getAttachment_shouldRetry_whenGmailRateLimits() {
        // given
        gmail.stubFor(get(urlPathEqualTo(MESSAGES + "/m1/attachments/a1")).inScenario("att").whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(429).withHeader("Content-Type", "application/json")
                        .withBody(recorded("google/gmail-rate-limited-429.json")))
                .willSetStateTo("ok"));
        gmail.stubFor(get(urlPathEqualTo(MESSAGES + "/m1/attachments/a1")).inScenario("att").whenScenarioStateIs("ok")
                .willReturn(okJson(recorded("google/gmail-attachment.json"))));

        // when / then
        assertThat(underTest.getAttachment(TOKEN, "m1", "a1")).hasSize(395);
    }

    @Test
    void sendMessage_shouldPostRawMessage_fromTheUsersOwnMailbox() {
        // given
        gmail.stubFor(post(urlPathEqualTo(MESSAGES + "/send"))
                .willReturn(okJson("{\"id\": \"18c2b0sent\", \"threadId\": \"18c2b0sent\", \"labelIds\": [\"SENT\"]}")));

        // when
        underTest.sendMessage(TOKEN, "cmF3LW1pbWU");

        // then
        gmail.verify(postRequestedFor(urlPathEqualTo(MESSAGES + "/send"))
                .withHeader("Authorization", equalTo("Bearer " + TOKEN))
                .withRequestBody(equalToJson("{\"raw\": \"cmF3LW1pbWU\"}")));
    }

    @Test
    void sendMessage_shouldReportMissingScope_whenSendScopeWasNotGranted() {
        // given
        gmail.stubFor(post(urlPathEqualTo(MESSAGES + "/send")).willReturn(aResponse().withStatus(403)
                .withHeader("Content-Type", "application/json")
                .withBody(recorded("google/gmail-insufficient-scope-403.json"))));

        // when / then
        assertThatThrownBy(() -> underTest.sendMessage(TOKEN, "cmF3")).isInstanceOf(GmailScopeMissingException.class);
    }

    @Test
    void sendMessage_shouldNotRetry_becauseSendingIsNotIdempotent() {
        // given
        gmail.stubFor(post(urlPathEqualTo(MESSAGES + "/send")).willReturn(aResponse().withStatus(503)));

        // when / then
        assertThatThrownBy(() -> underTest.sendMessage(TOKEN, "cmF3")).isInstanceOf(GoogleApiUnavailableException.class);
        gmail.verify(1, postRequestedFor(urlPathEqualTo(MESSAGES + "/send")));
    }

    @Test
    void listHistory_shouldReturnOnlyAddedMessages_andLatestHistoryId() {
        // given
        gmail.stubFor(get(urlPathEqualTo("/gmail/v1/users/me/history"))
                .withQueryParam("startHistoryId", equalTo("184460"))
                .withQueryParam("historyTypes", equalTo("messageAdded"))
                .willReturn(okJson(recorded("google/gmail-history.json"))));

        // when
        GmailHistoryPage page = underTest.listHistory(TOKEN, "184460", null);

        // then
        assertThat(page.addedMessageIds()).containsExactly("18c2a1f0openai001");
        assertThat(page.historyId()).isEqualTo("184475");
        assertThat(page.nextPageToken()).isNull();
    }

    @Test
    void listHistory_shouldThrowHistoryExpired_whenGmailNoLongerKnowsStartHistoryId() {
        // given
        gmail.stubFor(get(urlPathEqualTo("/gmail/v1/users/me/history")).willReturn(aResponse().withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody(recorded("google/gmail-history-expired-404.json"))));

        // when / then
        assertThatThrownBy(() -> underTest.listHistory(TOKEN, "1", null)).isInstanceOf(GmailHistoryExpiredException.class);
        gmail.verify(1, getRequestedFor(urlPathEqualTo("/gmail/v1/users/me/history")));
    }

    @Test
    void fetchCurrentHistoryId_shouldReadMailboxProfile() {
        // given
        gmail.stubFor(get(urlPathEqualTo("/gmail/v1/users/me/profile")).willReturn(okJson(recorded("google/gmail-profile.json"))));

        // when / then
        assertThat(underTest.fetchCurrentHistoryId(TOKEN)).isEqualTo("184475");
    }

    @Test
    void getMessage_shouldRetryWithBackoff_whenGmailRateLimits() {
        // given
        gmail.stubFor(get(urlPathEqualTo(MESSAGES + "/m1")).inScenario("rate").whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(429).withHeader("Content-Type", "application/json")
                        .withBody(recorded("google/gmail-rate-limited-429.json")))
                .willSetStateTo("user-limit"));
        gmail.stubFor(get(urlPathEqualTo(MESSAGES + "/m1")).inScenario("rate").whenScenarioStateIs("user-limit")
                .willReturn(aResponse().withStatus(403).withHeader("Content-Type", "application/json")
                        .withBody(recorded("google/gmail-user-rate-limit-403.json")))
                .willSetStateTo("ok"));
        gmail.stubFor(get(urlPathEqualTo(MESSAGES + "/m1")).inScenario("rate").whenScenarioStateIs("ok")
                .willReturn(okJson(recorded("emails/netflix-receipt.json"))));

        // when
        GmailMessage message = underTest.getMessage(TOKEN, "m1");

        // then
        assertThat(message.subject()).isEqualTo("Your Netflix membership receipt");
        gmail.verify(3, getRequestedFor(urlPathEqualTo(MESSAGES + "/m1")));
    }

    @Test
    void getMessage_shouldGiveUp_afterRepeatedServerErrors() {
        // given
        gmail.stubFor(get(urlPathEqualTo(MESSAGES + "/m1")).willReturn(aResponse().withStatus(503)));

        // when / then
        assertThatThrownBy(() -> underTest.getMessage(TOKEN, "m1")).isInstanceOf(GoogleApiUnavailableException.class);
        gmail.verify(4, getRequestedFor(urlPathEqualTo(MESSAGES + "/m1")));
    }

    @Test
    void getMessage_shouldThrowUnauthorized_withoutRetrying_whenAccessTokenIsRejected() {
        // given
        gmail.stubFor(get(urlPathEqualTo(MESSAGES + "/m1")).willReturn(aResponse().withStatus(401)
                .withHeader("Content-Type", "application/json")
                .withBody(recorded("google/gmail-unauthenticated-401.json"))));

        // when / then
        assertThatThrownBy(() -> underTest.getMessage(TOKEN, "m1")).isInstanceOf(GmailUnauthorizedException.class);
        gmail.verify(1, getRequestedFor(urlPathEqualTo(MESSAGES + "/m1")));
    }

    @Test
    void getMessage_shouldReportMissingScope_whenGmailScopeWasNotGranted() {
        // given
        gmail.stubFor(get(urlPathEqualTo(MESSAGES + "/m1")).willReturn(aResponse().withStatus(403)
                .withHeader("Content-Type", "application/json")
                .withBody(recorded("google/gmail-insufficient-scope-403.json"))));

        // when / then
        assertThatThrownBy(() -> underTest.getMessage(TOKEN, "m1")).isInstanceOf(GmailScopeMissingException.class);
        gmail.verify(1, getRequestedFor(urlPathEqualTo(MESSAGES + "/m1")));
    }

    private static String recorded(String fixture) {
        try (InputStream in = RestGmailClientTest.class.getResourceAsStream("/" + fixture)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
