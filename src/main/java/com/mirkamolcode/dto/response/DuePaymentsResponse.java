package com.mirkamolcode.dto.response;

import java.time.LocalDate;
import java.util.List;

public record DuePaymentsResponse(
        LocalDate today,
        String timeZone,
        List<PaymentDueResponse> entries
) {
}
