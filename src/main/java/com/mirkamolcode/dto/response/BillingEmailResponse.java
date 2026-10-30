package com.mirkamolcode.dto.response;

import com.mirkamolcode.detection.Confidence;
import com.mirkamolcode.detection.EmailKind;

import java.time.Instant;
import java.util.List;

public record BillingEmailResponse(
        Long id,
        String gmailMessageId,
        EmailKind kind,
        String sender,
        String subject,
        Instant receivedAt,
        Confidence confidence,
        List<Attachment> attachments
) {
    public record Attachment(Long id, String filename, String mimeType, long sizeBytes) {
    }
}
