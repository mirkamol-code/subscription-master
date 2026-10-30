package com.mirkamolcode.repository;

import com.mirkamolcode.entity.PushSubscription;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {
    List<PushSubscription> findByUserId(Long userId);

    Optional<PushSubscription> findByEndpoint(String endpoint);

    void deleteByUserIdAndEndpoint(Long userId, String endpoint);

    void deleteByEndpoint(String endpoint);

    @Modifying
    @Query("delete from PushSubscription e where e.user.id = :userId")
    int deleteAllOfUser(@Param("userId") Long userId);
}
