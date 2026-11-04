package com.mirkamolcode.detection;

import com.mirkamolcode.model.BillingFrequency;

import java.time.LocalDate;

public record TrialDetails(LocalDate endsOn, Money priceAfterTrial, BillingFrequency cycle) {
}
