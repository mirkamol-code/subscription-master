package com.mirkamolcode.repository;

import com.mirkamolcode.entity.Subscription;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.mirkamolcode.model.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long>, JpaSpecificationExecutor<Subscription> {
    List<Subscription> findByIsDeletedFalseAndStatusAndNextPaymentDateLessThanEqual(SubscriptionStatus status, LocalDate date);

    List<Subscription> findByIsDeletedFalseAndStatusAndNextPaymentDate(SubscriptionStatus status, LocalDate date);

    Optional<Subscription> findByUserIdAndMerchantKeyAndIsDeletedFalse(Long userId, String merchantKey);

    @Query("""
            select s from Subscription s
             where s.user.id = :userId and s.merchantKey is null and s.isDeleted = false
               and lower(s.name) in :lowercaseNames
             order by s.id
            """)
    List<Subscription> findManualByNames(@Param("userId") Long userId, @Param("lowercaseNames") Collection<String> lowercaseNames);
}
