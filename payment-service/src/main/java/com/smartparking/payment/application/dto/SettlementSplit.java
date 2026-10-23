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
public class SettlementSplit {
    private BigDecimal grossAmount;
    private BigDecimal feePercent;
    private BigDecimal feeAmount;
    private BigDecimal netAmount;
}
