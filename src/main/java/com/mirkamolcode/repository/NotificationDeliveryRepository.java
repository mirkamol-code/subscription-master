package com.mirkamolcode.repository;

import com.mirkamolcode.entity.NotificationDelivery;
import com.mirkamolcode.model.NotificationChannelType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {
    boolean existsByUserIdAndDedupeKeyAndChannel(Long userId, String dedupeKey, NotificationChannelType channel);

    @Modifying
    @Query("delete from NotificationDelivery e where e.userId = :userId")
    int deleteAllOfUser(@Param("userId") Long userId);
}
