package com.mirkamolcode.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public enum AnalyticsRange {
    LAST_7_DAYS(7),
    LAST_30_DAYS(30),
    LAST_90_DAYS(90),
    ALL_TIME(0);

    private static final LocalDate FIRST_DAY = LocalDate.EPOCH;

    private final int days;

    AnalyticsRange(int days) {
        this.days = days;
    }

    public Instant calculateStart(Instant now) {
        return isAllTime() ? Instant.EPOCH : now.minus(days, ChronoUnit.DAYS);
    }

    public LocalDate calculateFirstDay(LocalDate today) {
        return isAllTime() ? FIRST_DAY : today.minusDays(days - 1L);
    }

    private boolean isAllTime() {
        return days == 0;
    }
}
