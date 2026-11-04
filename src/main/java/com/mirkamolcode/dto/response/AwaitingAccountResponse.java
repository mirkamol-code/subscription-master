package com.mirkamolcode.dto.response;

import com.mirkamolcode.detection.EmailKind;
import com.mirkamolcode.model.CurrencyCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AwaitingAccountResponse(
        Long emailId,
        EmailKind kind,
        String sender,
        String subject,
        Instant receivedAt,
        BigDecimal amount,
        CurrencyCode currency,
        List<AccountOption> accounts
) {
    public record AccountOption(Long subscriptionId, String name, String customerReference) {
    }
}
