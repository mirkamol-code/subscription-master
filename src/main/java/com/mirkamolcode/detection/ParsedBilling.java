package com.mirkamolcode.detection;

import com.mirkamolcode.model.BillingFrequency;
import com.mirkamolcode.model.CurrencyCode;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ParsedBilling(
        BigDecimal amount,
        CurrencyCode currency,
        BillingFrequency cycle,
        LocalDate paymentDate,
        LocalDate nextBillingDate,
        Confidence confidence
) {
}
