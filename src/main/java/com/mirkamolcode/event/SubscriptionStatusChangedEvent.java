package com.mirkamolcode.event;

import com.mirkamolcode.model.StatusChangeReason;
import com.mirkamolcode.model.SubscriptionStatus;

import java.time.Instant;
import java.time.LocalDate;

public record SubscriptionStatusChangedEvent(
        Long userId,
        Long subscriptionId,
        String subscriptionName,
        SubscriptionStatus fromStatus,
        SubscriptionStatus toStatus,
        StatusChangeReason reason,
        Instant effectiveAt,
        LocalDate endsOn
) {
}
