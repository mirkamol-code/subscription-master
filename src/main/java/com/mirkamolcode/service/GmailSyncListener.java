package com.mirkamolcode.service;

import com.mirkamolcode.event.GmailConnectedEvent;
import com.mirkamolcode.event.GmailSyncRequestedEvent;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class GmailSyncListener {
    private final GmailSyncService syncService;

    public GmailSyncListener(GmailSyncService syncService) {
        this.syncService = syncService;
    }

    @Async("gmailSyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGmailConnected(GmailConnectedEvent event) {
        syncService.sync(event.userId());
    }

    @Async("gmailSyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSyncRequested(GmailSyncRequestedEvent event) {
        syncService.runClaimed(new SyncClaim(event.userId(), event.claimedAt()));
    }
}
