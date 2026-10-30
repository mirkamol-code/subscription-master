package com.mirkamolcode.notification;

import com.mirkamolcode.entity.NotificationDelivery;
import com.mirkamolcode.entity.User;
import com.mirkamolcode.repository.NotificationDeliveryRepository;
import com.mirkamolcode.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationDeliverer {
    private final NotificationDeliveryRepository deliveries;
    private final UserRepository users;

    public NotificationDeliverer(NotificationDeliveryRepository deliveries, UserRepository users) {
        this.deliveries = deliveries;
        this.users = users;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claimDelivery(Long userId, String dedupeKey, NotificationChannel channel) {
        if (deliveries.existsByUserIdAndDedupeKeyAndChannel(userId, dedupeKey, channel.type()))
            return false;
        deliveries.saveAndFlush(new NotificationDelivery(userId, dedupeKey, channel.type()));
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliver(Long userId, NotificationChannel channel, NotificationMessage message) {
        User user = users.findById(userId).orElseThrow();
        channel.send(user, message);
    }
}
