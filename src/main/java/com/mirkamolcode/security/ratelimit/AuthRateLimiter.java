package com.mirkamolcode.security.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class AuthRateLimiter {

    private final boolean enabled;
    private final int maxFailedAttempts;
    private final long blockDurationSeconds;

    private final Cache<String, AtomicInteger> attemptsCache;
    private final Cache<String, Instant> blockedCache;

    public AuthRateLimiter(
            @Value("${app.rate-limiting.auth.enabled:true}") boolean enabled,
            @Value("${app.rate-limiting.auth.max-failed-attempts:3}") int maxFailedAttempts,
            @Value("${app.rate-limiting.auth.block-duration-seconds:60}") long blockDurationSeconds) {
        this.enabled = enabled;
        this.maxFailedAttempts = maxFailedAttempts;
        this.blockDurationSeconds = blockDurationSeconds;

        this.attemptsCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(15))
                .maximumSize(10_000)
                .build();

        this.blockedCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(15))
                .maximumSize(10_000)
                .build();
    }

    public boolean isBlocked(String key) {
        if (!enabled || key == null) {
            return false;
        }
        Instant blockedUntil = blockedCache.getIfPresent(key);
        if (blockedUntil != null) {
            if (Instant.now().isBefore(blockedUntil)) {
                return true;
            } else {
                blockedCache.invalidate(key);
                attemptsCache.invalidate(key);
            }
        }
        return false;
    }

    public Instant getBlockedUntil(String key) {
        if (key == null) return null;
        return blockedCache.getIfPresent(key);
    }

    public long getRemainingBlockSeconds(String key) {
        if (key == null) return blockDurationSeconds;
        Instant blockedUntil = blockedCache.getIfPresent(key);
        if (blockedUntil != null) {
            long remaining = Duration.between(Instant.now(), blockedUntil).toSeconds();
            return Math.max(1, remaining);
        }
        return blockDurationSeconds;
    }

    public void recordSuccess(String key) {
        if (!enabled || key == null) return;
        attemptsCache.invalidate(key);
        blockedCache.invalidate(key);
    }

    public void recordFailure(String key) {
        if (!enabled || key == null) return;
        AtomicInteger attempts = attemptsCache.get(key, k -> new AtomicInteger(0));
        if (attempts != null) {
            int count = attempts.incrementAndGet();
            if (count >= maxFailedAttempts) {
                blockedCache.put(key, Instant.now().plusSeconds(blockDurationSeconds));
                attemptsCache.invalidate(key);
            }
        }
    }

    public int getFailedAttempts(String key) {
        if (key == null) return 0;
        AtomicInteger attempts = attemptsCache.getIfPresent(key);
        return attempts != null ? attempts.get() : 0;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getMaxFailedAttempts() {
        return maxFailedAttempts;
    }

    public long getBlockDurationSeconds() {
        return blockDurationSeconds;
    }
}
