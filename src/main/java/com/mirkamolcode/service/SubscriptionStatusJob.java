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
public class SubscriptionStatusJob {
    private static final Logger log = LoggerFactory.getLogger(SubscriptionStatusJob.class);

    private final SubscriptionRepository subscriptions;
    private final SubscriptionStatusService statusService;
    private final Clock clock;

    public SubscriptionStatusJob(SubscriptionRepository subscriptions, SubscriptionStatusService statusService, Clock clock) {
        this.subscriptions = subscriptions;
        this.statusService = statusService;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.status.daily-rules-cron}")
    @SchedulerLock(name = "subscriptionStatusRules", lockAtMostFor = "PT50M")
    public void applyDailyRules() {
        LocalDate latestLocalDate = LocalDate.ofInstant(clock.instant(), ZoneOffset.MAX);
        LocalDate overdueBefore = latestLocalDate.minusDays(statusService.getOverdueGraceDays());
        for (Long subscriptionId : subscriptions.findDailyRuleCandidateIds(latestLocalDate, overdueBefore)) {
            try {
                statusService.applyDailyRules(subscriptionId);
            } catch (RuntimeException e) {
                log.warn("Daily status rules failed for subscription {}: {}", subscriptionId, e.getMessage());
            }
        }
    }
}
