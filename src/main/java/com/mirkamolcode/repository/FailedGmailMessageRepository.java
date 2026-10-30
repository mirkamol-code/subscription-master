package com.mirkamolcode.repository;

import com.mirkamolcode.entity.FailedGmailMessage;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FailedGmailMessageRepository extends JpaRepository<FailedGmailMessage, Long> {
    Optional<FailedGmailMessage> findByUserIdAndGmailMessageId(Long userId, String gmailMessageId);

    List<FailedGmailMessage> findByUserIdAndAttemptsLessThanOrderById(Long userId, int attempts);

    void deleteByUserIdAndGmailMessageId(Long userId, String gmailMessageId);

    @Modifying
    @Query("delete from FailedGmailMessage e where e.user.id = :userId")
    int deleteAllOfUser(@Param("userId") Long userId);
}
