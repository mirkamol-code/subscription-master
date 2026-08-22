package com.mirkamolcode.entity;

import com.mirkamolcode.model.CurrencyCode;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(
        name = "exchange_rates",
        uniqueConstraints =
        @UniqueConstraint(
                name = "uk_exchange_rate_currency_date",
                columnNames = {"currency", "rate_date"})
)
public class ExchangeRate extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private CurrencyCode currency;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal rateToUzs;

    @Column(nullable = false)
    private LocalDate rateDate;

    protected ExchangeRate() {
    }

    public ExchangeRate(CurrencyCode currency, BigDecimal rateToUzs, LocalDate rateDate) {
        this.currency = currency;
        this.rateToUzs = rateToUzs;
        this.rateDate = rateDate;
    }

    public CurrencyCode getCurrency() {
        return currency;
    }

    public BigDecimal getRateToUzs() {
        return rateToUzs;
    }

    public LocalDate getRateDate() {
        return rateDate;
    }
}
