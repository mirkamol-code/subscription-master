package com.mirkamolcode.service;

import com.mirkamolcode.model.SubscriptionStatus;

import java.time.Instant;
import java.time.LocalDate;

public record StatusEvidence(Kind kind, Instant occurredAt, LocalDate accessEndsOn, SubscriptionStatus requestedStatus) {
    public enum Kind {
        RECEIPT,
        CANCELLATION,
        PAYMENT_FAILED,
        USER_MARK,
        OVERDUE,
        SCHEDULED_END
    }

    public static StatusEvidence receipt(Instant occurredAt) {
        return new StatusEvidence(Kind.RECEIPT, occurredAt, null, null);
    }

    public static StatusEvidence cancellation(Instant occurredAt, LocalDate accessEndsOn) {
        return new StatusEvidence(Kind.CANCELLATION, occurredAt, accessEndsOn, null);
    }

    public static StatusEvidence paymentFailed(Instant occurredAt) {
        return new StatusEvidence(Kind.PAYMENT_FAILED, occurredAt, null, null);
    }

    public static StatusEvidence userMark(Instant occurredAt, SubscriptionStatus requestedStatus) {
        return new StatusEvidence(Kind.USER_MARK, occurredAt, null, requestedStatus);
    }

    public static StatusEvidence overdue(Instant occurredAt) {
        return new StatusEvidence(Kind.OVERDUE, occurredAt, null, null);
    }

    public static StatusEvidence scheduledEnd(Instant occurredAt) {
        return new StatusEvidence(Kind.SCHEDULED_END, occurredAt, null, null);
    }
}
