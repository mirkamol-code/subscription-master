package com.mirkamolcode.service;

import com.mirkamolcode.entity.User;
import com.mirkamolcode.event.SubscriptionRetrackedEvent;
import com.mirkamolcode.event.SubscriptionStatusChangedEvent;
import com.mirkamolcode.model.StatusChangeReason;
import com.mirkamolcode.model.StatusChangeSource;
import com.mirkamolcode.notification.NotificationDispatcher;
import com.mirkamolcode.notification.NotificationMessageFactory;
import com.mirkamolcode.repository.UserRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationEventListener {
    private final NotificationDispatcher dispatcher;
    private final NotificationMessageFactory messages;
    private final UserRepository users;
    private final Clock clock;
    private final Duration emailEvidenceMaxAge;
    private final Duration systemEvidenceMaxAge;

    public NotificationEventListener(NotificationDispatcher dispatcher,
                                     NotificationMessageFactory messages,
                                     UserRepository users,
                                     Clock clock,
                                     @Value("${app.notifications.status-max-age}") Duration statusMaxAge,
                                     @Value("${app.status.overdue-grace-days}") int overdueGraceDays) {
        this.dispatcher = dispatcher;
        this.messages = messages;
        this.users = users;
        this.clock = clock;
        this.emailEvidenceMaxAge = statusMaxAge;
        this.systemEvidenceMaxAge = statusMaxAge.plusDays(overdueGraceDays + 1L);
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatusChanged(SubscriptionStatusChangedEvent event) {
        if (event.reason() == StatusChangeReason.USER || isStale(event))
            return;
        ZoneId zone = users.findById(event.userId()).map(User::getTimeZone).orElse(ZoneId.of(User.DEFAULT_TIME_ZONE));
        String dedupeKey = "STATUS:" + event.subscriptionId() + ":" + event.toStatus() + ":" + event.reason()
                + ":" + event.effectiveAt().getEpochSecond();
        dispatcher.dispatch(event.userId(), messages.statusChanged(event, zone), dedupeKey);
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRetracked(SubscriptionRetrackedEvent event) {
        LocalDate oldestRecentDay = LocalDate.ofInstant(clock.instant().minus(emailEvidenceMaxAge), ZoneOffset.UTC);
        if (event.chargedOn().isBefore(oldestRecentDay))
            return;
        dispatcher.dispatch(event.userId(), messages.retracked(event.subscriptionId(), event.merchantName(), event.chargedOn()),
                "RETRACKED:" + event.subscriptionId() + ":" + event.chargedOn());
    }

    private boolean isStale(SubscriptionStatusChangedEvent event) {
        Duration maxAge = event.reason().source() == StatusChangeSource.EMAIL ? emailEvidenceMaxAge : systemEvidenceMaxAge;
        return event.effectiveAt().isBefore(clock.instant().minus(maxAge));
    }
}
