package com.mirkamolcode.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirkamolcode.entity.NotificationSettings;
import com.mirkamolcode.entity.PushSubscription;
import com.mirkamolcode.entity.User;
import com.mirkamolcode.integration.push.PushResult;
import com.mirkamolcode.integration.push.PushSender;
import com.mirkamolcode.integration.push.PushTarget;
import com.mirkamolcode.model.NotificationChannelType;
import com.mirkamolcode.repository.PushSubscriptionRepository;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class PushChannel implements NotificationChannel {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final PushSubscriptionRepository pushSubscriptions;
    private final PushSender pushSender;

    public PushChannel(PushSubscriptionRepository pushSubscriptions, PushSender pushSender) {
        this.pushSubscriptions = pushSubscriptions;
        this.pushSender = pushSender;
    }

    @Override
    public NotificationChannelType type() {
        return NotificationChannelType.PUSH;
    }

    @Override
    public boolean isEnabled(NotificationSettings settings) {
        return settings.isPushEnabled();
    }

    @Override
    public void send(User user, NotificationMessage message) {
        String payload = buildPayload(message);
        for (PushSubscription subscription : pushSubscriptions.findByUserId(user.getId())) {
            PushResult delivery = pushSender.send(new PushTarget(subscription.getEndpoint(), subscription.getP256dh(),
                    subscription.getAuth(), payload));
            if (delivery == PushResult.EXPIRED)
                pushSubscriptions.delete(subscription);
        }
    }

    private static String buildPayload(NotificationMessage message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", message.title());
        payload.put("body", message.body());
        payload.put("url", message.link());
        payload.put("severity", message.severity().name());
        payload.put("type", message.type().name());
        try {
            return JSON.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
