package com.mirkamolcode.integration.gmail;

public record GmailAttachment(
        String attachmentId,
        String filename,
        String mimeType,
        long sizeBytes
) {
}
