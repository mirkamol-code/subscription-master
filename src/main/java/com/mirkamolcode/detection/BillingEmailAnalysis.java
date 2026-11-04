package com.mirkamolcode.detection;

import java.time.LocalDate;
import java.util.List;

public record BillingEmailAnalysis(
        EmailKind kind,
        List<DetectedCharge> charges,
        LocalDate accessEndsOn,
        boolean awaitingRepeat,
        PriceChangeNotice priceChange
) {
    public BillingEmailAnalysis {
        charges = List.copyOf(charges);
    }

    public BillingEmailAnalysis(EmailKind kind, List<DetectedCharge> charges, LocalDate accessEndsOn, boolean awaitingRepeat) {
        this(kind, charges, accessEndsOn, awaitingRepeat, null);
    }

    public static BillingEmailAnalysis irrelevant() {
        return new BillingEmailAnalysis(EmailKind.IRRELEVANT, List.of(), null, false);
    }

    static BillingEmailAnalysis of(EmailKind kind, Merchant merchant, ParsedBilling billing, LocalDate accessEndsOn) {
        return new BillingEmailAnalysis(kind, List.of(DetectedCharge.direct(merchant, billing)), accessEndsOn, false);
    }

    static BillingEmailAnalysis priceChange(Merchant merchant, PriceChangeNotice notice) {
        return new BillingEmailAnalysis(EmailKind.PRICE_CHANGE, List.of(DetectedCharge.direct(merchant, null)), null, false, notice);
    }

    public Merchant merchant() {
        return charges.isEmpty() ? null : charges.getFirst().merchant();
    }

    public ParsedBilling billing() {
        return charges.isEmpty() ? null : charges.getFirst().billing();
    }

    public boolean isPayment() {
        return kind == EmailKind.RECEIPT && !charges.isEmpty() && charges.getFirst().isPayment();
    }
}
