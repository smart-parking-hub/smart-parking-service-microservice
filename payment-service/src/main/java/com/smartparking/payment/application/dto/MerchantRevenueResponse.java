package com.smartparking.payment.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Collections;
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

    @Builder.Default
    private BigDecimal onlineTransferGrossRevenue = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal onlineTransferNetRevenue = BigDecimal.ZERO;
    @Builder.Default
    private Long onlineTransferCount = 0L;
    @Builder.Default
    private BigDecimal periodOnlineTransferNetRevenue = BigDecimal.ZERO;
    @Builder.Default
    private Long periodOnlineTransferCount = 0L;

    @Builder.Default
    private BigDecimal counterQrGrossRevenue = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal counterQrNetRevenue = BigDecimal.ZERO;
    @Builder.Default
    private Long counterQrCount = 0L;
    @Builder.Default
    private BigDecimal periodCounterQrNetRevenue = BigDecimal.ZERO;
    @Builder.Default
    private Long periodCounterQrCount = 0L;

    @Builder.Default
    private BigDecimal totalCashRevenue = BigDecimal.ZERO;
    @Builder.Default
    private Long totalCashBookings = 0L;
    @Builder.Default
    private BigDecimal periodCashRevenue = BigDecimal.ZERO;
    @Builder.Default
    private Long periodCashBookings = 0L;

    @Builder.Default
    private List<LotRevenueBreakdownDto> revenueByParkingLot = Collections.emptyList();

    private List<RevenueSeriesPointDto> series;
}
