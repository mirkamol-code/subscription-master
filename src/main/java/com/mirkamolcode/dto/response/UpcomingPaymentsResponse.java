package com.mirkamolcode.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record UpcomingPaymentsResponse(
        LocalDate today,
        String timeZone,
        List<PaymentDueResponse> entries,
        BigDecimal totalUzs,
        BigDecimal overdueTotalUzs
) {
}
