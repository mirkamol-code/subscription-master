package com.mirkamolcode.detection;

import com.mirkamolcode.model.BillingFrequency;
import com.mirkamolcode.model.SubscriptionCategory;

import java.util.List;

public record Merchant(
        String key,
        String displayName,
        List<String> aliases,
        String logoDomain,
        SubscriptionCategory category,
        BillingFrequency defaultCycle,
        boolean known
) {}
