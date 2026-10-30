package com.mirkamolcode.notification;

import com.mirkamolcode.entity.NotificationSettings;
import com.mirkamolcode.entity.User;
import com.mirkamolcode.model.NotificationChannelType;

public interface NotificationChannel {
    NotificationChannelType type();

    boolean isEnabled(NotificationSettings settings);

    void send(User user, NotificationMessage message);
}
