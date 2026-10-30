package com.mirkamolcode.service;

import com.mirkamolcode.detection.BillingEmailAnalyzer;
import com.mirkamolcode.detection.GmailQuery;
import com.mirkamolcode.dto.response.GmailSyncStatusResponse;
import com.mirkamolcode.entity.GmailConnection;
import com.mirkamolcode.event.GmailSyncRequestedEvent;
import com.mirkamolcode.exception.GmailDisconnectedException;
import com.mirkamolcode.exception.SyncAlreadyRunningException;
import com.mirkamolcode.exception.SyncClaimLostException;
import com.mirkamolcode.integration.gmail.GmailClient;
import com.mirkamolcode.integration.gmail.GmailHistoryExpiredException;
import com.mirkamolcode.integration.gmail.GmailMessage;
import com.mirkamolcode.integration.gmail.GmailNotFoundException;
import com.mirkamolcode.integration.gmail.GmailUnauthorizedException;
import com.mirkamolcode.integration.google.GoogleTokenService;
import com.mirkamolcode.model.SyncError;
import com.mirkamolcode.model.SyncState;
import com.mirkamolcode.repository.BillingEmailRepository;
import com.mirkamolcode.repository.GmailConnectionRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GmailSyncService {
    private static final Duration FALLBACK_OVERLAP = Duration.ofDays(1);
    private static final Logger log = LoggerFactory.getLogger(GmailSyncService.class);

    private final GmailConnectionRepository connections;
    private final BillingEmailRepository billingEmails;
    private final GmailSyncStateStore syncState;
    private final FailedGmailMessageTracker failedMessages;
    private final BillingEmailRecorder recorder;
    private final BillingEmailAnalyzer analyzer;
    private final GmailClient gmail;
    private final GoogleTokenService tokens;
    private final GmailQuery query;
    private final CurrentUserService currentUser;
    private final ApplicationEventPublisher events;

    public GmailSyncService(GmailConnectionRepository connections,
                            BillingEmailRepository billingEmails,
                            GmailSyncStateStore syncState,
                            FailedGmailMessageTracker failedMessages,
                            BillingEmailRecorder recorder,
                            BillingEmailAnalyzer analyzer,
                            GmailClient gmail,
                            GoogleTokenService tokens,
                            GmailQuery query,
                            CurrentUserService currentUser,
                            ApplicationEventPublisher events) {
        this.connections = connections;
        this.billingEmails = billingEmails;
        this.syncState = syncState;
        this.failedMessages = failedMessages;
        this.recorder = recorder;
        this.analyzer = analyzer;
        this.gmail = gmail;
        this.tokens = tokens;
        this.query = query;
        this.currentUser = currentUser;
        this.events = events;
    }

    @Transactional
    public GmailSyncStatusResponse requestSync() {
        Long userId = currentUser.requiredUser().getId();
        GmailConnection connection = connections.findByUserId(userId)
                .filter(GmailConnection::isConnected)
                .orElseThrow(() -> new GmailDisconnectedException("Connect your Gmail account before syncing"));
        SyncClaim claim = syncState.claim(userId).orElseThrow(SyncAlreadyRunningException::new);
        events.publishEvent(new GmailSyncRequestedEvent(userId, claim.claimedAt()));
        return new GmailSyncStatusResponse(SyncState.RUNNING, 0, 0, connection.getLastSyncedAt(), null);
    }

    @Transactional(readOnly = true)
    public GmailSyncStatusResponse findStatus() {
        return connections.findByUserId(currentUser.requiredUser().getId())
                .map(GmailSyncService::mapToGmailSyncStatusResponse)
                .orElseThrow(() -> new GmailDisconnectedException("Gmail is not connected"));
    }

    public boolean sync(Long userId) {
        return syncState.claim(userId)
                .map(claim -> {
                    runClaimed(claim);
                    return true;
                })
                .orElse(false);
    }

    public void runClaimed(SyncClaim claim) {
        Long userId = claim.userId();
        try {
            GmailConnection connection = connections.findByUserId(userId).orElseThrow();
            String latestHistoryId = withAccessToken(userId, gmail::fetchCurrentHistoryId);
            CandidateScan scan = findCandidateIds(userId, connection);
            syncState.recordTotal(claim, scan.ids().size());
            int failures = scan.failures() + recordAll(claim, scan.ids());
            syncState.complete(claim, latestHistoryId, failures == 0 ? null : SyncError.SOME_EMAILS_FAILED);
        } catch (SyncClaimLostException e) {
            log.warn("Gmail sync for user {} stopped: a newer run took over", userId);
        } catch (GmailDisconnectedException e) {
            log.info("Gmail sync for user {} stopped: reconnect required", userId);
            syncState.fail(claim, SyncError.RECONNECT_REQUIRED);
        } catch (RuntimeException e) {
            log.error("Gmail sync for user {} failed", userId, e);
            syncState.fail(claim, SyncError.SYNC_FAILED);
        }
    }

    private CandidateScan findCandidateIds(Long userId, GmailConnection connection) {
        Set<String> ids = new LinkedHashSet<>(failedMessages.findRetryableIds(userId));
        if (connection.getHistoryId() == null) {
            ids.addAll(listMessageIds(userId, query.buildLookbackQuery()));
            return new CandidateScan(List.copyOf(ids), 0);
        }
        try {
            CandidateScan fromHistory = findNewCandidateIds(userId, connection.getHistoryId());
            ids.addAll(fromHistory.ids());
            return new CandidateScan(List.copyOf(ids), fromHistory.failures());
        } catch (GmailHistoryExpiredException e) {
            log.info("Gmail history for user {} expired; listing mail since last sync", userId);
            Instant since = connection.getLastSyncedAt().minus(FALLBACK_OVERLAP);
            ids.addAll(listMessageIds(userId, query.buildReceivedAfterQuery(since)));
            return new CandidateScan(List.copyOf(ids), 0);
        }
    }

    private List<String> listMessageIds(Long userId, String gmailQuery) {
        return collectAllPages(pageToken -> {
            var page = withAccessToken(userId, accessToken -> gmail.listMessageIds(accessToken, gmailQuery, pageToken));
            return new Page(page.messageIds(), page.nextPageToken());
        });
    }

    private CandidateScan findNewCandidateIds(Long userId, String startHistoryId) {
        List<String> added = collectAllPages(pageToken -> {
            var page = withAccessToken(userId, accessToken -> gmail.listHistory(accessToken, startHistoryId, pageToken));
            return new Page(page.addedMessageIds(), page.nextPageToken());
        });
        List<String> candidates = new ArrayList<>();
        int failures = 0;
        for (String id : added) {
            if (billingEmails.existsByUserIdAndGmailMessageId(userId, id))
                continue;
            try {
                GmailMessage headers = withAccessToken(userId, accessToken -> gmail.getMessageHeaders(accessToken, id));
                if (analyzer.isCandidate(headers))
                    candidates.add(id);
            } catch (GmailDisconnectedException e) {
                throw e;
            } catch (GmailNotFoundException e) {
                log.debug("Gmail message {} disappeared before sync", id);
            } catch (RuntimeException e) {
                log.warn("Could not read headers of Gmail message {} for user {}: {}", id, userId, e.getMessage());
                failedMessages.recordFailure(userId, id, e.getMessage());
                failures++;
            }
        }
        return new CandidateScan(candidates, failures);
    }

    private int recordAll(SyncClaim claim, List<String> candidateIds) {
        Long userId = claim.userId();
        int processed = 0;
        int failures = 0;
        for (String id : candidateIds) {
            if (!billingEmails.existsByUserIdAndGmailMessageId(userId, id) && !recordOne(userId, id))
                failures++;
            syncState.recordProcessed(claim, ++processed);
        }
        return failures;
    }

    private boolean recordOne(Long userId, String id) {
        try {
            GmailMessage message = withAccessToken(userId, accessToken -> gmail.getMessage(accessToken, id));
            recorder.record(userId, message);
            failedMessages.clearFailure(userId, id);
            return true;
        } catch (GmailDisconnectedException | SyncClaimLostException e) {
            throw e;
        } catch (GmailNotFoundException e) {
            log.debug("Gmail message {} disappeared before sync", id);
            failedMessages.clearFailure(userId, id);
            return true;
        } catch (RuntimeException e) {
            log.warn("Could not record Gmail message {} for user {}: {}", id, userId, e.getMessage());
            failedMessages.recordFailure(userId, id, e.getMessage());
            return false;
        }
    }

    private static List<String> collectAllPages(Function<String, Page> fetchPage) {
        List<String> ids = new ArrayList<>();
        String pageToken = null;
        do {
            Page page = fetchPage.apply(pageToken);
            ids.addAll(page.ids());
            pageToken = page.nextPageToken();
        } while (pageToken != null);
        return ids;
    }

    private <T> T withAccessToken(Long userId, Function<String, T> call) {
        try {
            return call.apply(tokens.accessToken(userId));
        } catch (GmailUnauthorizedException rejected) {
            tokens.invalidate(userId);
            try {
                return call.apply(tokens.accessToken(userId));
            } catch (GmailUnauthorizedException stillRejected) {
                tokens.markDisconnected(userId);
                throw new GmailDisconnectedException("Gmail rejected a freshly refreshed access token");
            }
        }
    }

    private static GmailSyncStatusResponse mapToGmailSyncStatusResponse(GmailConnection connection) {
        return new GmailSyncStatusResponse(connection.getSyncState(), connection.getSyncProcessed(), connection.getSyncTotal(),
                connection.getLastSyncedAt(), connection.getSyncError());
    }

    private record Page(List<String> ids, String nextPageToken) {
    }

    private record CandidateScan(List<String> ids, int failures) {
    }
}
