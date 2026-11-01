package com.mirkamolcode.dto.request;

import com.mirkamolcode.model.SubscriptionStatus;

public record AdminUserFilter(
        String search,
        Integer joinedWithinDays,
        Integer loggedInWithinDays,
        SubscriptionStatus status,
        String merchant
) {
}
