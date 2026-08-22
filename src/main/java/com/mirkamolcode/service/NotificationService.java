package com.mirkamolcode.service;

import com.mirkamolcode.entity.Subscription;

public interface NotificationService {
    void paymentDueSoon(Subscription subscription);
}
