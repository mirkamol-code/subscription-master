package com.mirkamolcode.repository;

import com.mirkamolcode.entity.PaymentHistory;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentHistoryRepository extends JpaRepository<PaymentHistory, Long> {
    List<PaymentHistory> findBySubscriptionUserIdAndPaymentDateBetween(Long userId, LocalDate from, LocalDate to);

    List<PaymentHistory> findBySubscriptionIdOrderByPaymentDateDesc(Long subscriptionId);

    @Modifying
    @Query("delete from PaymentHistory e where e.subscription.id in (select s.id from Subscription s where s.user.id = :userId)")
    int deleteAllOfUser(@Param("userId") Long userId);
}
