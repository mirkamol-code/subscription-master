package com.mirkamolcode.service;

import com.mirkamolcode.model.SubscriptionStatus;
import com.mirkamolcode.entity.PaymentHistory;
import com.mirkamolcode.entity.Subscription;
import com.mirkamolcode.repository.PaymentHistoryRepository;
import com.mirkamolcode.repository.SubscriptionRepository;

import java.time.LocalDate;
import java.util.List;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {
    private final SubscriptionRepository subscriptions;
    private final PaymentHistoryRepository payments;
    private final ExchangeRateService exchangeRates;
    private final List<NotificationService> notifications;

    public PaymentService(SubscriptionRepository subscriptions, PaymentHistoryRepository payments, ExchangeRateService exchangeRates, List<NotificationService> notifications) {
        this.subscriptions = subscriptions;
        this.payments = payments;
        this.exchangeRates = exchangeRates;
        this.notifications = notifications;
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Tashkent")
    @SchedulerLock(name = "paymentDueNotifications", lockAtMostFor = "PT15M")
    @Transactional
    public void notifyDuePayments() {
        subscriptions.findByIsDeletedFalseAndStatusAndNextPaymentDate(SubscriptionStatus.ACTIVE, LocalDate.now().plusDays(2))
                .forEach(subscription -> notifications.forEach(notification -> notification.paymentDueSoon(subscription)));
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Tashkent")
    @SchedulerLock(name = "recordDuePayments", lockAtMostFor = "PT30M")
    @Transactional
    public void recordDuePayments() {
        for (Subscription subscription : subscriptions.findByIsDeletedFalseAndStatusAndNextPaymentDateLessThanEqual(SubscriptionStatus.ACTIVE, LocalDate.now())) {
            payments.save(new PaymentHistory(subscription, subscription.getNextPaymentDate(), subscription.getPrice(), subscription.getCurrency(), exchangeRates.rateToUzs(subscription.getCurrency())));
            subscription.advanceNextPaymentDate();
        }
    }

    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Tashkent")
    @SchedulerLock(name = "refreshExchangeRates", lockAtMostFor = "PT30M")
    public void refreshExchangeRates() {
        exchangeRates.refreshDailyRates();
    }
}
