package com.mirkamolcode.notification;

import com.mirkamolcode.service.NotificationService;
import com.mirkamolcode.entity.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
        name = "app.notifications.log.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class LogNotificationService implements NotificationService {

    private static final Logger LOG = LoggerFactory.getLogger(LogNotificationService.class);

    public void paymentDueSoon(Subscription subscription) {
        LOG.info("Payment due in two days: subscriptionId={}, user={}, dueDate={}",
                subscription.getId(),
                subscription.getUser().getEmail(),
                subscription.getNextPaymentDate());
    }
}
