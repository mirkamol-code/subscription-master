package com.mirkamolcode.service;

import com.mirkamolcode.entity.BillingEmail;
import com.mirkamolcode.entity.StatusChange;
import com.mirkamolcode.entity.Subscription;
import com.mirkamolcode.event.SubscriptionStatusChangedEvent;
import com.mirkamolcode.model.SubscriptionStatus;
import com.mirkamolcode.repository.StatusChangeRepository;
import com.mirkamolcode.repository.SubscriptionRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SubscriptionStatusService {
    private final SubscriptionStatusPolicy policy;
    private final SubscriptionRepository subscriptions;
    private final StatusChangeRepository statusChanges;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final int overdueGraceDays;

    public SubscriptionStatusService(SubscriptionStatusPolicy policy,
                                     SubscriptionRepository subscriptions,
                                     StatusChangeRepository statusChanges,
                                     ApplicationEventPublisher events,
                                     Clock clock,
                                     @Value("${app.status.overdue-grace-days}") int overdueGraceDays) {
        this.policy = policy;
        this.subscriptions = subscriptions;
        this.statusChanges = statusChanges;
        this.events = events;
        this.clock = clock;
        this.overdueGraceDays = overdueGraceDays;
    }

    public void apply(Subscription subscription, StatusEvidence evidence, BillingEmail source) {
        StatusSnapshot current = new StatusSnapshot(subscription.getStatus(), subscription.getStatusEvidenceAt(), subscription.getEndsOn(),
                subscription.getNextPaymentDate());
        policy.evaluate(current, evidence, calculateUsersToday(subscription)).ifPresent(decision -> {
            SubscriptionStatus from = subscription.getStatus();
            subscription.applyStatus(decision.status(), decision.endsOn(), decision.evidenceAt());
            if (decision.isStatusChange())
                recordChange(subscription, from, decision, source);
        });
    }

    public void markByUser(Subscription subscription, SubscriptionStatus status) {
        apply(subscription, StatusEvidence.userMark(clock.instant(), status), null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyDailyRules(Long subscriptionId) {
        Subscription subscription = subscriptions.findById(subscriptionId).orElseThrow();
        ZoneId zone = subscription.getUser().getTimeZone();
        if (subscription.getEndsOn() != null)
            apply(subscription, StatusEvidence.scheduledEnd(subscription.getEndsOn().atStartOfDay(zone).toInstant()), null);
        else if (calculateUsersToday(subscription).isAfter(subscription.getNextPaymentDate().plusDays(overdueGraceDays)))
            apply(subscription, StatusEvidence.overdue(subscription.getNextPaymentDate().atStartOfDay(zone).toInstant()), null);
    }

    public int getOverdueGraceDays() {
        return overdueGraceDays;
    }

    private LocalDate calculateUsersToday(Subscription subscription) {
        return LocalDate.ofInstant(clock.instant(), subscription.getUser().getTimeZone());
    }

    private void recordChange(Subscription subscription, SubscriptionStatus from, StatusDecision decision, BillingEmail source) {
        statusChanges.save(new StatusChange(subscription, from, decision.status(), decision.reason(), source,
                decision.evidenceAt(), decision.endsOn()));
        events.publishEvent(new SubscriptionStatusChangedEvent(subscription.getUser().getId(), subscription.getId(),
                subscription.getName(), from, decision.status(), decision.reason(), decision.evidenceAt(), decision.endsOn()));
    }
}
