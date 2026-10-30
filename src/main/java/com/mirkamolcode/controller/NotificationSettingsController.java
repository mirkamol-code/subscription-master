package com.mirkamolcode.controller;

import com.mirkamolcode.dto.request.NotificationSettingsRequest;
import com.mirkamolcode.dto.response.NotificationSettingsResponse;
import com.mirkamolcode.service.NotificationSettingsService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/settings/notifications")
public class NotificationSettingsController {
    private final NotificationSettingsService service;

    public NotificationSettingsController(NotificationSettingsService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('NOTIFICATION_MANAGE_OWN')")
    public NotificationSettingsResponse get() {
        return service.findForCurrentUser();
    }

    @PutMapping
    @PreAuthorize("hasAuthority('NOTIFICATION_MANAGE_OWN')")
    public NotificationSettingsResponse update(@Valid @RequestBody NotificationSettingsRequest request) {
        return service.update(request);
    }
}
