package com.mirkamolcode.service;

import com.mirkamolcode.dto.response.NotificationResponse;
import com.mirkamolcode.dto.response.ReadAllResponse;
import com.mirkamolcode.dto.response.UnreadCountResponse;
import com.mirkamolcode.entity.Notification;
import com.mirkamolcode.exception.NotFoundException;
import com.mirkamolcode.repository.NotificationRepository;

import java.time.Clock;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class NotificationInboxService {
    private final NotificationRepository notifications;
    private final CurrentUserService currentUser;
    private final Clock clock;

    public NotificationInboxService(NotificationRepository notifications, CurrentUserService currentUser, Clock clock) {
        this.notifications = notifications;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    public Page<NotificationResponse> list(Pageable pageable) {
        return notifications.findByUserId(findCurrentUserId(), pageable)
                .map(NotificationInboxService::mapToNotificationResponse);
    }

    public UnreadCountResponse countUnread() {
        return new UnreadCountResponse(notifications.countByUserIdAndReadAtIsNull(findCurrentUserId()));
    }

    @Transactional
    public NotificationResponse markRead(Long id) {
        Notification notification = notifications.findByIdAndUserId(id, findCurrentUserId())
                .orElseThrow(() -> new NotFoundException("Notification not found"));
        notification.markRead(clock.instant());
        return mapToNotificationResponse(notification);
    }

    @Transactional
    public ReadAllResponse markAllRead() {
        return new ReadAllResponse(notifications.markAllRead(findCurrentUserId(), clock.instant()));
    }

    private Long findCurrentUserId() {
        return currentUser.requiredUser().getId();
    }

    private static NotificationResponse mapToNotificationResponse(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getType(), notification.getSeverity(),
                notification.getTitle(), notification.getBody(), notification.getSubscriptionId(), notification.getLink(),
                notification.isRead(), notification.getCreatedAt());
    }
}
