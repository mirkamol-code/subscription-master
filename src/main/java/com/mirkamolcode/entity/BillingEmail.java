package com.mirkamolcode.entity;

import com.mirkamolcode.detection.Confidence;
import com.mirkamolcode.detection.EmailKind;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "billing_emails")
public class BillingEmail extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id")
    private Subscription subscription;

    @Column(nullable = false, length = 64)
    private String gmailMessageId;

    @Column(length = 64)
    private String threadId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EmailKind kind;

    @Column(length = 320)
    private String sender;

    @Column(length = 500)
    private String subject;

    @Column(nullable = false)
    private Instant receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 8)
    private Confidence confidence;

    @Column(length = 120)
    private String merchantKey;

    private LocalDate accessEndsOn;

    @OneToMany(mappedBy = "billingEmail", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EmailAttachment> attachments = new ArrayList<>();

    protected BillingEmail() {
    }

    private BillingEmail(User user, String gmailMessageId, String threadId, EmailKind kind, String sender, String subject,
                         Instant receivedAt, Confidence confidence) {
        this.user = user;
        this.gmailMessageId = gmailMessageId;
        this.threadId = threadId;
        this.kind = kind;
        this.sender = sender;
        this.subject = subject;
        this.receivedAt = receivedAt;
        this.confidence = confidence;
    }

    public static BillingEmail relevant(User user, String gmailMessageId, String threadId, EmailKind kind, String sender,
                                        String subject, Instant receivedAt, Confidence confidence,
                                        String merchantKey, LocalDate accessEndsOn) {
        BillingEmail email = new BillingEmail(user, gmailMessageId, threadId, kind, truncate(sender, 320), truncate(subject, 500),
                receivedAt, confidence);
        email.merchantKey = merchantKey;
        email.accessEndsOn = accessEndsOn;
        return email;
    }

    public static BillingEmail irrelevant(User user, String gmailMessageId, Instant receivedAt) {
        return new BillingEmail(user, gmailMessageId, null, EmailKind.IRRELEVANT, null, null, receivedAt, null);
    }

    public void attachTo(Subscription subscription) {
        this.subscription = subscription;
    }

    public void addAttachment(String gmailAttachmentId, String filename, String mimeType, long sizeBytes) {
        attachments.add(new EmailAttachment(this, gmailAttachmentId, truncate(filename, 255), mimeType, sizeBytes));
    }

    private static String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public Long getId() {
        return id;
    }

    public Subscription getSubscription() {
        return subscription;
    }

    public String getGmailMessageId() {
        return gmailMessageId;
    }

    public EmailKind getKind() {
        return kind;
    }

    public String getSender() {
        return sender;
    }

    public String getSubject() {
        return subject;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Confidence getConfidence() {
        return confidence;
    }

    public LocalDate getAccessEndsOn() {
        return accessEndsOn;
    }

    public List<EmailAttachment> getAttachments() {
        return List.copyOf(attachments);
    }
}
