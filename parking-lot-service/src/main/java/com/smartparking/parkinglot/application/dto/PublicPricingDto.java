package com.smartparking.parkinglot.application.dto;

import com.smartparking.common.enums.VehicleTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicPricingDto {
    private VehicleTypeEnum vehicleType;
    private BigDecimal basePricePerHour;
    @Builder.Default
    private String currency = "VND";
    @Builder.Default
    private String billingUnit = "HOUR";
    private BigDecimal surgeMultiplier;
    private LocalTime surgeStartTime;
    private LocalTime surgeEndTime;
    private BigDecimal overtimePenaltyMultiplier;
}
