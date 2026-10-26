package com.smartparking.payment.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantRevenueResponse {
    private Long merchantId;
    private BigDecimal totalGrossRevenue;
    private BigDecimal totalPlatformFee;
    private BigDecimal totalNetRevenue;
    private BigDecimal currentWalletBalance;

    private BigDecimal totalRevenue;
    private Long completedBookings;
    private BigDecimal walletBalance;
    private BigDecimal platformFeePercent;
    private BigDecimal minWithdrawalAmount;
    private List<RevenueSeriesPointDto> series;
}
