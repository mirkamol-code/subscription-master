package com.mirkamolcode.entity;

import com.mirkamolcode.model.NotificationSeverity;
import com.mirkamolcode.model.NotificationType;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "notifications")
public class Notification extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "subscription_id")
    private Long subscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private NotificationSeverity severity;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 1000)
    private String body;

    @Column(length = 500)
    private String link;

    private Instant readAt;

    protected Notification() {
    }

    public Notification(User user, Long subscriptionId, NotificationType type, NotificationSeverity severity,
                        String title, String body, String link) {
        this.user = user;
        this.subscriptionId = subscriptionId;
        this.type = type;
        this.severity = severity;
        this.title = title;
        this.body = body;
        this.link = link;
    }

    public void markRead(Instant readAt) {
        if (this.readAt == null)
            this.readAt = readAt;
    }

    public Long getId() {
        return id;
    }

    public Long getSubscriptionId() {
        return subscriptionId;
    }

    public NotificationType getType() {
        return type;
    }

    public NotificationSeverity getSeverity() {
        return severity;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public String getLink() {
        return link;
    }

    public boolean isRead() {
        return readAt != null;
    }
}
