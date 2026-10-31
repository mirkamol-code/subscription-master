package com.mirkamolcode.model;

public enum ReviewReason {
    AMOUNT_UNREADABLE("Receipt found, amount unreadable.");

    private final String note;

    ReviewReason(String note) {
        this.note = note;
    }

    public String note() {
        return note;
    }
}
