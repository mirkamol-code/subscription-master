package com.mirkamolcode.detection;

import com.mirkamolcode.model.PaymentMethodType;

public record PaymentMethod(PaymentMethodType type, String cardBrand, String cardLast4) {
    public PaymentMethod {
        if (cardLast4 != null && !cardLast4.matches("\\d{4}"))
            throw new IllegalArgumentException("Card last 4 must be exactly four digits");
    }

    static PaymentMethod card(String brand, String lastFour) {
        return new PaymentMethod(PaymentMethodType.CARD, brand, lastFour);
    }

    static PaymentMethod paypal() {
        return new PaymentMethod(PaymentMethodType.PAYPAL, null, null);
    }
}
