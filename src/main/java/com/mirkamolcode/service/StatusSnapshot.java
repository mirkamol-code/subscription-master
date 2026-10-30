package com.mirkamolcode.service;

import com.mirkamolcode.model.SubscriptionStatus;

import java.time.Instant;
import java.time.LocalDate;

public record StatusSnapshot(SubscriptionStatus status, Instant evidenceAt, LocalDate endsOn) {
}
