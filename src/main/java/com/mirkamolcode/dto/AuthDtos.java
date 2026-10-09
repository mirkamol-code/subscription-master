package com.mirkamolcode.dto;

import com.mirkamolcode.dto.response.UserProfileResponse;
import jakarta.validation.constraints.*;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record RegisterRequest(@NotBlank @Email @Size(max = 254) String email,
                                  @NotBlank @Size(min = 12, max = 72) String password) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record LogoutRequest(@NotBlank String refreshToken) {
    }

    public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresInSeconds) {
    }
}
