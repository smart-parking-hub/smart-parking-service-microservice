package com.smartparking.payment.domain.repository;

import com.smartparking.payment.domain.model.WalletLedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
public interface WalletLedgerEntryRepository extends JpaRepository<WalletLedgerEntry, Long> {

    java.util.List<WalletLedgerEntry> findByMerchantId(Long merchantId);

    boolean existsByBookingIdAndEntryType(Long bookingId, com.smartparking.common.enums.WalletLedgerEntryTypeEnum entryType);

    Page<WalletLedgerEntry> findByMerchantIdOrderByCreatedAtDesc(Long merchantId, Pageable pageable);

    @Query("SELECT COALESCE(SUM(w.netAmount), 0) FROM WalletLedgerEntry w WHERE w.merchantId = :merchantId")
    BigDecimal sumNetAmountByMerchantId(@Param("merchantId") Long merchantId);

    @Query("SELECT COALESCE(SUM(w.feeAmount), 0) FROM WalletLedgerEntry w")
    BigDecimal sumTotalPlatformFee();

    @Query("SELECT COALESCE(SUM(w.feeAmount), 0) FROM WalletLedgerEntry w WHERE w.merchantId = :merchantId")
    BigDecimal sumPlatformFeeByMerchantId(@Param("merchantId") Long merchantId);

    @Query("SELECT w FROM WalletLedgerEntry w LEFT JOIN FETCH w.payment p " +
           "WHERE w.merchantId = :merchantId " +
           "AND w.entryType IN (com.smartparking.common.enums.WalletLedgerEntryTypeEnum.SETTLEMENT_CREDIT, " +
           "com.smartparking.common.enums.WalletLedgerEntryTypeEnum.ADJUSTMENT) " +
           "ORDER BY w.createdAt ASC")
    java.util.List<WalletLedgerEntry> findRevenueEntriesWithPaymentByMerchantId(@Param("merchantId") Long merchantId);
}
