package com.mirkamolcode.integration.gmail;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class GmailRawMessage {
    private static final String CRLF = "\r\n";
    private static final int BODY_LINE_LENGTH = 76;
    private static final int ENCODED_WORD_BYTES = 39;

    private GmailRawMessage() {
    }

    public static String build(String recipient, String subject, String html) {
        if (recipient.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Recipient must not contain control characters");
        String mime = "To: " + recipient + CRLF
                + "Subject: " + encodeHeader(subject.replaceAll("\\p{Cntrl}", " ")) + CRLF
                + "MIME-Version: 1.0" + CRLF
                + "Content-Type: text/html; charset=UTF-8" + CRLF
                + "Content-Transfer-Encoding: base64" + CRLF
                + CRLF
                + wrap(Base64.getEncoder().encodeToString(html.getBytes(StandardCharsets.UTF_8)));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mime.getBytes(StandardCharsets.UTF_8));
    }

    private static String encodeHeader(String value) {
        StringBuilder words = new StringBuilder();
        StringBuilder chunk = new StringBuilder();
        value.codePoints().forEach(codePoint -> {
            String next = new String(Character.toChars(codePoint));
            if (utf8Length(chunk) + utf8Length(next) > ENCODED_WORD_BYTES) {
                appendEncodedWord(words, chunk.toString());
                chunk.setLength(0);
            }
            chunk.append(next);
        });
        appendEncodedWord(words, chunk.toString());
        return words.toString();
    }

    private static void appendEncodedWord(StringBuilder words, String text) {
        if (!words.isEmpty())
            words.append(CRLF).append(' ');
        words.append("=?UTF-8?B?").append(Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8))).append("?=");
    }

    private static int utf8Length(CharSequence text) {
        return text.toString().getBytes(StandardCharsets.UTF_8).length;
    }

    private static String wrap(String base64) {
        StringBuilder lines = new StringBuilder();
        for (int start = 0; start < base64.length(); start += BODY_LINE_LENGTH)
            lines.append(base64, start, Math.min(base64.length(), start + BODY_LINE_LENGTH)).append(CRLF);
        return lines.toString();
    }
}
