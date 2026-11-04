package com.mirkamolcode.detection;

import com.mirkamolcode.model.BillingFrequency;

import java.time.LocalDate;

public record LineItem(
        String title,
        String planName,
        BillingFrequency cycle,
        LocalDate renewsOn,
        Money price,
        boolean recurring
) {
}
