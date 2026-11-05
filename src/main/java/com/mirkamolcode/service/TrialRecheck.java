package com.mirkamolcode.service;

import com.mirkamolcode.detection.BillingEmailAnalyzer;
import com.mirkamolcode.detection.EmailKind;
import com.mirkamolcode.entity.BillingEmail;
import com.mirkamolcode.integration.gmail.GmailClient;
import com.mirkamolcode.integration.gmail.GmailMessage;
import com.mirkamolcode.integration.gmail.GmailMessagePage;
import com.mirkamolcode.model.GmailConnectionStatus;
import com.mirkamolcode.repository.BillingEmailRepository;
import com.mirkamolcode.repository.GmailConnectionRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TrialRecheck {
    static final String TRIAL_QUERY = "newer_than:30d (\"free trial\" OR trial)";

    private static final Logger log = LoggerFactory.getLogger(TrialRecheck.class);

    private final GmailConnectionRepository connections;
    private final GmailAccessor gmailAccessor;
    private final GmailClient gmail;
    private final BillingEmailAnalyzer analyzer;
    private final BillingEmailRepository billingEmails;
    private final BillingEmailRecorder recorder;
    private final FailedGmailMessageTracker failedMessages;
    private final TransactionTemplate transactions;

    public TrialRecheck(GmailConnectionRepository connections,
                        GmailAccessor gmailAccessor,
                        GmailClient gmail,
                        BillingEmailAnalyzer analyzer,
                        BillingEmailRepository billingEmails,
                        BillingEmailRecorder recorder,
                        FailedGmailMessageTracker failedMessages,
                        TransactionTemplate transactions) {
        this.connections = connections;
        this.gmailAccessor = gmailAccessor;
        this.gmail = gmail;
        this.analyzer = analyzer;
        this.billingEmails = billingEmails;
        this.recorder = recorder;
        this.failedMessages = failedMessages;
        this.transactions = transactions;
    }

    public Map<Long, UserCounts> run(boolean apply) {
        Map<Long, UserCounts> counts = new TreeMap<>();
        for (Long userId : connections.findUserIdsByStatus(GmailConnectionStatus.CONNECTED)) {
            try {
                counts.put(userId, recheckUser(userId, apply));
            } catch (RuntimeException e) {
                log.warn("Trial re-check skipped user {}: {}", userId, e.getMessage());
            }
        }
        return counts;
    }

    private UserCounts recheckUser(Long userId, boolean apply) {
        int found = 0;
        int wouldRecord = 0;
        int recorded = 0;
        int skipped = 0;
        for (String messageId : listTrialMessageIds(userId)) {
            Optional<BillingEmail> stored = billingEmails.findByUserIdAndGmailMessageId(userId, messageId);
            if (stored.isPresent() && stored.get().getKind() != EmailKind.IRRELEVANT) {
                skipped++;
                continue;
            }
            GmailMessage message = gmailAccessor.callWithFreshToken(userId, token -> gmail.getMessage(token, messageId));
            if (analyzer.analyze(message).kind() != EmailKind.TRIAL_STARTED)
                continue;
            found++;
            if (!apply) {
                wouldRecord++;
                continue;
            }
            if (recordAgain(userId, message, stored))
                recorded++;
        }
        return new UserCounts(found, wouldRecord, recorded, skipped);
    }

    private boolean recordAgain(Long userId, GmailMessage message, Optional<BillingEmail> irrelevant) {
        irrelevant.ifPresent(email -> transactions.executeWithoutResult(status -> billingEmails.deleteById(email.getId())));
        try {
            recorder.record(userId, message);
            return true;
        } catch (RuntimeException e) {
            log.warn("Trial re-check could not record message {} for user {}; the next sync retries it: {}", message.id(), userId,
                    e.getMessage());
            failedMessages.recordFailure(userId, message.id(), e.getMessage());
            return false;
        }
    }

    private List<String> listTrialMessageIds(Long userId) {
        List<String> ids = new ArrayList<>();
        String pageToken = null;
        do {
            String token = pageToken;
            GmailMessagePage page = gmailAccessor.callWithFreshToken(userId, access -> gmail.listMessageIds(access, TRIAL_QUERY, token));
            ids.addAll(page.messageIds());
            pageToken = page.nextPageToken();
        } while (pageToken != null);
        return ids;
    }

    public record UserCounts(int found, int wouldRecord, int recorded, int skipped) {
    }
}
