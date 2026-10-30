package com.mirkamolcode.repository;

import com.mirkamolcode.entity.FailedGmailMessage;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FailedGmailMessageRepository extends JpaRepository<FailedGmailMessage, Long> {
    Optional<FailedGmailMessage> findByUserIdAndGmailMessageId(Long userId, String gmailMessageId);

    List<FailedGmailMessage> findByUserIdAndAttemptsLessThanOrderById(Long userId, int attempts);

    void deleteByUserIdAndGmailMessageId(Long userId, String gmailMessageId);
}
