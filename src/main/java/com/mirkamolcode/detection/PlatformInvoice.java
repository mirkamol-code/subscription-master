package com.mirkamolcode.detection;

import java.time.LocalDate;
import java.util.List;

public record PlatformInvoice(
        LocalDate invoiceDate,
        String invoiceReference,
        List<LineItem> items,
        Money total,
        PaymentMethod paymentMethod
) {
    public PlatformInvoice {
        items = List.copyOf(items);
    }
}
