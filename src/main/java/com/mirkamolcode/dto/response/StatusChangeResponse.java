package com.mirkamolcode.dto.response;

import com.mirkamolcode.model.StatusChangeReason;
import com.mirkamolcode.model.StatusChangeSource;
import com.mirkamolcode.model.SubscriptionStatus;

import java.time.Instant;
import java.time.LocalDate;

public record StatusChangeResponse(
        Long id,
        SubscriptionStatus fromStatus,
        SubscriptionStatus toStatus,
        StatusChangeReason reason,
        StatusChangeSource source,
        Instant effectiveAt,
        LocalDate endsOn,
        Long billingEmailId,
        String emailSender,
        Instant emailReceivedAt
) {
}
