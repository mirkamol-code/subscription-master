package com.mirkamolcode.notification;

import com.mirkamolcode.model.NotificationSeverity;
import com.mirkamolcode.model.NotificationType;

public record NotificationMessage(
        NotificationType type,
        NotificationSeverity severity,
        Long subscriptionId,
        String title,
        String body,
        String link
) {
}
