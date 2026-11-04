package com.mirkamolcode.detection;

import com.mirkamolcode.model.BillingPlatform;

public record DetectedCharge(
        Merchant merchant,
        String planName,
        BillingPlatform platform,
        PaymentMethod paymentMethod,
        String invoiceReference,
        ParsedBilling billing
) {
    static DetectedCharge direct(Merchant merchant, ParsedBilling billing) {
        return new DetectedCharge(merchant, null, null, null, null, billing);
    }

    public boolean isPayment() {
        return billing != null && billing.amount() != null && billing.currency() != null;
    }
}
