package com.mirkamolcode.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record NotificationSettingsRequest(
        @NotNull(message = "emailEnabled is required") Boolean emailEnabled,
        @NotNull(message = "inAppEnabled is required") Boolean inAppEnabled,
        @NotNull(message = "pushEnabled is required") Boolean pushEnabled,
        @NotNull @Min(value = 1, message = "daysBefore must be at least 1") @Max(value = 30, message = "daysBefore must be at most 30") Integer daysBefore,
        @NotBlank(message = "timeZone is required") String timeZone
) {
}
