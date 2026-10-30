package com.mirkamolcode.service;

import com.mirkamolcode.entity.Subscription;
import com.mirkamolcode.notification.NotificationDispatcher;
import com.mirkamolcode.notification.NotificationMessageFactory;
import com.mirkamolcode.repository.SubscriptionRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PaymentReminderService {
    private final SubscriptionRepository subscriptions;
    private final NotificationSettingsService settings;
    private final NotificationDispatcher dispatcher;
    private final NotificationMessageFactory messages;
    private final Clock clock;
    private final int reminderHour;

    public PaymentReminderService(SubscriptionRepository subscriptions,
                                  NotificationSettingsService settings,
                                  NotificationDispatcher dispatcher,
                                  NotificationMessageFactory messages,
                                  Clock clock,
                                  @Value("${app.notifications.reminder-hour}") int reminderHour) {
        this.subscriptions = subscriptions;
        this.settings = settings;
        this.dispatcher = dispatcher;
        this.messages = messages;
        this.clock = clock;
        this.reminderHour = reminderHour;
    }

    public void remindIfDue(Long subscriptionId) {
        Subscription subscription = subscriptions.findById(subscriptionId).orElseThrow();
        Long userId = subscription.getUser().getId();
        ZonedDateTime usersNow = ZonedDateTime.ofInstant(clock.instant(), subscription.getUser().getTimeZone());
        if (usersNow.getHour() < reminderHour)
            return;
        LocalDate today = usersNow.toLocalDate();
        LocalDate due = subscription.getNextPaymentDate();
        int daysBefore = settings.findOrDefault(userId).getDaysBefore();
        if (due.isBefore(today) || due.isAfter(today.plusDays(daysBefore)))
            return;
        dispatcher.dispatch(userId, messages.paymentDue(subscription), "PAYMENT_DUE:" + subscriptionId + ":" + due);
    }
}
