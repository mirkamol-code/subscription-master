package com.mirkamolcode.repository;

import com.mirkamolcode.entity.NotificationDelivery;
import com.mirkamolcode.model.NotificationChannelType;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {
    boolean existsByUserIdAndDedupeKeyAndChannel(Long userId, String dedupeKey, NotificationChannelType channel);
}
