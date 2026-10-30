package com.mirkamolcode.service;

import com.mirkamolcode.integration.google.GoogleOAuthClient;
import com.mirkamolcode.integration.google.GoogleTokens;
import com.mirkamolcode.integration.google.InvalidGrantException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

public final class FakeGoogleOAuth implements GoogleOAuthClient {
    private final AtomicInteger refreshes = new AtomicInteger();
    private volatile int revokeAfterRefreshes = -1;

    public void reset() {
        refreshes.set(0);
        revokeAfterRefreshes = -1;
    }

    void revokeAfterRefreshes(int count) {
        revokeAfterRefreshes = count;
    }

    @Override
    public GoogleTokens refreshAccessToken(String refreshToken) {
        if (revokeAfterRefreshes >= 0 && refreshes.get() >= revokeAfterRefreshes)
            throw new InvalidGrantException("Token has been expired or revoked.");
        refreshes.incrementAndGet();
        return new GoogleTokens("ya29.fake-" + refreshes.get(), Duration.ofHours(1));
    }
}
