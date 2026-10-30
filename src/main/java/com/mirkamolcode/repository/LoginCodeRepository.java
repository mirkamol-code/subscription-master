package com.mirkamolcode.repository;

import com.mirkamolcode.entity.LoginCode;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoginCodeRepository extends JpaRepository<LoginCode, Long> {
    Optional<LoginCode> findByCodeHash(String codeHash);

    @Modifying
    @Query("update LoginCode c set c.used = true where c.codeHash = :codeHash and c.used = false and c.expiresAt > :now")
    int markUsed(@Param("codeHash") String codeHash, @Param("now") Instant now);
}
