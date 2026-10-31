package com.mirkamolcode.repository;

import com.mirkamolcode.entity.PaymentHistory;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentHistoryRepository extends JpaRepository<PaymentHistory, Long> {
    boolean existsByBillingEmailId(Long billingEmailId);

    List<PaymentHistory> findBySubscriptionUserIdAndPaymentDateBetween(Long userId, LocalDate from, LocalDate to);

    List<PaymentHistory> findBySubscriptionIdOrderByPaymentDateDesc(Long subscriptionId);

    @Query("select p.billingEmail.id from PaymentHistory p where p.subscription.id = :subscriptionId and p.billingEmail is not null")
    Set<Long> findBillingEmailIdsBySubscriptionId(@Param("subscriptionId") Long subscriptionId);

    @Modifying
    @Query("delete from PaymentHistory e where e.subscription.id in (select s.id from Subscription s where s.user.id = :userId)")
    int deleteAllOfUser(@Param("userId") Long userId);
}
