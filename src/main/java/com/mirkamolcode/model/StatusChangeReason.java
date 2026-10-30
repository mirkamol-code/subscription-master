package com.mirkamolcode.model;

public enum StatusChangeReason {
    EMAIL_RECEIPT(StatusChangeSource.EMAIL),
    EMAIL_CANCELLATION(StatusChangeSource.EMAIL),
    CANCELLATION_SCHEDULED(StatusChangeSource.EMAIL),
    EMAIL_PAYMENT_FAILED(StatusChangeSource.EMAIL),
    USER(StatusChangeSource.USER),
    OVERDUE(StatusChangeSource.SYSTEM),
    SCHEDULED_END(StatusChangeSource.SYSTEM);

    private final StatusChangeSource source;

    StatusChangeReason(StatusChangeSource source) {
        this.source = source;
    }

    public StatusChangeSource source() {
        return source;
    }
}
