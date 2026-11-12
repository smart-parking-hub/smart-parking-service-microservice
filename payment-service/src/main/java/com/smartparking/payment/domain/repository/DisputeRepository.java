package com.smartparking.payment.domain.repository;

import com.smartparking.common.enums.DisputeStatusEnum;
import com.smartparking.payment.domain.model.Dispute;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DisputeRepository extends JpaRepository<Dispute, Long> {

    Page<Dispute> findByDriverIdOrderByCreatedAtDesc(Long driverId, Pageable pageable);

    Page<Dispute> findByMerchantIdOrderByCreatedAtDesc(Long merchantId, Pageable pageable);

    Optional<Dispute> findByBookingId(Long bookingId);

    Optional<Dispute> findByIdAndDriverId(Long id, Long driverId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Dispute d WHERE d.id = :id")
    Optional<Dispute> findByIdForUpdate(@Param("id") Long id);

    @Query(value = "SELECT d FROM Dispute d WHERE d.driverId = :driverId ORDER BY d.createdAt DESC, d.id DESC",
           countQuery = "SELECT COUNT(d) FROM Dispute d WHERE d.driverId = :driverId")
    Page<Dispute> findAllDriverDisputesOrdered(@Param("driverId") Long driverId, Pageable pageable);

    @Query(value = "SELECT d FROM Dispute d WHERE d.driverId = :driverId AND d.status = :status ORDER BY d.createdAt DESC, d.id DESC",
           countQuery = "SELECT COUNT(d) FROM Dispute d WHERE d.driverId = :driverId AND d.status = :status")
    Page<Dispute> findDriverDisputesByStatusOrdered(
            @Param("driverId") Long driverId,
            @Param("status") DisputeStatusEnum status,
            Pageable pageable);

    @Query(value = """
            SELECT d FROM Dispute d
            WHERE d.driverId = :driverId
              AND d.status = com.smartparking.common.enums.DisputeStatusEnum.PENDING
              AND NOT EXISTS (
                  SELECT 1 FROM DisputeHistory h
                  WHERE h.disputeId = d.id
                    AND h.eventType = com.smartparking.payment.domain.model.DisputeHistoryEventType.MERCHANT_RESPONSE
              )
            ORDER BY d.createdAt DESC, d.id DESC
            """,
           countQuery = """
            SELECT COUNT(d) FROM Dispute d
            WHERE d.driverId = :driverId
              AND d.status = com.smartparking.common.enums.DisputeStatusEnum.PENDING
              AND NOT EXISTS (
                  SELECT 1 FROM DisputeHistory h
                  WHERE h.disputeId = d.id
                    AND h.eventType = com.smartparking.payment.domain.model.DisputeHistoryEventType.MERCHANT_RESPONSE
              )
            """)
    Page<Dispute> findDriverOpenDisputesOrdered(@Param("driverId") Long driverId, Pageable pageable);

    @Query(value = """
            SELECT d FROM Dispute d
            WHERE d.driverId = :driverId
              AND d.status = com.smartparking.common.enums.DisputeStatusEnum.PENDING
              AND EXISTS (
                  SELECT 1 FROM DisputeHistory h
                  WHERE h.disputeId = d.id
                    AND h.eventType = com.smartparking.payment.domain.model.DisputeHistoryEventType.MERCHANT_RESPONSE
              )
            ORDER BY d.createdAt DESC, d.id DESC
            """,
           countQuery = """
            SELECT COUNT(d) FROM Dispute d
            WHERE d.driverId = :driverId
              AND d.status = com.smartparking.common.enums.DisputeStatusEnum.PENDING
              AND EXISTS (
                  SELECT 1 FROM DisputeHistory h
                  WHERE h.disputeId = d.id
                    AND h.eventType = com.smartparking.payment.domain.model.DisputeHistoryEventType.MERCHANT_RESPONSE
              )
            """)
    Page<Dispute> findDriverInReviewDisputesOrdered(@Param("driverId") Long driverId, Pageable pageable);

    Page<Dispute> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(DisputeStatusEnum status);
}
