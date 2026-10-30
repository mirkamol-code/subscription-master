package com.mirkamolcode.service;

import com.mirkamolcode.model.StatusChangeReason;
import com.mirkamolcode.model.SubscriptionStatus;

import java.time.Instant;
import java.time.LocalDate;

public record StatusDecision(SubscriptionStatus status, LocalDate endsOn, Instant evidenceAt, StatusChangeReason reason,
                             boolean isStatusChange) {
}
