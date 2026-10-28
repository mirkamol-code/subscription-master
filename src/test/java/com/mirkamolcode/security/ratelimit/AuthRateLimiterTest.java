package com.mirkamolcode.security.ratelimit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthRateLimiterTest {

    @Test
    void recordFailure_shouldBlockIpForDuration_afterThreeFailedAttempts() {
        AuthRateLimiter limiter = new AuthRateLimiter(true, 3, 60);
        String ip = "192.168.1.50";

        limiter.recordFailure(ip);
        assertThat(limiter.isBlocked(ip)).isFalse();
        assertThat(limiter.getFailedAttempts(ip)).isEqualTo(1);

        limiter.recordFailure(ip);
        assertThat(limiter.isBlocked(ip)).isFalse();
        assertThat(limiter.getFailedAttempts(ip)).isEqualTo(2);

        limiter.recordFailure(ip);
        assertThat(limiter.isBlocked(ip)).isTrue();
        assertThat(limiter.getBlockedUntil(ip)).isNotNull();
        assertThat(limiter.getRemainingBlockSeconds(ip)).isGreaterThan(0);

        assertThat(limiter.isBlocked("192.168.1.99")).isFalse();
        assertThat(limiter.getBlockedUntil("192.168.1.99")).isNull();
    }

    @Test
    void recordSuccess_shouldResetFailedAttemptsCount() {
        AuthRateLimiter limiter = new AuthRateLimiter(true, 3, 60);
        String ip = "192.168.1.51";

        limiter.recordFailure(ip);
        limiter.recordFailure(ip);
        assertThat(limiter.getFailedAttempts(ip)).isEqualTo(2);

        limiter.recordSuccess(ip);
        assertThat(limiter.getFailedAttempts(ip)).isEqualTo(0);
        assertThat(limiter.isBlocked(ip)).isFalse();

        limiter.recordFailure(ip);
        assertThat(limiter.isBlocked(ip)).isFalse();
        assertThat(limiter.getFailedAttempts(ip)).isEqualTo(1);
    }

    @Test
    void isBlocked_shouldReturnFalse_whenDisabled() {
        AuthRateLimiter limiter = new AuthRateLimiter(false, 3, 60);
        String ip = "127.0.0.1";

        for (int i = 0; i < 10; i++) {
            limiter.recordFailure(ip);
        }

        assertThat(limiter.isBlocked(ip)).isFalse();
    }
}
