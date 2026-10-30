package com.mirkamolcode.dto.response;

import com.mirkamolcode.model.SyncState;

import java.time.Instant;

public record GmailSyncStatusResponse(
        SyncState state,
        int processed,
        int total,
        Instant lastSyncedAt,
        String error
) {
}
