package com.smartparking.payment.application.service;

import com.smartparking.payment.application.dto.MerchantRevenueResponse;
import com.smartparking.payment.application.dto.SettlementSplit;
import com.smartparking.payment.domain.model.Payment;

import java.math.BigDecimal;

public interface MerchantWalletService {
    SettlementSplit split(BigDecimal grossAmount);
    SettlementSplit creditSettlement(Long merchantId, Long bookingId, Payment payment, BigDecimal grossAmount, String note);
    void holdWithdrawal(Long merchantId, BigDecimal amount, String note);
    void refundWithdrawal(Long merchantId, BigDecimal amount, String note);
    BigDecimal getWalletBalance(Long merchantId);
    MerchantRevenueResponse getRevenueSummary(Long merchantId);
}
