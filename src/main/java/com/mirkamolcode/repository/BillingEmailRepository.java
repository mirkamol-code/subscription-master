package com.mirkamolcode.repository;

import com.mirkamolcode.detection.EmailKind;
import com.mirkamolcode.entity.BillingEmail;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BillingEmailRepository extends JpaRepository<BillingEmail, Long> {
    Optional<BillingEmail> findByIdAndSubscriptionId(Long id, Long subscriptionId);

    @EntityGraph(attributePaths = "attachments")
    Optional<BillingEmail> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndGmailMessageId(Long userId, String gmailMessageId);

    List<BillingEmail> findByUserIdAndMerchantKeyAndSubscriptionIsNullAndKindInOrderByReceivedAtAsc(
            Long userId, String merchantKey, Collection<EmailKind> kinds);

    @EntityGraph(attributePaths = "attachments")
    List<BillingEmail> findBySubscriptionIdOrderByReceivedAtDesc(Long subscriptionId);

    @Modifying
    @Query("delete from BillingEmail e where e.user.id = :userId")
    int deleteAllOfUser(@Param("userId") Long userId);
}
