package com.mirkamolcode.notification;

import com.mirkamolcode.entity.Notification;
import com.mirkamolcode.entity.NotificationSettings;
import com.mirkamolcode.entity.User;
import com.mirkamolcode.model.NotificationChannelType;
import com.mirkamolcode.repository.NotificationRepository;

import org.springframework.stereotype.Component;

@Component
public class InAppChannel implements NotificationChannel {
    private final NotificationRepository notifications;

    public InAppChannel(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @Override
    public NotificationChannelType type() {
        return NotificationChannelType.IN_APP;
    }

    @Override
    public boolean isEnabled(NotificationSettings settings) {
        return settings.isInAppEnabled();
    }

    @Override
    public void send(User user, NotificationMessage message) {
        notifications.save(new Notification(user, message.subscriptionId(), message.type(), message.severity(),
                message.title(), message.body(), message.link()));
    }
}
