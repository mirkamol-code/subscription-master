package com.mirkamolcode.service;

import com.mirkamolcode.exception.SyncClaimLostException;
import com.mirkamolcode.model.SyncError;
import com.mirkamolcode.repository.GmailConnectionRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class GmailSyncStateStore {
    private final GmailConnectionRepository connections;
    private final Clock clock;
    private final Duration staleAfter;

    public GmailSyncStateStore(GmailConnectionRepository connections, Clock clock,
                               @Value("${app.gmail.sync.stale-after}") Duration staleAfter) {
        this.connections = connections;
        this.clock = clock;
        this.staleAfter = staleAfter;
    }

    public Optional<SyncClaim> claim(Long userId) {
        Instant now = now();
        if (connections.claimSync(userId, now, now.minus(staleAfter)) != 1)
            return Optional.empty();
        return Optional.of(new SyncClaim(userId, now));
    }

    public void recordTotal(SyncClaim claim, int total) {
        requireOwnership(claim, connections.updateSyncTotal(claim.userId(), claim.claimedAt(), total, now()));
    }

    public void recordProcessed(SyncClaim claim, int processed) {
        requireOwnership(claim, connections.updateSyncProcessed(claim.userId(), claim.claimedAt(), processed, now()));
    }

    public void complete(SyncClaim claim, String historyId, SyncError error) {
        requireOwnership(claim, connections.completeSync(claim.userId(), claim.claimedAt(), now(), historyId,
                error == null ? null : error.name()));
    }

    public void fail(SyncClaim claim, SyncError error) {
        connections.failSync(claim.userId(), claim.claimedAt(), error.name());
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private static void requireOwnership(SyncClaim claim, int updatedRows) {
        if (updatedRows != 1)
            throw new SyncClaimLostException(claim.userId());
    }
}
