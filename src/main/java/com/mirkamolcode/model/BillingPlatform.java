package com.mirkamolcode.model;

public enum BillingPlatform {
    APPLE("Apple", "apple.com"),
    GOOGLE_PLAY("Google Play", "play.google.com"),
    PAYPAL("PayPal", "paypal.com");

    private final String displayName;
    private final String logoDomain;

    BillingPlatform(String displayName, String logoDomain) {
        this.displayName = displayName;
        this.logoDomain = logoDomain;
    }

    public String displayName() {
        return displayName;
    }

    public String logoDomain() {
        return logoDomain;
    }
}
