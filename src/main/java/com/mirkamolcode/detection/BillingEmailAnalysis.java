package com.mirkamolcode.detection;

import java.time.LocalDate;

public record BillingEmailAnalysis(
        EmailKind kind,
        Merchant merchant,
        ParsedBilling billing,
        LocalDate accessEndsOn
) {
    public static BillingEmailAnalysis irrelevant() {
        return new BillingEmailAnalysis(EmailKind.IRRELEVANT, null, null, null);
    }

    public boolean isPayment() {
        return kind == EmailKind.RECEIPT && billing != null && billing.amount() != null && billing.currency() != null;
    }
}
