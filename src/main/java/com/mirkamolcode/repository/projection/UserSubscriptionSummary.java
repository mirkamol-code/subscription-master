package com.mirkamolcode.repository.projection;

public interface UserSubscriptionSummary {
    Long getUserId();

    long getActiveCount();

    boolean getHasBillingRequired();

    boolean getHasActive();

    boolean getHasCancelled();

    String getMerchantNames();
}
