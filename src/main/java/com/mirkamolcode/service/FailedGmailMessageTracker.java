package com.mirkamolcode.service;

import com.mirkamolcode.entity.FailedGmailMessage;
import com.mirkamolcode.repository.FailedGmailMessageRepository;
import com.mirkamolcode.repository.UserRepository;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class FailedGmailMessageTracker {
    static final int MAX_ATTEMPTS = 3;

    private final FailedGmailMessageRepository failures;
    private final UserRepository users;

    public FailedGmailMessageTracker(FailedGmailMessageRepository failures, UserRepository users) {
        this.failures = failures;
        this.users = users;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<String> findRetryableIds(Long userId) {
        return failures.findByUserIdAndAttemptsLessThanOrderById(userId, MAX_ATTEMPTS).stream()
                .map(FailedGmailMessage::getGmailMessageId)
                .toList();
    }

    public void recordFailure(Long userId, String gmailMessageId, String error) {
        FailedGmailMessage failure = failures.findByUserIdAndGmailMessageId(userId, gmailMessageId)
                .orElseGet(() -> new FailedGmailMessage(users.getReferenceById(userId), gmailMessageId));
        failure.recordAttempt(error);
        failures.save(failure);
    }

    public void clearFailure(Long userId, String gmailMessageId) {
        failures.deleteByUserIdAndGmailMessageId(userId, gmailMessageId);
    }
}
