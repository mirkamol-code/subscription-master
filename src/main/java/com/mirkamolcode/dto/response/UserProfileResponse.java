package com.mirkamolcode.dto.response;

import java.time.Instant;
import java.util.List;

public record UserProfileResponse(
        Long id,
        String email,
        List<String> roles,
        String baseCurrency,
        Instant createdAt
) {
}
