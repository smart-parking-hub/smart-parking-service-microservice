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

    List<Payment> findByBookingId(Long bookingId);

    Optional<Payment> findByTransactionCode(String transactionCode);

    Optional<Payment> findByOrderCode(Long orderCode);

    Page<Payment> findByDriverId(Long driverId, Pageable pageable);

    boolean existsByBookingIdAndPaymentTypeAndStatus(
            Long bookingId,
            PaymentTypeEnum paymentType,
            PaymentStatusEnum status);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.merchantId = :merchantId AND p.status = 'SUCCESS'")
    BigDecimal sumTotalRevenueByMerchantId(@Param("merchantId") Long merchantId);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
           "WHERE p.merchantId = :merchantId AND p.status = 'SUCCESS' " +
           "AND p.createdAt >= :startDate AND p.createdAt <= :endDate")
    BigDecimal sumRevenueByMerchantIdAndDateRange(
            @Param("merchantId") Long merchantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
}
