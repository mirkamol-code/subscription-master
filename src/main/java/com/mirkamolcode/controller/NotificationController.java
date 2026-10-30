package com.mirkamolcode.controller;

import com.mirkamolcode.dto.response.NotificationResponse;
import com.mirkamolcode.dto.response.ReadAllResponse;
import com.mirkamolcode.dto.response.UnreadCountResponse;
import com.mirkamolcode.service.NotificationInboxService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/notifications")
public class NotificationController {
    private final NotificationInboxService service;

    public NotificationController(NotificationInboxService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('NOTIFICATION_MANAGE_OWN')")
    public Page<NotificationResponse> list(@ParameterObject @PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/unread-count")
    @PreAuthorize("hasAuthority('NOTIFICATION_MANAGE_OWN')")
    public UnreadCountResponse unreadCount() {
        return service.countUnread();
    }

    @PostMapping("/{id}/read")
    @PreAuthorize("hasAuthority('NOTIFICATION_MANAGE_OWN')")
    public NotificationResponse markRead(@PathVariable Long id) {
        return service.markRead(id);
    }

    @PostMapping("/read-all")
    @PreAuthorize("hasAuthority('NOTIFICATION_MANAGE_OWN')")
    public ReadAllResponse markAllRead() {
        return service.markAllRead();
    }
}
