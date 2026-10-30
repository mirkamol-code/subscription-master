package com.mirkamolcode.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "notification_settings")
public class NotificationSettings extends AuditableEntity {
    public static final int DEFAULT_DAYS_BEFORE = 3;

    @Id
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private boolean emailEnabled = true;

    @Column(nullable = false)
    private boolean inAppEnabled = true;

    @Column(nullable = false)
    private boolean pushEnabled = false;

    @Column(nullable = false)
    private int daysBefore = DEFAULT_DAYS_BEFORE;

    @Version
    private long version;

    protected NotificationSettings() {
    }

    public NotificationSettings(User user) {
        this.user = user;
    }

    public void change(boolean emailEnabled, boolean inAppEnabled, boolean pushEnabled, int daysBefore) {
        this.emailEnabled = emailEnabled;
        this.inAppEnabled = inAppEnabled;
        this.pushEnabled = pushEnabled;
        this.daysBefore = daysBefore;
    }

    public boolean isEmailEnabled() {
        return emailEnabled;
    }

    public boolean isInAppEnabled() {
        return inAppEnabled;
    }

    public boolean isPushEnabled() {
        return pushEnabled;
    }

    public int getDaysBefore() {
        return daysBefore;
    }
}
