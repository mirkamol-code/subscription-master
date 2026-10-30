package com.mirkamolcode.service;

import com.mirkamolcode.repository.SubscriptionRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentReminderJob {
    private static final Logger log = LoggerFactory.getLogger(PaymentReminderJob.class);
    private static final int MAX_DAYS_BEFORE_WITH_ZONE_SLACK = 32;

    private final SubscriptionRepository subscriptions;
    private final PaymentReminderService reminders;
    private final Clock clock;

    public PaymentReminderJob(SubscriptionRepository subscriptions, PaymentReminderService reminders, Clock clock) {
        this.subscriptions = subscriptions;
        this.reminders = reminders;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.notifications.reminder-cron}")
    @SchedulerLock(name = "paymentReminders", lockAtMostFor = "PT50M")
    public void sendDueReminders() {
        LocalDate utcToday = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        for (Long subscriptionId : subscriptions.findReminderCandidateIds(utcToday.minusDays(1), utcToday.plusDays(MAX_DAYS_BEFORE_WITH_ZONE_SLACK))) {
            try {
                reminders.remindIfDue(subscriptionId);
            } catch (RuntimeException e) {
                log.warn("Payment reminder failed for subscription {}: {}", subscriptionId, e.getMessage());
            }
        }
    }
}
