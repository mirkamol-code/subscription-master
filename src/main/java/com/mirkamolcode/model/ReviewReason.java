package com.mirkamolcode.model;

public enum ReviewReason {
    AMOUNT_UNREADABLE("Receipt found, amount unreadable."),
    SIMILAR_INVOICES("Two similar invoices found. Confirm this is a subscription or hide it."),
    SIMILAR_INVOICES_REPEATED("Three or more similar invoices found. Confirm this is a subscription or hide it."),
    UNCONFIRMED_TRIAL("Trial from a sender we don't know. Confirm it or hide it.");

    private final String note;

    ReviewReason(String note) {
        this.note = note;
    }

    public String note() {
        return note;
    }
}
