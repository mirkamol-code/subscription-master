package com.mirkamolcode.service;

final class PeriodValidation {

    private PeriodValidation() {
    }

    static void requireSupportedYear(int year) {
        if (year < 2000 || year > 2100)
            throw new IllegalArgumentException("year must be between 2000 and 2100");
    }

    static void requireValidMonth(int month) {
        if (month < 1 || month > 12)
            throw new IllegalArgumentException("month must be between 1 and 12");
    }
}
