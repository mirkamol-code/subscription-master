package com.mirkamolcode.repository;

import com.mirkamolcode.entity.GmailConnection;
import com.mirkamolcode.model.GmailConnectionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GmailConnectionRepository extends JpaRepository<GmailConnection, Long> {
    Optional<GmailConnection> findByUserId(Long userId);

    @Query("select c.user.id from GmailConnection c where c.status = :status order by c.user.id")
    List<Long> findUserIdsByStatus(@Param("status") GmailConnectionStatus status);

    @Modifying
    @Query("""
            update GmailConnection c
               set c.syncState = com.mirkamolcode.model.SyncState.RUNNING, c.syncStartedAt = :now, c.syncHeartbeatAt = :now,
                   c.syncProcessed = 0, c.syncTotal = 0, c.syncError = null
             where c.user.id = :userId
               and c.status = com.mirkamolcode.model.GmailConnectionStatus.CONNECTED
               and (c.syncState <> com.mirkamolcode.model.SyncState.RUNNING
                    or coalesce(c.syncHeartbeatAt, c.syncStartedAt) < :staleBefore)
            """)
    int claimSync(@Param("userId") Long userId, @Param("now") Instant now, @Param("staleBefore") Instant staleBefore);

    @Modifying
    @Query("""
            update GmailConnection c set c.syncTotal = :total, c.syncHeartbeatAt = :now
             where c.user.id = :userId and c.syncStartedAt = :claimedAt
               and c.syncState = com.mirkamolcode.model.SyncState.RUNNING
            """)
    int updateSyncTotal(@Param("userId") Long userId, @Param("claimedAt") Instant claimedAt,
                        @Param("total") int total, @Param("now") Instant now);

    @Modifying
    @Query("""
            update GmailConnection c set c.syncProcessed = :processed, c.syncHeartbeatAt = :now
             where c.user.id = :userId and c.syncStartedAt = :claimedAt
               and c.syncState = com.mirkamolcode.model.SyncState.RUNNING
            """)
    int updateSyncProcessed(@Param("userId") Long userId, @Param("claimedAt") Instant claimedAt,
                            @Param("processed") int processed, @Param("now") Instant now);

    @Modifying
    @Query("""
            update GmailConnection c
               set c.syncState = com.mirkamolcode.model.SyncState.IDLE, c.lastSyncedAt = :now,
                   c.historyId = coalesce(:historyId, c.historyId), c.syncError = :error
             where c.user.id = :userId and c.syncStartedAt = :claimedAt
               and c.syncState = com.mirkamolcode.model.SyncState.RUNNING
            """)
    int completeSync(@Param("userId") Long userId, @Param("claimedAt") Instant claimedAt, @Param("now") Instant now,
                     @Param("historyId") String historyId, @Param("error") String error);

    @Modifying
    @Query("""
            update GmailConnection c set c.syncState = com.mirkamolcode.model.SyncState.FAILED, c.syncError = :error
             where c.user.id = :userId and c.syncStartedAt = :claimedAt
               and c.syncState = com.mirkamolcode.model.SyncState.RUNNING
            """)
    int failSync(@Param("userId") Long userId, @Param("claimedAt") Instant claimedAt, @Param("error") String error);
}
