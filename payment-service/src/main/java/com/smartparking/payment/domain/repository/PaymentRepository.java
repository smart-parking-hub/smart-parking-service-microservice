package com.smartparking.payment.domain.repository;

import com.smartparking.common.enums.PaymentStatusEnum;
import com.smartparking.common.enums.PaymentTypeEnum;
import com.smartparking.payment.domain.model.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    java.util.List<Payment> findByGatewayAndStatusAndIdGreaterThanOrderByIdAsc(com.smartparking.common.enums.PaymentGatewayEnum gateway, PaymentStatusEnum status, Long id, org.springframework.data.domain.Pageable pageable);

    Page<Payment> findByPaymentType(PaymentTypeEnum type, Pageable pageable);
    Page<Payment> findByMerchantIdAndPaymentType(Long merchantId, PaymentTypeEnum type, Pageable pageable);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);
    List<Payment> findByBookingId(Long bookingId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.bookingId = :bookingId AND p.paymentType = :type ORDER BY p.id DESC")
    List<Payment> findAttemptsForUpdate(@Param("bookingId") Long bookingId, @Param("type") PaymentTypeEnum type);

    Optional<Payment> findByTransactionCode(String transactionCode);

    Optional<Payment> findByOrderCode(Long orderCode);

    Page<Payment> findByDriverId(Long driverId, Pageable pageable);

    Page<Payment> findByDriverIdAndBookingId(Long driverId, Long bookingId, Pageable pageable);

    boolean existsByBookingIdAndPaymentTypeAndStatus(
            Long bookingId,
            PaymentTypeEnum paymentType,
            PaymentStatusEnum status);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.merchantId = :merchantId AND p.status = 'SUCCESS'")
    BigDecimal sumTotalRevenueByMerchantId(@Param("merchantId") Long merchantId);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.merchantId = :merchantId AND p.parkingLotId = :parkingLotId AND p.status = 'SUCCESS'")
    BigDecimal sumTotalRevenueByMerchantIdAndParkingLotId(
            @Param("merchantId") Long merchantId,
            @Param("parkingLotId") Long parkingLotId);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
           "WHERE p.merchantId = :merchantId AND p.status = 'SUCCESS' " +
           "AND p.createdAt >= :startDate AND p.createdAt <= :endDate")
    BigDecimal sumRevenueByMerchantIdAndDateRange(
            @Param("merchantId") Long merchantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query("SELECT p FROM Payment p WHERE p.status = 'SUCCESS' " +
           "AND p.merchantId IS NOT NULL " +
           "AND p.createdAt >= :startDate AND p.createdAt < :endDate " +
           "ORDER BY p.id ASC")
    List<Payment> findSuccessfulPaymentsInWindow(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query("SELECT p FROM Payment p WHERE p.gateway = 'WALLET' AND p.paymentType = 'DEPOSIT' " +
           "AND p.status = 'SUCCESS' AND p.merchantId IS NOT NULL AND p.createdAt < :before " +
           "AND NOT EXISTS (SELECT j.id FROM LedgerJournal j WHERE j.paymentId = p.id " +
           "AND j.journalType = 'SETTLEMENT_CREDIT') ORDER BY p.id ASC")
    List<Payment> findUnsettledWalletDepositsBefore(@Param("before") LocalDateTime before);

    Optional<Payment> findFirstByDriverIdAndBookingIdAndGatewayOrderByIdDesc(
            Long driverId, Long bookingId, com.smartparking.common.enums.PaymentGatewayEnum gateway);

    List<Payment> findByMerchantIdAndStatusAndCreatedAtBetweenOrderByCreatedAtAsc(
            Long merchantId,
            PaymentStatusEnum status,
            LocalDateTime startDate,
            LocalDateTime endDate);

    List<Payment> findByMerchantIdAndParkingLotIdAndStatusAndCreatedAtBetweenOrderByCreatedAtAsc(
            Long merchantId,
            Long parkingLotId,
            PaymentStatusEnum status,
            LocalDateTime startDate,
            LocalDateTime endDate);

    @Query("SELECT MAX(p.createdAt) FROM Payment p WHERE p.merchantId = :merchantId AND p.status = 'SUCCESS'")
    LocalDateTime findLatestPaymentCreatedAtByMerchantId(@Param("merchantId") Long merchantId);

    @Query("SELECT MAX(p.createdAt) FROM Payment p WHERE p.merchantId = :merchantId AND p.parkingLotId = :parkingLotId AND p.status = 'SUCCESS'")
    LocalDateTime findLatestPaymentCreatedAtByMerchantIdAndParkingLotId(
            @Param("merchantId") Long merchantId,
            @Param("parkingLotId") Long parkingLotId);

    interface ChannelAggregation {
        Long getParkingLotId();
        String getPaymentChannel();
        BigDecimal getTotalAmount();
        Long getPaymentCount();
    }

    @Query("SELECT p.parkingLotId AS parkingLotId, COALESCE(p.paymentChannel, 'ONLINE_TRANSFER') AS paymentChannel, " +
           "COALESCE(SUM(p.amount), 0) AS totalAmount, COUNT(p.id) AS paymentCount " +
           "FROM Payment p WHERE p.merchantId = :merchantId AND p.status = 'SUCCESS' " +
           "GROUP BY p.parkingLotId, COALESCE(p.paymentChannel, 'ONLINE_TRANSFER')")
    List<ChannelAggregation> aggregateSuccessByMerchantGroupedByLotAndChannel(@Param("merchantId") Long merchantId);

    @Query("SELECT p.parkingLotId AS parkingLotId, COALESCE(p.paymentChannel, 'ONLINE_TRANSFER') AS paymentChannel, " +
           "COALESCE(SUM(p.amount), 0) AS totalAmount, COUNT(p.id) AS paymentCount " +
           "FROM Payment p WHERE p.merchantId = :merchantId AND p.status = 'SUCCESS' " +
           "AND p.createdAt >= :startDate AND p.createdAt <= :endDate " +
           "GROUP BY p.parkingLotId, COALESCE(p.paymentChannel, 'ONLINE_TRANSFER')")
    List<ChannelAggregation> aggregateSuccessByMerchantAndDateRangeGroupedByLotAndChannel(
            @Param("merchantId") Long merchantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
}
