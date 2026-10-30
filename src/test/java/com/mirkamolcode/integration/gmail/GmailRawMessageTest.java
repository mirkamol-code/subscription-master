package com.mirkamolcode.integration.gmail;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GmailRawMessageTest {

    @Test
    void build_shouldProduceBase64UrlMimeMessage_addressedToTheUser() {
        // when
        String raw = GmailRawMessage.build("alex.doe@gmail.com", "Netflix renews on 30 Nov", "<p>22.99 USD</p>");

        // then
        String mime = decode(raw);
        assertThat(raw).doesNotContain("+").doesNotContain("/").doesNotContain("=");
        assertThat(mime).contains("To: alex.doe@gmail.com\r\n")
                .contains("MIME-Version: 1.0\r\n")
                .contains("Content-Type: text/html; charset=UTF-8\r\n")
                .contains("Content-Transfer-Encoding: base64\r\n")
                .contains("Subject: =?UTF-8?B?" + Base64.getEncoder().encodeToString("Netflix renews on 30 Nov".getBytes(StandardCharsets.UTF_8)) + "?=");
        String body = mime.substring(mime.indexOf("\r\n\r\n") + 4).replace("\r\n", "");
        assertThat(new String(Base64.getDecoder().decode(body), StandardCharsets.UTF_8)).isEqualTo("<p>22.99 USD</p>");
    }

    @Test
    void build_shouldEncodeNonLatinSubject() {
        // when
        String mime = decode(GmailRawMessage.build("alex.doe@gmail.com", "Obuna to'lovi: Счёт", "<p>x</p>"));

        // then
        assertThat(mime).contains("Subject: =?UTF-8?B?" + Base64.getEncoder().encodeToString("Obuna to'lovi: Счёт".getBytes(StandardCharsets.UTF_8)) + "?=");
    }

    @Test
    void build_shouldFoldLongSubjectIntoShortEncodedWords_thatDecodeBackToTheSubject() {
        // given
        String subject = "Adobe Creative Cloud All Apps 100 GB (Teams) renews on 13 Nov 2026 — Счёт №12345";

        // when
        String mime = decode(GmailRawMessage.build("alex.doe@gmail.com", subject, "<p>x</p>"));

        // then
        String headers = mime.substring(0, mime.indexOf("\r\n\r\n"));
        assertThat(headers.split("\r\n")).allSatisfy(line -> assertThat(line.length()).isLessThanOrEqualTo(76));
        String subjectHeader = headers.substring(headers.indexOf("Subject: "), headers.indexOf("\r\nMIME-Version"));
        StringBuilder decoded = new StringBuilder();
        java.util.regex.Matcher word = java.util.regex.Pattern.compile("=\\?UTF-8\\?B\\?([^?]+)\\?=").matcher(subjectHeader);
        while (word.find())
            decoded.append(new String(Base64.getDecoder().decode(word.group(1)), StandardCharsets.UTF_8));
        assertThat(decoded.toString()).isEqualTo(subject);
    }

    @Test
    void build_shouldNotLetSubjectInjectHeaders() {
        // when
        String mime = decode(GmailRawMessage.build("alex.doe@gmail.com", "Netflix\r\nBcc: attacker@example.com", "<p>x</p>"));

        // then
        String headers = mime.substring(0, mime.indexOf("\r\n\r\n"));
        assertThat(headers).doesNotContain("Bcc:").doesNotContain("attacker@example.com");
    }

    @Test
    void build_shouldRejectRecipientWithLineBreaks() {
        // when / then
        assertThatThrownBy(() -> GmailRawMessage.build("alex@gmail.com\r\nBcc: x@example.com", "s", "b"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static String decode(String raw) {
        return new String(Base64.getUrlDecoder().decode(raw), StandardCharsets.UTF_8);
    }
}
