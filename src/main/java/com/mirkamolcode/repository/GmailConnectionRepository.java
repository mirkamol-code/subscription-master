package com.mirkamolcode.repository;

import com.mirkamolcode.entity.GmailConnection;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GmailConnectionRepository extends JpaRepository<GmailConnection, Long> {
    Optional<GmailConnection> findByUserId(Long userId);
}
