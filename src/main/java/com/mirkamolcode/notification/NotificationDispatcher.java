package com.mirkamolcode.notification;

import com.mirkamolcode.entity.NotificationSettings;
import com.mirkamolcode.entity.Subscription;
import com.mirkamolcode.repository.SubscriptionRepository;
import com.mirkamolcode.service.NotificationSettingsService;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class NotificationDispatcher {
    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final List<NotificationChannel> channels;
    private final NotificationDeliverer deliverer;
    private final NotificationSettingsService settings;
    private final SubscriptionRepository subscriptions;

    public NotificationDispatcher(List<NotificationChannel> channels, NotificationDeliverer deliverer,
                                  NotificationSettingsService settings, SubscriptionRepository subscriptions) {
        this.channels = List.copyOf(channels);
        this.deliverer = deliverer;
        this.settings = settings;
        this.subscriptions = subscriptions;
    }

    public void dispatch(Long userId, NotificationMessage message, String dedupeKey) {
        if (isAboutHiddenSubscription(message))
            return;
        NotificationSettings userSettings = settings.findOrDefault(userId);
        for (NotificationChannel channel : channels) {
            if (!channel.isEnabled(userSettings) || !claim(userId, dedupeKey, channel))
                continue;
            try {
                deliverer.deliver(userId, channel, message);
            } catch (RuntimeException e) {
                log.warn("{} notification {} for user {} failed: {}", channel.type(), dedupeKey, userId, e.getMessage());
            }
        }
    }

    private boolean claim(Long userId, String dedupeKey, NotificationChannel channel) {
        try {
            return deliverer.claimDelivery(userId, dedupeKey, channel);
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }

    private boolean isAboutHiddenSubscription(NotificationMessage message) {
        return message.subscriptionId() != null
                && subscriptions.findById(message.subscriptionId()).map(Subscription::isHidden).orElse(false);
    }
}
