package com.mirkamolcode.dto;

import java.math.BigDecimal;
import java.util.List;

public record SpendingSummary(
        BigDecimal monthlyTotalUzs,
        String baseCurrency,
        SubscriptionCost mostExpensive,
        List<CategoryCost> byCategory
) {
}
