package com.smartparking.payment.application.service;

import com.smartparking.payment.application.dto.MerchantRevenueResponse;
import com.smartparking.payment.application.dto.SettlementSplit;
import com.smartparking.payment.domain.model.LedgerJournal;
import com.smartparking.payment.domain.model.Payment;

import java.math.BigDecimal;

public interface MerchantWalletService {
    SettlementSplit split(BigDecimal grossAmount);
    SettlementSplit creditSettlement(Long merchantId, Long bookingId, Payment payment, BigDecimal grossAmount, String note);
    void holdWithdrawal(Long merchantId, BigDecimal amount, String note);
    void refundWithdrawal(Long merchantId, BigDecimal amount, String note);
    BigDecimal getWalletBalance(Long merchantId);
    MerchantRevenueResponse getRevenueSummary(Long merchantId);
    MerchantRevenueResponse getRevenueSummary(Long merchantId, Long parkingLotId, String startDate, String endDate, String granularity);

    default void recordPaymentEscrow(Payment payment, String note) {
    }

    default void settleApprovedWithdrawal(Long merchantId, Long withdrawalId, BigDecimal amount, String note) {
    }

    default void recordRefundLiability(Long merchantId, Long bookingId, Long paymentId, BigDecimal refundAmount, String idempotencyKey, String note) {
    }

    default void settleCompletedRefund(Long merchantId, Long bookingId, Long paymentId, BigDecimal refundAmount, String idempotencyKey, String note) {
    }

    default BigDecimal getLedgerBackedBalance(Long merchantId) {
        return getWalletBalance(merchantId);
    }

    default boolean isWalletReconciled(Long merchantId) {
        return true;
    }

    default LedgerJournal reverseJournal(Long journalId, String reason) {
        throw new UnsupportedOperationException("Reversal not supported");
    }
}
