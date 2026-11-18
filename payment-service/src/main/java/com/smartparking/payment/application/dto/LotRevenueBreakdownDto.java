package com.smartparking.payment.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LotRevenueBreakdownDto {
    private Long parkingLotId;

    @Builder.Default
    private BigDecimal onlineTransferGrossRevenue = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal onlineTransferRevenue = BigDecimal.ZERO;
    @Builder.Default
    private Long onlineTransferCount = 0L;

    @Builder.Default
    private BigDecimal counterQrGrossRevenue = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal counterQrRevenue = BigDecimal.ZERO;
    @Builder.Default
    private Long counterQrCount = 0L;

    @Builder.Default
    private BigDecimal cashRevenue = BigDecimal.ZERO;
    @Builder.Default
    private Long cashBookingCount = 0L;

    @Builder.Default
    private BigDecimal periodOnlineTransferRevenue = BigDecimal.ZERO;
    @Builder.Default
    private Long periodOnlineTransferCount = 0L;

    @Builder.Default
    private BigDecimal periodCounterQrRevenue = BigDecimal.ZERO;
    @Builder.Default
    private Long periodCounterQrCount = 0L;

    @Builder.Default
    private BigDecimal periodCashRevenue = BigDecimal.ZERO;
    @Builder.Default
    private Long periodCashBookingCount = 0L;
}
