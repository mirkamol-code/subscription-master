package com.mirkamolcode.repository;

import com.mirkamolcode.entity.MerchantTombstone;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantTombstoneRepository extends JpaRepository<MerchantTombstone, Long> {
    Optional<MerchantTombstone> findByUserIdAndMerchantKey(Long userId, String merchantKey);
}
