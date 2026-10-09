package com.mirkamolcode.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record ExchangeRatesResponse(
        String baseCurrency,
        Map<String, BigDecimal> rates,
        Instant updatedAt
) {
}
