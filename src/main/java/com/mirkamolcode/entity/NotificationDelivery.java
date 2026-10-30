package com.mirkamolcode.entity;

import com.mirkamolcode.model.NotificationChannelType;
import jakarta.persistence.*;

@Entity
@Table(name = "notification_deliveries")
public class NotificationDelivery extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 200)
    private String dedupeKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NotificationChannelType channel;

    protected NotificationDelivery() {
    }

    public NotificationDelivery(Long userId, String dedupeKey, NotificationChannelType channel) {
        this.userId = userId;
        this.dedupeKey = dedupeKey;
        this.channel = channel;
    }
}
