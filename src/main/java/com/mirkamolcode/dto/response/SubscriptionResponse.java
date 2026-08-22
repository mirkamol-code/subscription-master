package com.mirkamolcode.dto.response;

import com.mirkamolcode.model.BillingFrequency;
import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.model.SubscriptionCategory;
import com.mirkamolcode.model.SubscriptionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SubscriptionResponse(
        Long id,
        String name,
        BigDecimal price,
        CurrencyCode currency,
        BillingFrequency frequency,
        SubscriptionStatus status,
        SubscriptionCategory category,
        LocalDate startDate,
        LocalDate nextPaymentDate,
        long version
){
}
