package com.mirkamolcode.dto.request;

import jakarta.validation.constraints.NotNull;

public record AccountAssignmentRequest(
        @NotNull(message = "Subscription is required")
        Long subscriptionId
) {
}
