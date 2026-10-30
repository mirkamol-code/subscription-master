package com.mirkamolcode.detection;

public record BillingEmailAnalysis(
        EmailKind kind,
        Merchant merchant,
        ParsedBilling billing
) {
    public static BillingEmailAnalysis irrelevant() {
        return new BillingEmailAnalysis(EmailKind.IRRELEVANT, null, null);
    }

    public boolean isPayment() {
        return kind == EmailKind.RECEIPT && billing != null && billing.amount() != null && billing.currency() != null;
    }
}
