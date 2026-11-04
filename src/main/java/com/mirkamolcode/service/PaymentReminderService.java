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
        LocalDate due = subscription.getNextPaymentDate();
        if (isWithinReminderWindow(subscription, due))
            dispatcher.dispatch(subscription.getUser().getId(), messages.paymentDue(subscription), "PAYMENT_DUE:" + subscriptionId + ":" + due);
    }

    public void remindTrialEndingIfDue(Long subscriptionId) {
        Subscription subscription = subscriptions.findById(subscriptionId).orElseThrow();
        LocalDate endsOn = subscription.getTrialEndsOn();
        if (!subscription.isTrial() || subscription.getEndsOn() != null || endsOn == null)
            return;
        LocalDate remindFor = subscription.isTrialEndEstimated() ? endsOn.minusDays(1) : endsOn;
        if (isWithinReminderWindow(subscription, remindFor))
            dispatcher.dispatch(subscription.getUser().getId(), messages.trialEnding(subscription),
                    "TRIAL_ENDING:" + subscriptionId + ":" + endsOn);
    }

    private boolean isWithinReminderWindow(Subscription subscription, LocalDate date) {
        ZonedDateTime usersNow = ZonedDateTime.ofInstant(clock.instant(), subscription.getUser().getTimeZone());
        if (usersNow.getHour() < reminderHour)
            return false;
        LocalDate today = usersNow.toLocalDate();
        int daysBefore = settings.findOrDefault(subscription.getUser().getId()).getDaysBefore();
        return !date.isBefore(today) && !date.isAfter(today.plusDays(daysBefore));
    }
}
