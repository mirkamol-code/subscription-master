package com.mirkamolcode.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SpendingSummary(
        BigDecimal monthlyTotalUzs,
        String baseCurrency,
        SubscriptionCost mostExpensive,
        List<CategoryCost> byCategory,
        Integer year,
        Integer month
) {
    public SpendingSummary(BigDecimal monthlyTotalUzs, String baseCurrency, SubscriptionCost mostExpensive, List<CategoryCost> byCategory) {
        this(monthlyTotalUzs, baseCurrency, mostExpensive, byCategory, null, null);
    }
}
