package com.mirkamolcode.entity;

import com.mirkamolcode.detection.Confidence;
import com.mirkamolcode.detection.PriceChangeNotice;
import com.mirkamolcode.model.BillingFrequency;
import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.model.PriceChangeSource;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

@Entity
@Table(name = "price_changes")
public class PriceChange extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String merchantKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id")
    private Subscription subscription;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_email_id")
    private BillingEmail billingEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private PriceChangeSource source;

    @Column(precision = 19, scale = 4)
    private BigDecimal oldAmount;

    @Column(precision = 19, scale = 4)
    private BigDecimal newAmount;

    @Enumerated(EnumType.STRING)
    @Column(length = 3)
    private CurrencyCode currency;

    private LocalDate effectiveOn;

    @Column(nullable = false)
    private Instant observedAt;

    protected PriceChange() {
    }

    private PriceChange(User user, String merchantKey, Subscription subscription, BillingEmail billingEmail, PriceChangeSource source,
                        BigDecimal oldAmount, BigDecimal newAmount, CurrencyCode currency, LocalDate effectiveOn, Instant observedAt) {
        this.user = user;
        this.merchantKey = merchantKey;
        this.subscription = subscription;
        this.billingEmail = billingEmail;
        this.source = source;
        this.oldAmount = oldAmount;
        this.newAmount = newAmount;
        this.currency = currency;
        this.effectiveOn = effectiveOn;
        this.observedAt = observedAt;
    }

    public static PriceChange fromNotice(User user, String merchantKey, Subscription subscription, BillingEmail notice,
                                         PriceChangeNotice details) {
        return new PriceChange(user, merchantKey, subscription, notice, PriceChangeSource.NOTICE, details.oldAmount(),
                details.newAmount(), details.currency(), details.effectiveOn(), notice.getReceivedAt());
    }

    public void attachTo(Subscription subscription) {
        this.subscription = subscription;
    }

    public static Optional<PriceChange> fromInvoice(Subscription subscription, BillingEmail invoice, LocalDate paidOn, BigDecimal amount,
                                                    CurrencyCode currency, BillingFrequency cycle, Confidence confidence,
                                                    boolean usageBased) {
        boolean isNewerInvoice = subscription.getLastPaymentDate() != null && paidOn.isAfter(subscription.getLastPaymentDate());
        boolean isComparable = confidence == Confidence.HIGH && !usageBased && currency == subscription.getCurrency()
                && cycle == subscription.getFrequency();
        if (!isNewerInvoice || !isComparable || amount.compareTo(subscription.getPrice()) == 0)
            return Optional.empty();
        return Optional.of(new PriceChange(subscription.getUser(), subscription.getMerchantKey(), subscription, invoice,
                PriceChangeSource.INVOICE, subscription.getPrice(), amount, currency,
                paidOn, paidOn.atStartOfDay(ZoneOffset.UTC).toInstant()));
    }

    public boolean isNewerThan(PriceChange other) {
        return other == null || observedAt.isAfter(other.observedAt);
    }

    public Long getId() {
        return id;
    }

    public PriceChangeSource getSource() {
        return source;
    }

    public BigDecimal getOldAmount() {
        return oldAmount;
    }

    public BigDecimal getNewAmount() {
        return newAmount;
    }

    public CurrencyCode getCurrency() {
        return currency;
    }

    public LocalDate getEffectiveOn() {
        return effectiveOn;
    }
}
