package com.smartparking.payment.domain.repository;

import com.smartparking.payment.domain.model.RefundRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {

    List<RefundRequest> findByRefundChannelAndStatusAndIdGreaterThanOrderByIdAsc(String channel, String status, Long id, org.springframework.data.domain.Pageable pageable);

    List<RefundRequest> findByPaymentIdIn(java.util.Collection<Long> paymentIds);

    Optional<RefundRequest> findByIdempotencyKey(String idempotencyKey);

    Optional<RefundRequest> findByBookingId(Long bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RefundRequest r WHERE r.id = :id")
    Optional<RefundRequest> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RefundRequest r WHERE r.bookingId = :bookingId")
    Optional<RefundRequest> findByBookingIdForUpdate(@Param("bookingId") Long bookingId);

    Page<RefundRequest> findByMerchantIdOrderByCreatedAtDesc(Long merchantId, Pageable pageable);

    List<RefundRequest> findByStatusAndDueAtBefore(String status, LocalDateTime cutoff);
}

