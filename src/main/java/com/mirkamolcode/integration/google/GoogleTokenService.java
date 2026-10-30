package com.mirkamolcode.integration.google;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mirkamolcode.entity.GmailConnection;
import com.mirkamolcode.exception.GmailDisconnectedException;
import com.mirkamolcode.repository.GmailConnectionRepository;
import com.mirkamolcode.security.TokenCipher;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleTokenService {
    private static final Duration EXPIRY_SKEW = Duration.ofSeconds(60);
    private static final Duration MAX_TOKEN_LIFETIME = Duration.ofHours(1);
    private static final long MAX_CACHED_USERS = 10_000;

    private final GmailConnectionRepository connections;
    private final GoogleOAuthClient oauthClient;
    private final TokenCipher cipher;
    private final Clock clock;
    private final Cache<Long, CachedAccessToken> cache = Caffeine.newBuilder()
            .maximumSize(MAX_CACHED_USERS)
            .expireAfterWrite(MAX_TOKEN_LIFETIME)
            .build();

    public GoogleTokenService(GmailConnectionRepository connections, GoogleOAuthClient oauthClient, TokenCipher cipher, Clock clock) {
        this.connections = connections;
        this.oauthClient = oauthClient;
        this.cipher = cipher;
        this.clock = clock;
    }

    @Transactional(noRollbackFor = GmailDisconnectedException.class)
    public String accessToken(Long userId) {
        GmailConnection connection = connections.findByUserId(userId)
                .filter(GmailConnection::isConnected)
                .orElseThrow(GoogleTokenService::reconnectRequired);

        CachedAccessToken cached = cache.getIfPresent(userId);
        if (cached != null && cached.isValidFor(connection, clock.instant()))
            return cached.value();

        try {
            GoogleTokens tokens = oauthClient.refreshAccessToken(cipher.decrypt(connection.getEncryptedRefreshToken()));
            Instant validUntil = clock.instant().plus(tokens.expiresIn()).minus(EXPIRY_SKEW);
            cache.put(userId, new CachedAccessToken(tokens.accessToken(), connection.getEncryptedRefreshToken(), validUntil));
            return tokens.accessToken();
        } catch (InvalidGrantException e) {
            cache.invalidate(userId);
            connection.disconnect();
            connections.save(connection);
            throw reconnectRequired();
        }
    }

    public void invalidate(Long userId) {
        cache.invalidate(userId);
    }

    @Transactional
    public void markDisconnected(Long userId) {
        cache.invalidate(userId);
        connections.findByUserId(userId).ifPresent(connection -> {
            connection.disconnect();
            connections.save(connection);
        });
    }

    private static GmailDisconnectedException reconnectRequired() {
        return new GmailDisconnectedException("Gmail access was revoked or expired; please reconnect your Google account");
    }

    private record CachedAccessToken(String value, String issuedForRefreshToken, Instant validUntil) {
        boolean isValidFor(GmailConnection connection, Instant now) {
            return issuedForRefreshToken.equals(connection.getEncryptedRefreshToken()) && now.isBefore(validUntil);
        }
    }
}
