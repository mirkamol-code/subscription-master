package com.mirkamolcode.service;

import com.mirkamolcode.model.StatusChangeReason;
import com.mirkamolcode.model.SubscriptionStatus;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Component;

import static com.mirkamolcode.model.SubscriptionStatus.ACTIVE;
import static com.mirkamolcode.model.SubscriptionStatus.BILLING_REQUIRED;
import static com.mirkamolcode.model.SubscriptionStatus.CANCELLED;

@Component
public class SubscriptionStatusPolicy {

    public Optional<StatusDecision> evaluate(StatusSnapshot current, StatusEvidence evidence, LocalDate today) {
        if (isOlderThanLatestEvidence(current, evidence))
            return Optional.empty();
        return switch (evidence.kind()) {
            case RECEIPT -> decide(current, evidence, ACTIVE, null, StatusChangeReason.EMAIL_RECEIPT);
            case PAYMENT_FAILED -> decide(current, evidence, BILLING_REQUIRED, null, StatusChangeReason.EMAIL_PAYMENT_FAILED);
            case CANCELLATION -> decideCancellation(current, evidence, today);
            case USER_MARK -> evidence.requestedStatus() == current.status() && current.endsOn() == null
                    ? Optional.empty()
                    : decide(current, evidence, evidence.requestedStatus(), null, StatusChangeReason.USER);
            case OVERDUE -> current.status() == ACTIVE && current.endsOn() == null
                    ? decide(current, evidence, BILLING_REQUIRED, null, StatusChangeReason.OVERDUE)
                    : Optional.empty();
            case SCHEDULED_END -> current.status() == ACTIVE && current.endsOn() != null && !today.isBefore(current.endsOn())
                    ? decide(current, evidence, CANCELLED, null, StatusChangeReason.SCHEDULED_END)
                    : Optional.empty();
        };
    }

    private static Optional<StatusDecision> decideCancellation(StatusSnapshot current, StatusEvidence evidence, LocalDate today) {
        LocalDate accessEndsOn = evidence.accessEndsOn();
        if (accessEndsOn != null && today.isBefore(accessEndsOn))
            return decide(current, evidence, ACTIVE, accessEndsOn, StatusChangeReason.CANCELLATION_SCHEDULED);
        return decide(current, evidence, CANCELLED, null, StatusChangeReason.EMAIL_CANCELLATION);
    }

    private static Optional<StatusDecision> decide(StatusSnapshot current, StatusEvidence evidence, SubscriptionStatus status,
                                                   LocalDate endsOn, StatusChangeReason reason) {
        boolean isStatusChange = status != current.status() || !Objects.equals(endsOn, current.endsOn());
        return Optional.of(new StatusDecision(status, endsOn, evidence.occurredAt(), reason, isStatusChange));
    }

    private static boolean isOlderThanLatestEvidence(StatusSnapshot current, StatusEvidence evidence) {
        return current.evidenceAt() != null && evidence.occurredAt().isBefore(current.evidenceAt());
    }
}
