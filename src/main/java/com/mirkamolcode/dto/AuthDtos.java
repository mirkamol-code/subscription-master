package com.mirkamolcode.dto;

import jakarta.validation.constraints.*;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record ExchangeRequest(@NotBlank(message = "Login code is required") @Size(max = 128) String code) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record LogoutRequest(@NotBlank String refreshToken) {
    }

    public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresInSeconds) {
    }
}
