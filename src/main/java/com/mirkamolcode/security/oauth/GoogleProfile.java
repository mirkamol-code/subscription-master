package com.mirkamolcode.security.oauth;

public record GoogleProfile(
        String subject,
        String email,
        boolean emailVerified,
        String displayName,
        String avatarUrl
) {
}
