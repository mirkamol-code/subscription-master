package com.mirkamolcode.entity;

import com.mirkamolcode.detection.Confidence;
import com.mirkamolcode.model.BillingFrequency;
import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.model.ReviewReason;
import com.mirkamolcode.model.SubscriptionCategory;
import com.mirkamolcode.model.SubscriptionStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
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

    @Column(length = 120)
    private String merchantKey;

    @Column(length = 120, insertable = false, updatable = false)
    private String merchantSlug;

    @Column(nullable = false)
    private boolean needsReview = false;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private ReviewReason reviewReason;

    @Column(nullable = false)
    private boolean isHidden = false;

    private LocalDate lastPaymentDate;

    @Column(length = 253)
    private String logoDomain;

    private Instant statusEvidenceAt;

    private LocalDate endsOn;

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

    public void update(String name, BigDecimal price, CurrencyCode currency, BillingFrequency frequency, SubscriptionCategory category, LocalDate startDate) {
        this.name = name;
        this.price = price;
        this.currency = currency;
        this.frequency = frequency;
        this.category = category;
        this.startDate = startDate;
        clearReview();
    }

    public void markDeleted() {
        isDeleted = true;
        status = SubscriptionStatus.CANCELLED;
    }

    public void linkMerchant(String merchantKey, String logoDomain) {
        this.merchantKey = merchantKey;
        this.logoDomain = logoDomain;
    }

    public void recordPayment(LocalDate paidOn, BigDecimal amount, CurrencyCode currency, BillingFrequency cycle,
                              LocalDate nextPaymentDate, Confidence confidence) {
        if (lastPaymentDate != null && paidOn.isBefore(lastPaymentDate))
            return;
        boolean changed = price.compareTo(amount) != 0 || this.currency != currency || frequency != cycle;
        if (confidence == Confidence.LOW && changed)
            needsReview = true;
        this.price = amount;
        this.currency = currency;
        this.frequency = cycle;
        this.lastPaymentDate = paidOn;
        this.nextPaymentDate = nextPaymentDate != null ? nextPaymentDate : cycle.addTo(paidOn);
    }

    public void applyStatus(SubscriptionStatus status, LocalDate endsOn, Instant evidenceAt) {
        this.status = status;
        this.endsOn = endsOn;
        this.statusEvidenceAt = evidenceAt;
    }

    public void confirmPayment(LocalDate paidOn, BigDecimal amount, CurrencyCode currency) {
        recordPayment(paidOn, amount, currency, frequency, null, Confidence.HIGH);
        clearReview();
    }

    public void flagUnreadableAmount() {
        needsReview = true;
        reviewReason = ReviewReason.AMOUNT_UNREADABLE;
    }

    public boolean isAwaitingAmountReview() {
        return reviewReason == ReviewReason.AMOUNT_UNREADABLE;
    }

    public boolean isInRenewalWindow(LocalDate day, int daysBefore, int daysAfter) {
        return !day.isBefore(nextPaymentDate.minusDays(daysBefore)) && !day.isAfter(nextPaymentDate.plusDays(daysAfter));
    }

    private void clearReview() {
        needsReview = false;
        reviewReason = null;
    }

    public void flagForReview() {
        needsReview = true;
    }

    public void hide() {
        isHidden = true;
    }

    public void unhide() {
        isHidden = false;
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

    public String getMerchantKey() {
        return merchantKey;
    }

    public String getMerchantSlug() {
        return merchantSlug;
    }

    public boolean isNeedsReview() {
        return needsReview;
    }

    public ReviewReason getReviewReason() {
        return reviewReason;
    }

    public String getReviewNote() {
        return reviewReason == null ? null : reviewReason.note();
    }

    public boolean isHidden() {
        return isHidden;
    }

    public LocalDate getLastPaymentDate() {
        return lastPaymentDate;
    }

    public String getLogoDomain() {
        return logoDomain;
    }

    public Instant getStatusEvidenceAt() {
        return statusEvidenceAt;
    }

    public LocalDate getEndsOn() {
        return endsOn;
    }

    public long getVersion() {
        return version;
    }
}
