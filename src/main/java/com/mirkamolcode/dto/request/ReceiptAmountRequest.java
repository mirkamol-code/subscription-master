package com.mirkamolcode.dto.request;

import com.mirkamolcode.model.CurrencyCode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record ReceiptAmountRequest(
        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be greater than zero")
        BigDecimal amount,
        @NotNull(message = "Currency is required")
        CurrencyCode currency
) {
}
