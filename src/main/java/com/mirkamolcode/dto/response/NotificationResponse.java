package com.mirkamolcode.dto.response;

import com.mirkamolcode.model.NotificationSeverity;
import com.mirkamolcode.model.NotificationType;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        NotificationType type,
        NotificationSeverity severity,
        String title,
        String body,
        Long subscriptionId,
        String link,
        boolean read,
        Instant createdAt
) {
}
