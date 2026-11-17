package com.smartparking.payment.domain.repository;

import com.smartparking.payment.domain.model.CashCheckoutReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface CashCheckoutReceiptRepository extends JpaRepository<CashCheckoutReceipt, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CashCheckoutReceipt r where r.bookingId = :id")
    Optional<CashCheckoutReceipt> findCurrentForUpdate(@Param("id") Long bookingId);
    List<CashCheckoutReceipt> findByStatusAndBookingIdGreaterThanOrderByBookingIdAsc(String status, Long after, Pageable pageable);
}
