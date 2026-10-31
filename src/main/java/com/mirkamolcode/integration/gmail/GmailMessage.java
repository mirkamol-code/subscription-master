package com.mirkamolcode.integration.gmail;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

import org.jsoup.Jsoup;

public record GmailMessage(
        String id,
        String threadId,
        Instant receivedAt,
        String from,
        String subject,
        String htmlBody,
        String textBody,
        List<GmailAttachment> attachments,
        MessageHeaders headers
) {
    public GmailMessage(String id, String threadId, Instant receivedAt, String from, String subject, String htmlBody, String textBody) {
        this(id, threadId, receivedAt, from, subject, htmlBody, textBody, List.of());
    }

    public GmailMessage(String id, String threadId, Instant receivedAt, String from, String subject, String htmlBody, String textBody,
                        List<GmailAttachment> attachments) {
        this(id, threadId, receivedAt, from, subject, htmlBody, textBody, attachments, MessageHeaders.none());
    }

    public GmailMessage {
        subject = subject == null ? "" : subject;
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
        headers = headers == null ? MessageHeaders.none() : headers;
    }

    public LocalDate receivedOnUtc() {
        return receivedAt == null ? null : LocalDate.ofInstant(receivedAt, ZoneOffset.UTC);
    }

    public String bodyText() {
        if (textBody != null && !textBody.isBlank())
            return textBody;
        return htmlBody == null ? "" : Jsoup.parse(htmlBody).text();
    }

    public String senderDomain() {
        String address = senderAddress();
        int at = address.lastIndexOf('@');
        return at < 0 ? "" : address.substring(at + 1).toLowerCase(Locale.ROOT);
    }

    public boolean isFromDomain(String domain) {
        String normalized = domain.toLowerCase(Locale.ROOT);
        String sender = senderDomain();
        return sender.equals(normalized) || sender.endsWith("." + normalized);
    }

    public String senderAddress() {
        if (from == null)
            return "";
        int open = from.lastIndexOf('<');
        int close = from.lastIndexOf('>');
        return open >= 0 && close > open ? from.substring(open + 1, close).trim() : from.trim();
    }

    public String senderName() {
        if (from == null)
            return "";
        int open = from.lastIndexOf('<');
        return open > 0 ? from.substring(0, open).trim().replaceAll("^\"|\"$", "") : "";
    }
}
