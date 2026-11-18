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
public class RevenueSeriesPointDto {
    private String period;
    private BigDecimal revenue;
    private Long bookingCount;

    @Builder.Default
    private BigDecimal onlineTransferRevenue = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal counterQrRevenue = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal cashRevenue = BigDecimal.ZERO;

    @Builder.Default
    private Long onlineTransferCount = 0L;
    @Builder.Default
    private Long counterQrCount = 0L;
    @Builder.Default
    private Long cashBookingCount = 0L;
}
