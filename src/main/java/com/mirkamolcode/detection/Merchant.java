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
) {
    public List<String> namesForMatching() {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(displayName), aliases.stream()).toList();
    }
}
