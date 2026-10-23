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
public class MerchantRevenueResponse {
    private Long merchantId;
    private BigDecimal totalGrossRevenue;
    private BigDecimal totalPlatformFee;
    private BigDecimal totalNetRevenue;
    private BigDecimal currentWalletBalance;
}
