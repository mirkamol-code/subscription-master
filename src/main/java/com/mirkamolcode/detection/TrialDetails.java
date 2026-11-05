package com.mirkamolcode.detection;

import com.mirkamolcode.model.BillingFrequency;

import java.time.LocalDate;

public record TrialDetails(LocalDate endsOn, boolean endsOnEstimated, Money priceAfterTrial, BillingFrequency cycle,
                           boolean startConfirmed) {
}
