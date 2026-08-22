package com.mirkamolcode.repository;

import com.mirkamolcode.entity.Subscription;

import java.time.LocalDate;
import java.util.List;

import com.mirkamolcode.model.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long>, JpaSpecificationExecutor<Subscription> {
    List<Subscription> findByIsDeletedFalseAndStatusAndNextPaymentDateLessThanEqual(SubscriptionStatus status, LocalDate date);

    List<Subscription> findByIsDeletedFalseAndStatusAndNextPaymentDate(SubscriptionStatus status, LocalDate date);
}
