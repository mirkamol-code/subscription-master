package com.mirkamolcode.repository.projection;

import java.math.BigDecimal;

public interface MerchantActivity {
    String getSlug();

    String getName();

    long getActiveSubscribers();

    long getActiveSubscriptions();

    long getNewLast7Days();

    long getNewLast30Days();

    long getCancelledInRange();

    BigDecimal getTrackedSpendUzs();

    long getPayingUsers();

    long getLogins();
}
