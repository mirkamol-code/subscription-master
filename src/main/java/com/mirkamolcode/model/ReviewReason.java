package com.mirkamolcode.model;

public enum ReviewReason {
    AMOUNT_UNREADABLE("Receipt found, amount unreadable."),
    UNCONFIRMED_RECURRING("Two similar invoices found. Confirm this is a subscription or hide it."),
    UNCONFIRMED_RECURRING_REPEATED("Three or more similar invoices found. Confirm this is a subscription or hide it.");

    private final String note;

    ReviewReason(String note) {
        this.note = note;
    }

    public String note() {
        return note;
    }
}
