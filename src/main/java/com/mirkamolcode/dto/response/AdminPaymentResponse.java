package com.mirkamolcode.dto.response;

import com.mirkamolcode.model.CurrencyCode;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AdminPaymentResponse(
        Long id,
        Long subscriptionId,
        String subscriptionName,
        LocalDate paidOn,
        BigDecimal amount,
        CurrencyCode currency,
        BigDecimal amountUzs
) {
}
