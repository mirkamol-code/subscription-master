package com.mirkamolcode.dto.response;

import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.model.DueBucket;
import com.mirkamolcode.model.DueEntryKind;
import com.mirkamolcode.model.SubscriptionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PaymentDueResponse(
        Long subscriptionId,
        Long userId,
        String userName,
        String userEmail,
        String merchant,
        String merchantSlug,
        DueEntryKind kind,
        LocalDate dueDate,
        BigDecimal amount,
        CurrencyCode currency,
        BigDecimal amountUzs,
        SubscriptionStatus status,
        DueBucket bucket
) {
}
