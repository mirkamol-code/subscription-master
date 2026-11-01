package com.mirkamolcode.dto.response;

import com.mirkamolcode.model.Role;
import com.mirkamolcode.model.SubscriptionStatus;

import java.time.Instant;
import java.util.List;

public record AdminUserRowResponse(
        Long id,
        String email,
        String displayName,
        String avatarUrl,
        Role role,
        Instant createdAt,
        Instant lastLoginAt,
        long activeSubscriptions,
        List<String> merchantNames,
        SubscriptionStatus status
) {
}
