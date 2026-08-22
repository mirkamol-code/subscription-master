package com.mirkamolcode.entity;

import com.mirkamolcode.model.CurrencyCode;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "payment_history", indexes = @Index(name = "idx_payment_history_subscription", columnList = "subscription_id"))
public class PaymentHistory extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    @Column(nullable = false)
    private LocalDate paymentDate;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal originalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private CurrencyCode originalCurrency;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal exchangeRateToUzs;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amountUzs;

    protected PaymentHistory() {
    }

    public PaymentHistory(Subscription subscription, LocalDate paymentDate, BigDecimal originalAmount, CurrencyCode originalCurrency, BigDecimal exchangeRateToUzs) {
        this.subscription = subscription;
        this.paymentDate = paymentDate;
        this.originalAmount = originalAmount;
        this.originalCurrency = originalCurrency;
        this.exchangeRateToUzs = exchangeRateToUzs;
        this.amountUzs = originalAmount.multiply(exchangeRateToUzs);
    }

    public Long getId() {
        return id;
    }

    public Subscription getSubscription() {
        return subscription;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public BigDecimal getOriginalAmount() {
        return originalAmount;
    }

    public CurrencyCode getOriginalCurrency() {
        return originalCurrency;
    }

    public BigDecimal getExchangeRateToUzs() {
        return exchangeRateToUzs;
    }

    public BigDecimal getAmountUzs() {
        return amountUzs;
    }
}
