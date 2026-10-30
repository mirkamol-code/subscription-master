package com.mirkamolcode.entity;

import com.mirkamolcode.model.GmailConnectionStatus;
import jakarta.persistence.*;

@Entity
@Table(name = "gmail_connections")
public class GmailConnection extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, length = 254)
    private String gmailAddress;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String encryptedRefreshToken;

    @Column(nullable = false, length = 1000)
    private String grantedScopes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private GmailConnectionStatus status;

    @Version
    private long version;

    protected GmailConnection() {
    }

    public GmailConnection(User user, String gmailAddress, String encryptedRefreshToken, String grantedScopes) {
        this.user = user;
        reconnect(gmailAddress, encryptedRefreshToken, grantedScopes);
    }

    public void reconnect(String gmailAddress, String encryptedRefreshToken, String grantedScopes) {
        this.gmailAddress = gmailAddress;
        this.encryptedRefreshToken = encryptedRefreshToken;
        this.grantedScopes = grantedScopes;
        this.status = GmailConnectionStatus.CONNECTED;
    }

    public void disconnect() {
        status = GmailConnectionStatus.DISCONNECTED;
    }

    public boolean isConnected() {
        return status == GmailConnectionStatus.CONNECTED;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getGmailAddress() {
        return gmailAddress;
    }

    public String getEncryptedRefreshToken() {
        return encryptedRefreshToken;
    }

    public String getGrantedScopes() {
        return grantedScopes;
    }

    public GmailConnectionStatus getStatus() {
        return status;
    }
}
