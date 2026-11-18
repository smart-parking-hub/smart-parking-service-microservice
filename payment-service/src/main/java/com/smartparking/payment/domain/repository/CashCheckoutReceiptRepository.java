package com.smartparking.payment.domain.repository;

import com.smartparking.payment.domain.model.CashCheckoutReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CashCheckoutReceiptRepository extends JpaRepository<CashCheckoutReceipt, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CashCheckoutReceipt r where r.bookingId = :id")
    Optional<CashCheckoutReceipt> findCurrentForUpdate(@Param("id") Long bookingId);
    List<CashCheckoutReceipt> findByStatusAndBookingIdGreaterThanOrderByBookingIdAsc(String status, Long after, Pageable pageable);

    interface LotCashAggregation {
        Long getParkingLotId();
        BigDecimal getTotalAmount();
        Long getBookingCount();
    }

    @Query("SELECT r.parkingLotId AS parkingLotId, COALESCE(SUM(r.amount), 0) AS totalAmount, COUNT(r.bookingId) AS bookingCount " +
           "FROM CashCheckoutReceipt r WHERE r.merchantId = :merchantId AND r.status = 'COMPLETED' " +
           "GROUP BY r.parkingLotId")
    List<LotCashAggregation> aggregateCompletedByMerchantGroupedByLot(@Param("merchantId") Long merchantId);

    @Query("SELECT r.parkingLotId AS parkingLotId, COALESCE(SUM(r.amount), 0) AS totalAmount, COUNT(r.bookingId) AS bookingCount " +
           "FROM CashCheckoutReceipt r WHERE r.merchantId = :merchantId AND r.status = 'COMPLETED' " +
           "AND r.updatedAt >= :startDate AND r.updatedAt <= :endDate " +
           "GROUP BY r.parkingLotId")
    List<LotCashAggregation> aggregateCompletedByMerchantAndDateRangeGroupedByLot(
            @Param("merchantId") Long merchantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    List<CashCheckoutReceipt> findByMerchantIdAndStatusAndUpdatedAtBetweenOrderByUpdatedAtAsc(
            Long merchantId,
            String status,
            LocalDateTime startDate,
            LocalDateTime endDate);

    List<CashCheckoutReceipt> findByMerchantIdAndParkingLotIdAndStatusAndUpdatedAtBetweenOrderByUpdatedAtAsc(
            Long merchantId,
            Long parkingLotId,
            String status,
            LocalDateTime startDate,
            LocalDateTime endDate);

    @Query("SELECT MAX(r.updatedAt) FROM CashCheckoutReceipt r WHERE r.merchantId = :merchantId AND r.status = 'COMPLETED'")
    LocalDateTime findLatestCompletedUpdatedAtByMerchantId(@Param("merchantId") Long merchantId);

    @Query("SELECT MAX(r.updatedAt) FROM CashCheckoutReceipt r WHERE r.merchantId = :merchantId AND r.parkingLotId = :parkingLotId AND r.status = 'COMPLETED'")
    LocalDateTime findLatestCompletedUpdatedAtByMerchantIdAndParkingLotId(
            @Param("merchantId") Long merchantId,
            @Param("parkingLotId") Long parkingLotId);
}
