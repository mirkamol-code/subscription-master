package com.mirkamolcode.repository;

import com.mirkamolcode.entity.StatusChange;
import com.mirkamolcode.model.StatusChangeReason;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatusChangeRepository extends JpaRepository<StatusChange, Long> {
    boolean existsBySubscriptionIdAndReason(Long subscriptionId, StatusChangeReason reason);

    @EntityGraph(attributePaths = "billingEmail")
    List<StatusChange> findBySubscriptionIdOrderByEffectiveAtDescIdDesc(Long subscriptionId);
}
