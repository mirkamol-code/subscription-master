package com.mirkamolcode.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PushSubscriptionRequest(
        @NotBlank @Size(max = 2048) @Pattern(regexp = "^https://.+", message = "endpoint must be an https URL") String endpoint,
        @NotNull @Valid Keys keys
) {
    public record Keys(@NotBlank @Size(max = 255) String p256dh, @NotBlank @Size(max = 255) String auth) {
    }
}
