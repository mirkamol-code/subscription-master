package com.mirkamolcode.dto;

import com.mirkamolcode.model.SubscriptionCategory;

import java.math.BigDecimal;

public record CategoryCost(
        SubscriptionCategory category,
        BigDecimal monthlyCostUzs
) {
}
