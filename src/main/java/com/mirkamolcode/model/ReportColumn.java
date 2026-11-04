package com.mirkamolcode.model;

public enum ReportColumn {
    SERVICE("Service"),
    CATEGORY("Category"),
    STATUS("Status"),
    BILLING("Billing"),
    PRICE("Price"),
    CURRENCY("Currency"),
    YEARLY_COST("Yearly cost"),
    YEARLY_COST_UZS("Yearly cost (UZS)"),
    STARTED("Started"),
    NEXT_PAYMENT("Next payment");

    private final String header;

    ReportColumn(String header) {
        this.header = header;
    }

    public String header() {
        return header;
    }
}
