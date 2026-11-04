package com.mirkamolcode.detection;

import com.mirkamolcode.model.CurrencyCode;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PriceChangeNotice(BigDecimal oldAmount, BigDecimal newAmount, CurrencyCode currency, LocalDate effectiveOn) {
}
