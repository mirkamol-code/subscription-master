package com.mirkamolcode.dto;

import com.mirkamolcode.model.CurrencyCode;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SourceRate(CurrencyCode currency, BigDecimal rateToUzs, LocalDate rateDate) {
}
