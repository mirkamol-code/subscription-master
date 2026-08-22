package com.mirkamolcode.repository;

import com.mirkamolcode.entity.PaymentHistory;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentHistoryRepository extends JpaRepository<PaymentHistory, Long> {
    List<PaymentHistory> findBySubscriptionUserIdAndPaymentDateBetween(Long userId, LocalDate from, LocalDate to);
}
