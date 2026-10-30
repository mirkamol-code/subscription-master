package com.mirkamolcode.notification;

import com.mirkamolcode.entity.Subscription;
import com.mirkamolcode.event.SubscriptionStatusChangedEvent;
import com.mirkamolcode.model.NotificationSeverity;
import com.mirkamolcode.model.NotificationType;
import com.mirkamolcode.model.StatusChangeReason;
import com.mirkamolcode.model.SubscriptionStatus;

import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class NotificationMessageFactory {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH);

    private final String frontendBaseUrl;

    public NotificationMessageFactory(@Value("${app.frontend-base-url}") String frontendBaseUrl) {
        this.frontendBaseUrl = frontendBaseUrl;
    }

    public NotificationMessage paymentDue(Subscription subscription) {
        String name = subscription.getName();
        String due = DATE.format(subscription.getNextPaymentDate());
        String amount = subscription.getPrice().setScale(2, RoundingMode.HALF_UP).toPlainString() + " " + subscription.getCurrency();
        return message(NotificationType.PAYMENT_DUE, NotificationSeverity.INFO, subscription.getId(),
                name + " renews on " + due,
                amount + " will be charged for " + name + " on " + due + ".");
    }

    public NotificationMessage statusChanged(SubscriptionStatusChangedEvent event, ZoneId zone) {
        String name = event.subscriptionName();
        String on = DATE.format(event.effectiveAt().atZone(zone).toLocalDate());
        if (event.toStatus() == SubscriptionStatus.BILLING_REQUIRED)
            return message(NotificationType.BILLING_REQUIRED, NotificationSeverity.URGENT, event.subscriptionId(),
                    "Payment problem with " + name,
                    event.reason() == StatusChangeReason.OVERDUE
                            ? "We expected a payment for " + name + " on " + on + " but found no receipt. Check your payment method."
                            : name + " couldn't charge your payment method on " + on + ". Update it to keep the subscription.");
        if (event.toStatus() == SubscriptionStatus.CANCELLED)
            return message(NotificationType.CANCELLED, NotificationSeverity.INFO, event.subscriptionId(),
                    name + " was cancelled",
                    event.reason() == StatusChangeReason.SCHEDULED_END
                            ? "Your access to " + name + " ended on " + on + "."
                            : name + " was cancelled on " + on + ".");
        if (event.endsOn() != null)
            return message(NotificationType.CANCELLATION_SCHEDULED, NotificationSeverity.INFO, event.subscriptionId(),
                    name + " ends on " + DATE.format(event.endsOn()),
                    name + " was cancelled and stays active until " + DATE.format(event.endsOn()) + ".");
        return message(NotificationType.REACTIVATED, NotificationSeverity.INFO, event.subscriptionId(),
                name + " is active again",
                "A new payment for " + name + " arrived on " + on + ", so it's active again.");
    }

    public NotificationMessage retracked(Long subscriptionId, String merchantName, LocalDate chargedOn) {
        return message(NotificationType.RETRACKED, NotificationSeverity.INFO, subscriptionId,
                merchantName + " is back",
                merchantName + " charged you again on " + DATE.format(chargedOn) + ", so we're tracking it again.");
    }

    private NotificationMessage message(NotificationType type, NotificationSeverity severity, Long subscriptionId,
                                        String title, String body) {
        return new NotificationMessage(type, severity, subscriptionId, title, body,
                frontendBaseUrl + "/subscriptions/" + subscriptionId);
    }
}
