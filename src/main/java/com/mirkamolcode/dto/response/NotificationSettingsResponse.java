package com.mirkamolcode.dto.response;

public record NotificationSettingsResponse(
        boolean emailEnabled,
        boolean inAppEnabled,
        boolean pushEnabled,
        int daysBefore,
        String timeZone
) {
}
