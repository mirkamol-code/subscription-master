package com.mirkamolcode.repository;

import com.mirkamolcode.entity.Subscription;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.mirkamolcode.model.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long>, JpaSpecificationExecutor<Subscription> {
    boolean existsByUserId(Long userId);

    List<Subscription> findByIsDeletedFalseAndStatusAndNextPaymentDateLessThanEqual(SubscriptionStatus status, LocalDate date);

    List<Subscription> findByIsDeletedFalseAndStatusAndNextPaymentDate(SubscriptionStatus status, LocalDate date);

    Optional<Subscription> findByUserIdAndMerchantKeyAndIsDeletedFalse(Long userId, String merchantKey);

    @Query("""
            select s.id from Subscription s
             where s.isDeleted = false and s.isHidden = false and s.endsOn is null
               and s.status = com.mirkamolcode.model.SubscriptionStatus.ACTIVE
               and s.nextPaymentDate between :from and :to
             order by s.id
            """)
    List<Long> findReminderCandidateIds(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select s.id from Subscription s
             where s.isDeleted = false and s.status = com.mirkamolcode.model.SubscriptionStatus.ACTIVE
               and ((s.endsOn is not null and s.endsOn <= :latestLocalDate)
                    or (s.endsOn is null and s.nextPaymentDate < :overdueBefore))
             order by s.id
            """)
    List<Long> findDailyRuleCandidateIds(@Param("latestLocalDate") LocalDate latestLocalDate,
                                         @Param("overdueBefore") LocalDate overdueBefore);

    @Modifying
    @Query("delete from Subscription e where e.user.id = :userId")
    int deleteAllOfUser(@Param("userId") Long userId);
}
