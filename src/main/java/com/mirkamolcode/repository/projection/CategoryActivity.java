package com.mirkamolcode.repository.projection;

import java.math.BigDecimal;

public interface CategoryActivity {
    String getCategory();

    long getActiveSubscribers();

    long getActiveSubscriptions();

    BigDecimal getTrackedSpendUzs();
}
