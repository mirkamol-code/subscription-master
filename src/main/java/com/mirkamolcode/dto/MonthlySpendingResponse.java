package com.mirkamolcode.dto;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public record MonthlySpendingResponse(
        YearMonth month,
        BigDecimal totalUzs,
        String baseCurrency,
        List<CategoryCost> byCategory
) {
}
