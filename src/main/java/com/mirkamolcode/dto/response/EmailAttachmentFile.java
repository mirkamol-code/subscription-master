package com.mirkamolcode.dto.response;

public record EmailAttachmentFile(String filename, String mimeType, byte[] bytes) {
}
