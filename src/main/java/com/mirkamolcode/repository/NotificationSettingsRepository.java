package com.mirkamolcode.repository;

import com.mirkamolcode.entity.NotificationSettings;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationSettingsRepository extends JpaRepository<NotificationSettings, Long> {
}
