package com.mirkamolcode.repository;

import com.mirkamolcode.entity.PriceChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PriceChangeRepository extends JpaRepository<PriceChange, Long> {
    List<PriceChange> findByUserIdAndMerchantKeyAndSubscriptionIsNull(Long userId, String merchantKey);
}
