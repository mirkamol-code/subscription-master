package com.mirkamolcode.entity;

import com.mirkamolcode.model.BillingFrequency;
import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.model.SubscriptionCategory;
import com.mirkamolcode.model.SubscriptionStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "subscriptions", indexes = {@Index(name = "idx_subscription_user_deleted", columnList = "user_id,is_deleted"), @Index(name = "idx_subscription_due", columnList = "next_payment_date")})
public class Subscription extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private CurrencyCode currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BillingFrequency frequency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SubscriptionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionCategory category;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate nextPaymentDate;

    @Column(nullable = false)
    private boolean isDeleted = false;

    @Version
    private long version;

    public Subscription() {}

    public Subscription(User user, String name, BigDecimal price, CurrencyCode currency, BillingFrequency frequency, SubscriptionStatus status, SubscriptionCategory category, LocalDate startDate) {
        this(user, name, price, currency, frequency, status, category, startDate, frequency.addTo(startDate));
    }

    public Subscription(User user, String name, BigDecimal price, CurrencyCode currency, BillingFrequency frequency, SubscriptionStatus status, SubscriptionCategory category, LocalDate startDate, LocalDate nextPaymentDate) {
        this.user = user;
        this.name = name;
        this.price = price;
        this.currency = currency;
        this.frequency = frequency;
        this.status = status;
        this.category = category;
        this.startDate = startDate;
        this.nextPaymentDate = nextPaymentDate != null ? nextPaymentDate : frequency.addTo(startDate);
    }

    public void update(String name, BigDecimal price, CurrencyCode currency, BillingFrequency frequency, SubscriptionStatus status, SubscriptionCategory category, LocalDate startDate) {
        this.name = name;
        this.price = price;
        this.currency = currency;
        this.frequency = frequency;
        this.status = status;
        this.category = category;
        this.startDate = startDate;
    }

    public void markDeleted() {
        isDeleted = true;
        status = SubscriptionStatus.CANCELLED;
    }

    /** TASK-08: Pause a subscription */
    public void pause() {
        this.status = SubscriptionStatus.PAUSED;
    }

    /** TASK-08: Resume a paused subscription back to ACTIVE */
    public void resume() {
        this.status = SubscriptionStatus.ACTIVE;
    }

    public void advanceNextPaymentDate() {
        nextPaymentDate = frequency.addTo(nextPaymentDate);
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public CurrencyCode getCurrency() {
        return currency;
    }

    public BillingFrequency getFrequency() {
        return frequency;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public SubscriptionCategory getCategory() {
        return category;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getNextPaymentDate() {
        return nextPaymentDate;
    }

    public boolean isDeleted() {
        return isDeleted;
    }

    public long getVersion() {
        return version;
    }
}
