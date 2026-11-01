package com.mirkamolcode.dto.response;

import java.math.BigDecimal;

public record MerchantAnalyticsResponse(
        int rank,
        String slug,
        String name,
        long activeSubscribers,
        long activeSubscriptions,
        BigDecimal sharePercent,
        long newLast7Days,
        long newLast30Days,
        long cancelledInRange,
        BigDecimal trackedSpendUzs,
        BigDecimal averageSpendPerUserUzs,
        BigDecimal loginsPerUser
) {
}
