package com.mirkamolcode.controller;

import com.mirkamolcode.dto.request.PushSubscriptionRequest;
import com.mirkamolcode.dto.request.PushUnsubscribeRequest;
import com.mirkamolcode.dto.response.VapidKeyResponse;
import com.mirkamolcode.service.PushSubscriptionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/push")
public class PushController {
    private final PushSubscriptionService service;

    public PushController(PushSubscriptionService service) {
        this.service = service;
    }

    @GetMapping("/vapid-public-key")
    @PreAuthorize("hasAuthority('NOTIFICATION_MANAGE_OWN')")
    public VapidKeyResponse publicKey() {
        return service.findPublicKey();
    }

    @PostMapping("/subscriptions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('NOTIFICATION_MANAGE_OWN')")
    public void subscribe(@Valid @RequestBody PushSubscriptionRequest request) {
        service.subscribe(request);
    }

    @DeleteMapping("/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('NOTIFICATION_MANAGE_OWN')")
    public void unsubscribe(@Valid @RequestBody PushUnsubscribeRequest request) {
        service.unsubscribe(request.endpoint());
    }
}
