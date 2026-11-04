package com.mirkamolcode.model;
public enum SubscriptionStatus {
    TRIAL("Free trial"),
    ACTIVE("Active"),
    CANCELLED("Cancelled"),
    BILLING_REQUIRED("Billing required");

    private final String label;

    SubscriptionStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
