package com.mirkamolcode.model;

import java.time.LocalDate;

public enum BillingFrequency {
    WEEKLY {
        public LocalDate addTo(LocalDate date) {
            return date.plusWeeks(1);
        }
    },
    MONTHLY {
        public LocalDate addTo(LocalDate date) {
            return date.plusMonths(1);
        }
    },
    ANNUAL {
        public LocalDate addTo(LocalDate date) {
            return date.plusYears(1);
        }
    };

    public abstract LocalDate addTo(LocalDate date);

    public int annualOccurrences() {
        return switch (this) {
            case WEEKLY -> 52;
            case MONTHLY -> 12;
            case ANNUAL -> 1;
        };
    }

    public java.math.BigDecimal monthlyMultiplier() {
        return switch (this) {
            case WEEKLY ->
                    java.math.BigDecimal.valueOf(52).divide(java.math.BigDecimal.valueOf(12), 8, java.math.RoundingMode.HALF_UP);
            case MONTHLY -> java.math.BigDecimal.ONE;
            case ANNUAL ->
                    java.math.BigDecimal.ONE.divide(java.math.BigDecimal.valueOf(12), 8, java.math.RoundingMode.HALF_UP);
        };
    }
}
