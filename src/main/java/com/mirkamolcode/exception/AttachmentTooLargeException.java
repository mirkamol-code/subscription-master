package com.mirkamolcode.exception;
public class AttachmentTooLargeException extends RuntimeException {
    public AttachmentTooLargeException(long sizeBytes, long maxBytes) {
        super(sizeBytes < 0
                ? "Attachment is larger than the limit of " + maxBytes + " bytes"
                : "Attachment is " + sizeBytes + " bytes; the limit is " + maxBytes + " bytes");
    }
}
