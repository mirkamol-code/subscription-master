package com.mirkamolcode.dto;

import com.mirkamolcode.model.SubscriptionCategory;

import java.math.BigDecimal;

public record CategoryCost(
        SubscriptionCategory category,
        BigDecimal monthlyCostUzs,
        String currency
) {
    public CategoryCost(SubscriptionCategory category, BigDecimal monthlyCostUzs) {
        this(category, monthlyCostUzs, "UZS");
    }

    public static CategoryCost ofUzs(SubscriptionCategory category, BigDecimal monthlyCostUzs) {
        return new CategoryCost(category, monthlyCostUzs, "UZS");
    }
}
