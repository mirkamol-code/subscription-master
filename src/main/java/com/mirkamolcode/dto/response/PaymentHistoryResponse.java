package com.mirkamolcode.dto.response;

import com.mirkamolcode.model.CurrencyCode;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PaymentHistoryResponse(
        Long id,
        LocalDate chargedAt,
        BigDecimal amount,
        CurrencyCode currency,
        BigDecimal amountUzs,
        BigDecimal exchangeRateToUzs
) {
}
