package com.mirkamolcode.dto.response;

import com.mirkamolcode.model.Role;

import java.time.Instant;

public record AdminUserResponse(
        Long id,
        String email,
        String displayName,
        String avatarUrl,
        Role role,
        String timeZone,
        Instant createdAt,
        Instant lastLoginAt
) {
}
