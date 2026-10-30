package com.mirkamolcode.service;

import com.mirkamolcode.dto.response.DisconnectGmailResponse;
import com.mirkamolcode.entity.GmailConnection;
import com.mirkamolcode.integration.google.GoogleOAuthClient;
import com.mirkamolcode.integration.google.GoogleTokenService;
import com.mirkamolcode.repository.GmailConnectionRepository;
import com.mirkamolcode.security.TokenCipher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AccountService {
    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final CurrentUserService currentUser;
    private final GmailConnectionRepository connections;
    private final GoogleOAuthClient oauthClient;
    private final GoogleTokenService tokens;
    private final TokenCipher cipher;
    private final AccountDataPurger purger;

    public AccountService(CurrentUserService currentUser,
                          GmailConnectionRepository connections,
                          GoogleOAuthClient oauthClient,
                          GoogleTokenService tokens,
                          TokenCipher cipher,
                          AccountDataPurger purger) {
        this.currentUser = currentUser;
        this.connections = connections;
        this.oauthClient = oauthClient;
        this.tokens = tokens;
        this.cipher = cipher;
        this.purger = purger;
    }

    public DisconnectGmailResponse disconnectGmail() {
        Long userId = currentUser.requiredUser().getId();
        boolean revoked = revokeGoogleAccess(userId);
        purger.deleteUserData(userId);
        tokens.invalidate(userId);
        return new DisconnectGmailResponse(revoked);
    }

    public void deleteAccount() {
        Long userId = currentUser.requiredUser().getId();
        revokeGoogleAccess(userId);
        purger.deleteAccount(userId);
        tokens.invalidate(userId);
    }

    private boolean revokeGoogleAccess(Long userId) {
        return connections.findByUserId(userId)
                .map(GmailConnection::getEncryptedRefreshToken)
                .map(encrypted -> revokeStoredToken(userId, encrypted))
                .orElse(true);
    }

    private boolean revokeStoredToken(Long userId, String encryptedRefreshToken) {
        boolean revoked;
        try {
            revoked = oauthClient.revoke(cipher.decrypt(encryptedRefreshToken));
        } catch (IllegalStateException e) {
            revoked = false;
        }
        if (!revoked)
            log.warn("Google access for user {} could not be revoked; local data is deleted anyway", userId);
        return revoked;
    }
}
