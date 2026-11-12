package com.smartparking.payment.domain.repository;

import com.smartparking.payment.domain.model.DisputeHistory;
import com.smartparking.payment.domain.model.DisputeHistoryEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Repository
public interface DisputeHistoryRepository extends JpaRepository<DisputeHistory, Long> {

    List<DisputeHistory> findByDisputeIdOrderByCreatedAtAscIdAsc(Long disputeId);

    @Query("""
            SELECT DISTINCT h.disputeId FROM DisputeHistory h
            WHERE h.disputeId IN :disputeIds
              AND h.eventType = :eventType
            """)
    Set<Long> findDisputeIdsWithEventType(
            @Param("disputeIds") Collection<Long> disputeIds,
            @Param("eventType") DisputeHistoryEventType eventType);
}

