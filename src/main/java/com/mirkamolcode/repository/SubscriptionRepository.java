package com.mirkamolcode.repository;

import com.mirkamolcode.entity.Subscription;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.mirkamolcode.model.SubscriptionStatus;
import com.mirkamolcode.repository.projection.CurrencyTotal;
import com.mirkamolcode.repository.projection.MerchantOption;
import com.mirkamolcode.repository.projection.UserSubscriptionSummary;
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

    List<Subscription> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("""
            select count(distinct s.user.id) from Subscription s
             where s.isDeleted = false and s.isHidden = false
               and s.status = com.mirkamolcode.model.SubscriptionStatus.ACTIVE
            """)
    long countActiveSubscribers();

    @Query("""
            select s.currency as currency, count(s) as count, sum(s.price) as total from Subscription s
             where s.isDeleted = false and s.isHidden = false
               and s.status <> com.mirkamolcode.model.SubscriptionStatus.CANCELLED
               and s.nextPaymentDate between :from and :to
               and (s.endsOn is null or s.endsOn > s.nextPaymentDate)
             group by s.currency
            """)
    List<CurrencyTotal> sumDuePaymentsByCurrency(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query(value = """
            with per_merchant as (
                select s.user_id,
                       mode() within group (order by s.name) as merchant_name,
                       count(*) filter (where s.status = 'ACTIVE') as active_count,
                       bool_or(s.status = 'BILLING_REQUIRED') as has_billing_required,
                       bool_or(s.status = 'ACTIVE') as has_active,
                       bool_or(s.status = 'CANCELLED') as has_cancelled,
                       bool_and(s.status = 'CANCELLED') as is_all_cancelled
                  from subscriptions s
                 where s.user_id in (:userIds) and s.is_deleted = false and s.is_hidden = false
                 group by s.user_id, s.merchant_slug
            )
            select m.user_id as userId,
                   sum(m.active_count) as activeCount,
                   bool_or(m.has_billing_required) as hasBillingRequired,
                   bool_or(m.has_active) as hasActive,
                   bool_or(m.has_cancelled) as hasCancelled,
                   string_agg(m.merchant_name, chr(31) order by m.merchant_name)
                       filter (where not m.is_all_cancelled) as merchantNames
              from per_merchant m
             group by m.user_id
            """, nativeQuery = true)
    List<UserSubscriptionSummary> summariseByUser(@Param("userIds") Collection<Long> userIds);

    @Query(value = """
            select s.merchant_slug as slug, mode() within group (order by s.name) as name
              from subscriptions s
             where s.is_deleted = false and s.is_hidden = false and s.merchant_slug <> ''
             group by s.merchant_slug
             order by s.merchant_slug
            """, nativeQuery = true)
    List<MerchantOption> findMerchantOptions();

    @Query("""
            select s from Subscription s join fetch s.user
             where s.isDeleted = false and s.isHidden = false
               and s.status <> com.mirkamolcode.model.SubscriptionStatus.CANCELLED
               and (s.nextPaymentDate between :from and :to or s.endsOn between :from and :to)
               and (:merchantSlug is null or s.merchantSlug = :merchantSlug)
               and (:status is null or s.status = :status)
            """)
    List<Subscription> findDueBetween(@Param("from") LocalDate from,
                                      @Param("to") LocalDate to,
                                      @Param("merchantSlug") String merchantSlug,
                                      @Param("status") SubscriptionStatus status);

    @Query("""
            select s from Subscription s join fetch s.user
             where s.isDeleted = false and s.isHidden = false
               and s.status = com.mirkamolcode.model.SubscriptionStatus.BILLING_REQUIRED
            """)
    List<Subscription> findBillingRequired();
}
