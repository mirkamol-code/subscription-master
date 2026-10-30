package com.mirkamolcode.service;

import java.time.Instant;

public record SyncClaim(Long userId, Instant claimedAt) {
}
