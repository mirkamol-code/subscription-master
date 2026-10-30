package com.mirkamolcode.repository;

import com.mirkamolcode.entity.MerchantTombstone;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MerchantTombstoneRepository extends JpaRepository<MerchantTombstone, Long> {
    Optional<MerchantTombstone> findByUserIdAndMerchantKey(Long userId, String merchantKey);

    @Modifying
    @Query("delete from MerchantTombstone e where e.user.id = :userId")
    int deleteAllOfUser(@Param("userId") Long userId);
}
