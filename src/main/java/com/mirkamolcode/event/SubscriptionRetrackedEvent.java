package com.mirkamolcode.event;

import java.time.LocalDate;

public record SubscriptionRetrackedEvent(Long userId, Long subscriptionId, String merchantName, LocalDate chargedOn) {
}
