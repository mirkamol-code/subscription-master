package com.mirkamolcode.repository;

import com.mirkamolcode.entity.BillingEmail;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingEmailRepository extends JpaRepository<BillingEmail, Long> {
    boolean existsByUserIdAndGmailMessageId(Long userId, String gmailMessageId);

    @EntityGraph(attributePaths = "attachments")
    List<BillingEmail> findBySubscriptionIdOrderByReceivedAtDesc(Long subscriptionId);
}
