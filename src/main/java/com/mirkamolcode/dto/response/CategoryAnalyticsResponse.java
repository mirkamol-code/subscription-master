package com.mirkamolcode.dto.response;

import com.mirkamolcode.model.SubscriptionCategory;

import java.math.BigDecimal;

public record CategoryAnalyticsResponse(
        SubscriptionCategory category,
        long activeSubscribers,
        long activeSubscriptions,
        BigDecimal sharePercent,
        BigDecimal trackedSpendUzs
) {
}
