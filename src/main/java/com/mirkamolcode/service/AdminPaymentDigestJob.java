package com.mirkamolcode.service;

import com.mirkamolcode.dto.response.PaymentDueResponse;
import com.mirkamolcode.dto.response.UpcomingPaymentsResponse;
import com.mirkamolcode.entity.User;
import com.mirkamolcode.model.DueBucket;
import com.mirkamolcode.model.DueEntryKind;
import com.mirkamolcode.notification.NotificationDispatcher;
import com.mirkamolcode.notification.NotificationMessageFactory;
import com.mirkamolcode.repository.UserRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AdminPaymentDigestJob {
    private static final Logger log = LoggerFactory.getLogger(AdminPaymentDigestJob.class);
    private static final Set<DueBucket> DUE_SOON = EnumSet.of(DueBucket.TODAY, DueBucket.NEXT_3_DAYS);

    private final UserRepository users;
    private final AdminPaymentService payments;
    private final NotificationDispatcher dispatcher;
    private final NotificationMessageFactory messages;
    private final Clock clock;
    private final int reminderHour;

    public AdminPaymentDigestJob(UserRepository users,
                                 AdminPaymentService payments,
                                 NotificationDispatcher dispatcher,
                                 NotificationMessageFactory messages,
                                 Clock clock,
                                 @Value("${app.notifications.reminder-hour}") int reminderHour) {
        this.users = users;
        this.payments = payments;
        this.dispatcher = dispatcher;
        this.messages = messages;
        this.clock = clock;
        this.reminderHour = reminderHour;
    }

    @Scheduled(cron = "${app.notifications.reminder-cron}")
    @SchedulerLock(name = "adminPaymentDigest", lockAtMostFor = "PT50M")
    public void sendDigests() {
        for (User admin : users.findAdmins()) {
            try {
                sendDigestTo(admin);
            } catch (RuntimeException e) {
                log.warn("Admin payment digest failed for user {}: {}", admin.getId(), e.getMessage());
            }
        }
    }

    private void sendDigestTo(User admin) {
        ZonedDateTime adminNow = clock.instant().atZone(admin.getTimeZone());
        if (adminNow.getHour() < reminderHour)
            return;
        UpcomingPaymentsResponse upcoming = payments.listUpcoming(admin.getTimeZone());
        List<PaymentDueResponse> dueSoon = selectPayments(upcoming, DUE_SOON);
        List<PaymentDueResponse> overdue = selectPayments(upcoming, EnumSet.of(DueBucket.OVERDUE));
        if (dueSoon.isEmpty() && overdue.isEmpty())
            return;
        dispatcher.dispatch(admin.getId(),
                messages.adminPaymentDigest(dueSoon.size(), AdminPaymentService.sumUzs(dueSoon.stream()),
                        overdue.size(), AdminPaymentService.sumUzs(overdue.stream())),
                "ADMIN_PAYMENT_DIGEST:" + adminNow.toLocalDate());
    }

    private static List<PaymentDueResponse> selectPayments(UpcomingPaymentsResponse upcoming, Set<DueBucket> buckets) {
        return upcoming.entries().stream()
                .filter(entry -> entry.kind() == DueEntryKind.PAYMENT && buckets.contains(entry.bucket()))
                .toList();
    }
}
