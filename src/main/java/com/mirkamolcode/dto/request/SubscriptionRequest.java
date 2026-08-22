package com.mirkamolcode.dto.request;

import com.mirkamolcode.model.BillingFrequency;
import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.model.SubscriptionCategory;
import com.mirkamolcode.model.SubscriptionStatus;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SubscriptionRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 15, fraction = 4) BigDecimal price,
        @NotNull CurrencyCode currency, @NotNull BillingFrequency frequency,
        @NotNull SubscriptionStatus status, @NotNull SubscriptionCategory category,
        @NotNull @PastOrPresent LocalDate startDate
) {
}
