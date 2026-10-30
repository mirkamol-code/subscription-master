package com.mirkamolcode.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PushUnsubscribeRequest(@NotBlank String endpoint) {
}
