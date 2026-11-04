package com.mirkamolcode.model;
public enum SubscriptionCategory {
    ENTERTAINMENT("Entertainment"),
    PRODUCTIVITY("Productivity"),
    AI_TOOLS("AI Tools"),
    EDUCATION("Education"),
    HEALTH("Health & Fitness"),
    FINANCE("Finance"),
    INFRASTRUCTURE("Infrastructure"),
    OTHER("Other");

    private final String label;

    SubscriptionCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
