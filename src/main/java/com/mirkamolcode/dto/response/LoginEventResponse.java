package com.mirkamolcode.dto.response;

import java.time.Instant;

public record LoginEventResponse(
        Long id,
        Instant occurredAt,
        String ip,
        String userAgent
) {
}
