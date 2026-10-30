package com.mirkamolcode.service;

import com.mirkamolcode.dto.request.PushSubscriptionRequest;
import com.mirkamolcode.dto.response.VapidKeyResponse;
import com.mirkamolcode.entity.PushSubscription;
import com.mirkamolcode.entity.User;
import com.mirkamolcode.integration.push.PushSender;
import com.mirkamolcode.repository.PushSubscriptionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PushSubscriptionService {
    private final PushSubscriptionRepository pushSubscriptions;
    private final PushSender pushSender;
    private final CurrentUserService currentUser;

    public PushSubscriptionService(PushSubscriptionRepository pushSubscriptions, PushSender pushSender, CurrentUserService currentUser) {
        this.pushSubscriptions = pushSubscriptions;
        this.pushSender = pushSender;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public VapidKeyResponse findPublicKey() {
        return new VapidKeyResponse(pushSender.publicKey());
    }

    public void subscribe(PushSubscriptionRequest request) {
        User user = currentUser.requiredUser();
        PushSubscription subscription = pushSubscriptions.findByEndpoint(request.endpoint())
                .map(existing -> {
                    existing.reassign(user, request.keys().p256dh(), request.keys().auth());
                    return existing;
                })
                .orElseGet(() -> new PushSubscription(user, request.endpoint(), request.keys().p256dh(), request.keys().auth()));
        pushSubscriptions.save(subscription);
    }

    public void unsubscribe(String endpoint) {
        pushSubscriptions.deleteByUserIdAndEndpoint(currentUser.requiredUser().getId(), endpoint);
    }
}
