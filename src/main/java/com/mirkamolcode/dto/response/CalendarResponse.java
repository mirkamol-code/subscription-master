package com.mirkamolcode.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** TASK-04: Grouped calendar view with pre-converted costs in UZS */
public record CalendarResponse(
        int year,
        int month,
        BigDecimal totalMonthCostUzs,
        String currency,
        List<CalendarDayGroup> days
) {
    public record CalendarDayGroup(
            LocalDate date,
            BigDecimal dayTotalUzs,
            List<SubscriptionResponse> subscriptions
    ) {
    }
}
