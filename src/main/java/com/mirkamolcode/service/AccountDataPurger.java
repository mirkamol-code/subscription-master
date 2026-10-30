package com.mirkamolcode.service;

import com.mirkamolcode.repository.BillingEmailRepository;
import com.mirkamolcode.repository.FailedGmailMessageRepository;
import com.mirkamolcode.repository.GmailConnectionRepository;
import com.mirkamolcode.repository.MerchantTombstoneRepository;
import com.mirkamolcode.repository.NotificationDeliveryRepository;
import com.mirkamolcode.repository.NotificationRepository;
import com.mirkamolcode.repository.PaymentHistoryRepository;
import com.mirkamolcode.repository.PushSubscriptionRepository;
import com.mirkamolcode.repository.SubscriptionRepository;
import com.mirkamolcode.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AccountDataPurger {
    private final GmailConnectionRepository connections;
    private final NotificationRepository notifications;
    private final NotificationDeliveryRepository deliveries;
    private final PushSubscriptionRepository pushSubscriptions;
    private final FailedGmailMessageRepository failedMessages;
    private final MerchantTombstoneRepository tombstones;
    private final PaymentHistoryRepository payments;
    private final BillingEmailRepository billingEmails;
    private final SubscriptionRepository subscriptions;
    private final UserRepository users;

    public AccountDataPurger(GmailConnectionRepository connections,
                             NotificationRepository notifications,
                             NotificationDeliveryRepository deliveries,
                             PushSubscriptionRepository pushSubscriptions,
                             FailedGmailMessageRepository failedMessages,
                             MerchantTombstoneRepository tombstones,
                             PaymentHistoryRepository payments,
                             BillingEmailRepository billingEmails,
                             SubscriptionRepository subscriptions,
                             UserRepository users) {
        this.connections = connections;
        this.notifications = notifications;
        this.deliveries = deliveries;
        this.pushSubscriptions = pushSubscriptions;
        this.failedMessages = failedMessages;
        this.tombstones = tombstones;
        this.payments = payments;
        this.billingEmails = billingEmails;
        this.subscriptions = subscriptions;
        this.users = users;
    }

    public void deleteUserData(Long userId) {
        connections.findLockedByUserId(userId);
        connections.deleteAllOfUser(userId);
        notifications.deleteAllOfUser(userId);
        deliveries.deleteAllOfUser(userId);
        pushSubscriptions.deleteAllOfUser(userId);
        failedMessages.deleteAllOfUser(userId);
        tombstones.deleteAllOfUser(userId);
        payments.deleteAllOfUser(userId);
        billingEmails.deleteAllOfUser(userId);
        subscriptions.deleteAllOfUser(userId);
    }

    public void deleteAccount(Long userId) {
        deleteUserData(userId);
        users.deleteById(userId);
    }
}
