package com.mirkamolcode.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "failed_gmail_messages")
public class FailedGmailMessage extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 64)
    private String gmailMessageId;

    @Column(nullable = false)
    private int attempts;

    private String lastError;

    protected FailedGmailMessage() {
    }

    public FailedGmailMessage(User user, String gmailMessageId) {
        this.user = user;
        this.gmailMessageId = gmailMessageId;
    }

    public void recordAttempt(String error) {
        attempts++;
        recordError(error);
    }

    public void recordWaitingAttempt(String error) {
        attempts = Math.max(attempts, 1);
        recordError(error);
    }

    private void recordError(String error) {
        lastError = error == null || error.length() <= 255 ? error : error.substring(0, 255);
    }

    public String getGmailMessageId() {
        return gmailMessageId;
    }

    public int getAttempts() {
        return attempts;
    }
}
