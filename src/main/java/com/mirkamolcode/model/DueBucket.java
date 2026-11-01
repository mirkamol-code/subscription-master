package com.mirkamolcode.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public enum DueBucket {
    OVERDUE,
    PASSED,
    TODAY,
    NEXT_3_DAYS,
    NEXT_7_DAYS,
    LATER;

    public static final int SOON_DAYS = 3;
    public static final int WEEK_DAYS = 7;

    public static DueBucket classifyPayment(LocalDate dueDate, SubscriptionStatus status, LocalDate today) {
        return status == SubscriptionStatus.BILLING_REQUIRED ? OVERDUE : classifyByDate(dueDate, today);
    }

    public static DueBucket classifyAccessEnd(LocalDate endsOn, LocalDate today) {
        return classifyByDate(endsOn, today);
    }

    private static DueBucket classifyByDate(LocalDate date, LocalDate today) {
        long daysUntil = ChronoUnit.DAYS.between(today, date);
        if (daysUntil < 0)
            return PASSED;
        if (daysUntil == 0)
            return TODAY;
        if (daysUntil <= SOON_DAYS)
            return NEXT_3_DAYS;
        return daysUntil < WEEK_DAYS ? NEXT_7_DAYS : LATER;
    }
}
