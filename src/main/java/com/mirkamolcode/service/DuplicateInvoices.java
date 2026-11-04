package com.mirkamolcode.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

final class DuplicateInvoices {
    private static final long RESEND_WINDOW_DAYS = 3;

    private DuplicateInvoices() {
    }

    static boolean areSameInvoice(String reference, LocalDate billedOn, String otherReference, LocalDate otherBilledOn) {
        return reference != null && Objects.equals(reference, otherReference)
                && billedOn != null && otherBilledOn != null
                && Math.abs(ChronoUnit.DAYS.between(billedOn, otherBilledOn)) <= RESEND_WINDOW_DAYS;
    }
}
