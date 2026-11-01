package com.mirkamolcode.repository;

import com.mirkamolcode.entity.LoginEvent;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginEventRepository extends JpaRepository<LoginEvent, Long> {
    Page<LoginEvent> findByUserIdOrderByOccurredAtDesc(Long userId, Pageable pageable);

    long countByOccurredAtGreaterThanEqual(Instant since);
}
