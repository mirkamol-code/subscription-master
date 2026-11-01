package com.mirkamolcode.repository.projection;

import com.mirkamolcode.model.CurrencyCode;

import java.math.BigDecimal;

public interface CurrencyTotal {
    CurrencyCode getCurrency();

    long getCount();

    BigDecimal getTotal();
}
