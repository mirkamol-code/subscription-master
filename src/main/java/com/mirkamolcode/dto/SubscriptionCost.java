package com.mirkamolcode.dto;

import java.math.BigDecimal;

public record SubscriptionCost(
        Long subscriptionId,
        String subscriptionName,
        BigDecimal monthlyCostUzs
) {
}
