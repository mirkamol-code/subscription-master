package com.mirkamolcode.event;

import java.time.Instant;

public record GmailSyncRequestedEvent(Long userId, Instant claimedAt) {
}
