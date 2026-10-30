package com.mirkamolcode.entity;

import com.mirkamolcode.model.StatusChangeReason;
import com.mirkamolcode.model.SubscriptionStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "status_changes")
public class StatusChange extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SubscriptionStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SubscriptionStatus toStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private StatusChangeReason reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_email_id")
    private BillingEmail billingEmail;

    @Column(nullable = false)
    private Instant effectiveAt;

    private LocalDate endsOn;

    protected StatusChange() {
    }

    public StatusChange(Subscription subscription, SubscriptionStatus fromStatus, SubscriptionStatus toStatus,
                        StatusChangeReason reason, BillingEmail billingEmail, Instant effectiveAt, LocalDate endsOn) {
        this.subscription = subscription;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
        this.billingEmail = billingEmail;
        this.effectiveAt = effectiveAt;
        this.endsOn = endsOn;
    }

    public Long getId() {
        return id;
    }

    public SubscriptionStatus getFromStatus() {
        return fromStatus;
    }

    public SubscriptionStatus getToStatus() {
        return toStatus;
    }

    public StatusChangeReason getReason() {
        return reason;
    }

    public BillingEmail getBillingEmail() {
        return billingEmail;
    }

    public Instant getEffectiveAt() {
        return effectiveAt;
    }

    public LocalDate getEndsOn() {
        return endsOn;
    }
}
