package com.mirkamolcode.dto.response;

import java.time.Instant;
import java.util.List;

public record UserProfileResponse(
        Long id,
        String email,
        String displayName,
        String avatarUrl,
        List<String> roles,
        String baseCurrency,
        Instant createdAt
) {
}
