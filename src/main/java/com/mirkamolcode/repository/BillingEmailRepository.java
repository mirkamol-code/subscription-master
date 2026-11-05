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
    @Query("""
            select e from BillingEmail e where e.id = :id and (e.subscription.id = :subscriptionId
                or exists (select p from PaymentHistory p where p.billingEmail = e and p.subscription.id = :subscriptionId))""")
    Optional<BillingEmail> findByIdAndSubscriptionId(@Param("id") Long id, @Param("subscriptionId") Long subscriptionId);

    long countBySubscriptionIdAndRecurrenceCandidateTrue(Long subscriptionId);

    List<BillingEmail> findByUserIdAndAwaitingAccountTrueOrderByReceivedAtDesc(Long userId);

    List<BillingEmail> findBySubscriptionIdAndAccountGuessedTrue(Long subscriptionId);

    @EntityGraph(attributePaths = "attachments")
    Optional<BillingEmail> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndGmailMessageId(Long userId, String gmailMessageId);

    Optional<BillingEmail> findByUserIdAndGmailMessageId(Long userId, String gmailMessageId);

    List<BillingEmail> findByUserIdAndMerchantKeyAndSubscriptionIsNullAndKindInOrderByReceivedAtAsc(
            Long userId, String merchantKey, Collection<EmailKind> kinds);

    List<BillingEmail> findByUserIdAndSenderDomainAndRecurrenceCandidateTrue(Long userId, String senderDomain);

    @EntityGraph(attributePaths = "attachments")
    @Query("""
            select e from BillingEmail e where e.subscription.id = :subscriptionId
                or exists (select p from PaymentHistory p where p.billingEmail = e and p.subscription.id = :subscriptionId)
            order by e.receivedAt desc""")
    List<BillingEmail> findBySubscriptionIdOrderByReceivedAtDesc(@Param("subscriptionId") Long subscriptionId);

    @Modifying
    @Query("delete from BillingEmail e where e.user.id = :userId")
    int deleteAllOfUser(@Param("userId") Long userId);
}
