package com.mirkamolcode.entity;

import com.mirkamolcode.detection.CategoryGuess;
import com.mirkamolcode.detection.Confidence;
import com.mirkamolcode.detection.Money;
import com.mirkamolcode.model.CategoryConfidence;
import com.mirkamolcode.model.CategorySource;
import com.mirkamolcode.detection.PaymentMethod;
import com.mirkamolcode.model.BillingFrequency;
import com.mirkamolcode.model.BillingPlatform;
import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.model.PaymentMethodType;
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

    @Column(precision = 19, scale = 4)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(length = 3)
    private CurrencyCode currency;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private BillingFrequency frequency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SubscriptionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionCategory category;

    @Column(nullable = false)
    private LocalDate startDate;

    private LocalDate nextPaymentDate;

    private LocalDate trialEndsOn;

    @Column(name = "is_read_only", nullable = false)
    private boolean readOnly;

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

    @Column(length = 120)
    private String planName;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private BillingPlatform billingPlatform;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private PaymentMethodType paymentMethodType;

    @Column(length = 20)
    private String cardBrand;

    @Column(length = 4)
    private String cardLast4;

    @Column(length = 64)
    private String customerReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private CategorySource categorySource = CategorySource.UNKNOWN;

    @Enumerated(EnumType.STRING)
    @Column(length = 6)
    private CategoryConfidence categoryConfidence;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "latest_price_change_id")
    private PriceChange latestPriceChange;

    @Version
    private long version;

    private static final int REPEATED_INVOICES = 3;

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

    public static Subscription startTrial(User user, String name, SubscriptionCategory category, LocalDate startedOn,
                                          LocalDate trialEndsOn, Money priceAfterTrial, BillingFrequency cycle) {
        Subscription trial = new Subscription();
        trial.user = user;
        trial.name = name;
        trial.status = SubscriptionStatus.TRIAL;
        trial.category = category;
        trial.startDate = startedOn;
        trial.describeTrial(trialEndsOn, priceAfterTrial, cycle);
        return trial;
    }

    public void describeTrial(LocalDate trialEndsOn, Money priceAfterTrial, BillingFrequency cycle) {
        this.trialEndsOn = trialEndsOn;
        this.nextPaymentDate = trialEndsOn;
        this.price = priceAfterTrial == null ? null : priceAfterTrial.amount();
        this.currency = priceAfterTrial == null ? null : priceAfterTrial.currency();
        this.frequency = cycle;
    }

    public static Subscription earlierApplePurchases(Subscription legacy, String merchantKey) {
        Subscription history = new Subscription(legacy.user, "Apple (earlier purchases)", legacy.price, legacy.currency, legacy.frequency,
                SubscriptionStatus.CANCELLED, legacy.category, legacy.startDate, legacy.nextPaymentDate);
        history.linkMerchant(merchantKey, legacy.logoDomain);
        history.lastPaymentDate = legacy.lastPaymentDate;
        history.readOnly = true;
        return history;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void carryUserChoicesFrom(Subscription legacy, boolean carryHidden, boolean carryCategory) {
        if (carryHidden && legacy.isHidden)
            isHidden = true;
        if (carryCategory && legacy.category != SubscriptionCategory.OTHER) {
            category = legacy.category;
            categorySource = CategorySource.USER;
            categoryConfidence = null;
        }
    }

    public boolean isTrial() {
        return status == SubscriptionStatus.TRIAL;
    }

    public LocalDate getTrialEndsOn() {
        return trialEndsOn;
    }

    public void update(String name, BigDecimal price, CurrencyCode currency, BillingFrequency frequency, SubscriptionCategory category, LocalDate startDate) {
        this.name = name;
        this.price = price;
        this.currency = currency;
        this.frequency = frequency;
        if (category != this.category) {
            this.category = category;
            this.categorySource = CategorySource.USER;
            this.categoryConfidence = null;
        }
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

    public void describeBilling(String planName, BillingPlatform billingPlatform, PaymentMethod paymentMethod) {
        this.billingPlatform = billingPlatform;
        if (planName != null)
            this.planName = planName;
        if (paymentMethod != null) {
            this.paymentMethodType = paymentMethod.type();
            this.cardBrand = paymentMethod.cardBrand();
            this.cardLast4 = paymentMethod.cardLast4();
        }
    }

    public void restoreLatestPayment(LocalDate paidOn, BigDecimal amount, CurrencyCode currency) {
        this.price = amount;
        this.currency = currency;
        this.lastPaymentDate = paidOn;
        this.nextPaymentDate = frequency.addTo(paidOn);
    }

    public void fillMissingBilling(String planName, PaymentMethod paymentMethod) {
        if (this.planName == null)
            this.planName = planName;
        if (paymentMethodType == null && paymentMethod != null) {
            this.paymentMethodType = paymentMethod.type();
            this.cardBrand = paymentMethod.cardBrand();
            this.cardLast4 = paymentMethod.cardLast4();
        }
    }

    public void assignCustomer(String customerReference) {
        if (this.customerReference == null)
            this.customerReference = customerReference;
    }

    public void recordPayment(LocalDate paidOn, BigDecimal amount, CurrencyCode currency, BillingFrequency cycle,
                              LocalDate nextPaymentDate, Confidence confidence) {
        if (lastPaymentDate != null && paidOn.isBefore(lastPaymentDate))
            return;
        boolean changed = price == null || price.compareTo(amount) != 0 || this.currency != currency || frequency != cycle;
        if (confidence == Confidence.LOW && changed)
            needsReview = true;
        this.price = amount;
        this.currency = currency;
        this.frequency = cycle;
        this.lastPaymentDate = paidOn;
        this.nextPaymentDate = nextPaymentDate != null ? nextPaymentDate : cycle.addTo(paidOn);
        this.trialEndsOn = null;
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

    public void flagSimilarInvoicesForReview(int invoicesSeen) {
        needsReview = true;
        reviewReason = invoicesSeen >= REPEATED_INVOICES ? ReviewReason.SIMILAR_INVOICES_REPEATED : ReviewReason.SIMILAR_INVOICES;
    }

    public boolean isReviewingSimilarInvoices() {
        return reviewReason == ReviewReason.SIMILAR_INVOICES || reviewReason == ReviewReason.SIMILAR_INVOICES_REPEATED;
    }

    public void confirmRecurring() {
        clearReview();
    }

    public void raiseRecurrenceConfidence(int invoicesSeen) {
        if (reviewReason == ReviewReason.SIMILAR_INVOICES && invoicesSeen >= REPEATED_INVOICES)
            reviewReason = ReviewReason.SIMILAR_INVOICES_REPEATED;
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

    public String getPlanName() {
        return planName;
    }

    public BillingPlatform getBillingPlatform() {
        return billingPlatform;
    }

    public PaymentMethodType getPaymentMethodType() {
        return paymentMethodType;
    }

    public String getCardBrand() {
        return cardBrand;
    }

    public String getCardLast4() {
        return cardLast4;
    }

    public String getCustomerReference() {
        return customerReference;
    }

    public void applyDetectedCategory(CategoryGuess guess) {
        if (categorySource == CategorySource.USER)
            return;
        this.category = guess.category();
        this.categorySource = CategorySource.DETECTED;
        this.categoryConfidence = guess.confidence();
    }

    public CategorySource getCategorySource() {
        return categorySource;
    }

    public CategoryConfidence getCategoryConfidence() {
        return categoryConfidence;
    }

    public void noteLatestPriceChange(PriceChange change) {
        if (change.isNewerThan(latestPriceChange))
            latestPriceChange = change;
    }

    public PriceChange getLatestPriceChange() {
        return latestPriceChange;
    }
}
