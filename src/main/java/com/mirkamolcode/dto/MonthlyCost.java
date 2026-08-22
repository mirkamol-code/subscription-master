package com.mirkamolcode.dto;

import java.math.BigDecimal;
import java.time.YearMonth;

public record MonthlyCost(
        YearMonth month,
        BigDecimal costUzs
) {
}
