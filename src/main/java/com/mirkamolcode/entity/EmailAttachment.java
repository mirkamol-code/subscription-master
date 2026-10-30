package com.mirkamolcode.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "email_attachments")
public class EmailAttachment extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "billing_email_id", nullable = false)
    private BillingEmail billingEmail;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String gmailAttachmentId;

    @Column(nullable = false)
    private String filename;

    @Column(nullable = false, length = 127)
    private String mimeType;

    @Column(nullable = false)
    private long sizeBytes;

    protected EmailAttachment() {
    }

    EmailAttachment(BillingEmail billingEmail, String gmailAttachmentId, String filename, String mimeType, long sizeBytes) {
        this.billingEmail = billingEmail;
        this.gmailAttachmentId = gmailAttachmentId;
        this.filename = filename;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
    }

    public Long getId() {
        return id;
    }

    public String getGmailAttachmentId() {
        return gmailAttachmentId;
    }

    public String getFilename() {
        return filename;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }
}
