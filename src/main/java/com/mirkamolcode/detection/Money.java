package com.mirkamolcode.detection;

import com.mirkamolcode.model.CurrencyCode;

import java.math.BigDecimal;
import java.util.Objects;

public record Money(BigDecimal amount, CurrencyCode currency) {
    @Override
    public boolean equals(Object other) {
        return other instanceof Money money && currency == money.currency && amount.compareTo(money.amount) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount.stripTrailingZeros(), currency);
    }
}
