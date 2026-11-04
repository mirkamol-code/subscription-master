package com.mirkamolcode.dto.response;

import com.mirkamolcode.model.BillingFrequency;
import com.mirkamolcode.model.CategoryConfidence;
import com.mirkamolcode.model.CategorySource;
import com.mirkamolcode.model.BillingPlatform;
import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.model.PaymentMethodType;
import com.mirkamolcode.model.PriceChangeSource;
import com.mirkamolcode.model.ReviewReason;
import com.mirkamolcode.model.SubscriptionCategory;
import com.mirkamolcode.model.SubscriptionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SubscriptionResponse(
        Long id,
        String name,
        BigDecimal price,
        CurrencyCode currency,
        BillingFrequency frequency,
        SubscriptionStatus status,
        SubscriptionCategory category,
        CategorySource categorySource,
        CategoryConfidence categoryConfidence,
        LocalDate startDate,
        LocalDate nextPaymentDate,
        String merchantKey,
        boolean needsReview,
        ReviewReason reviewReason,
        String reviewNote,
        boolean hidden,
        LocalDate lastPaymentDate,
        String logoDomain,
        LocalDate endsOn,
        long version,
        String planName,
        BillingPlatform billingPlatform,
        PaymentMethod paymentMethod,
        PriceChangeSummary latestPriceChange
) {
    public record PaymentMethod(PaymentMethodType type, String cardBrand, String cardLast4) {
    }

    public record PriceChangeSummary(BigDecimal oldAmount, BigDecimal newAmount, CurrencyCode currency, LocalDate effectiveOn,
                                     PriceChangeSource source) {
    }
}
