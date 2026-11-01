package com.mirkamolcode.dto.response;

import java.math.BigDecimal;

public record AdminOverviewResponse(
        long signupsToday,
        long signupsLast7Days,
        long signupsLast30Days,
        long activeSubscribers,
        long loginsLast24Hours,
        long paymentsDueNext7Days,
        BigDecimal paymentsDueNext7DaysTotalUzs
) {
}
