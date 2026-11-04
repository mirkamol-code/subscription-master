package com.mirkamolcode.service;

import com.mirkamolcode.model.GmailConnectionStatus;
import com.mirkamolcode.repository.GmailConnectionRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class GmailSyncScheduler {
    private static final Logger log = LoggerFactory.getLogger(GmailSyncScheduler.class);

    private final GmailConnectionRepository connections;
    private final GmailSyncService syncService;

    public GmailSyncScheduler(GmailConnectionRepository connections, GmailSyncService syncService) {
        this.connections = connections;
        this.syncService = syncService;
    }

    @Scheduled(cron = "${app.gmail.sync.cron}", zone = "Asia/Tashkent")
    public void syncAllConnectedMailboxes() {
        for (Long userId : connections.findUserIdsByStatus(GmailConnectionStatus.CONNECTED)) {
            if (!syncService.sync(userId))
                log.debug("Skipping scheduled Gmail sync for user {}: already running", userId);
        }
    }
}
