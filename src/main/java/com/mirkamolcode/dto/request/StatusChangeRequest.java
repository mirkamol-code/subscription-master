package com.mirkamolcode.dto.request;

import com.mirkamolcode.model.SubscriptionStatus;
import jakarta.validation.constraints.NotNull;

public record StatusChangeRequest(@NotNull(message = "Status is required") SubscriptionStatus status) {
}
