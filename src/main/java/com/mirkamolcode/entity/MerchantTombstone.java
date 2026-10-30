package com.mirkamolcode.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "merchant_tombstones")
public class MerchantTombstone extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 120)
    private String merchantKey;

    @Column(nullable = false)
    private Instant deletedAt;

    protected MerchantTombstone() {
    }

    public MerchantTombstone(User user, String merchantKey, Instant deletedAt) {
        this.user = user;
        this.merchantKey = merchantKey;
        this.deletedAt = deletedAt;
    }

    public void markDeletedAgain(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public boolean covers(Instant receivedAt) {
        return receivedAt.isBefore(deletedAt);
    }
}
