package com.smartparking.parkinglot.application.dto;

import com.smartparking.common.enums.VehicleTypeEnum;
import com.smartparking.parkinglot.domain.model.PricingRule;
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
public class PricingRuleDto {
    private Long id;
    private VehicleTypeEnum vehicleType;
    private BigDecimal basePricePerHour;
    private BigDecimal surgeMultiplier;
    private LocalTime surgeStartTime;
    private LocalTime surgeEndTime;
    private BigDecimal overtimePenaltyMultiplier;

    public static PricingRuleDto fromEntity(PricingRule rule) {
        return PricingRuleDto.builder()
                .id(rule.getId())
                .vehicleType(rule.getVehicleType())
                .basePricePerHour(rule.getBasePricePerHour())
                .surgeMultiplier(rule.getSurgeMultiplier())
                .surgeStartTime(rule.getSurgeStartTime())
                .surgeEndTime(rule.getSurgeEndTime())
                .overtimePenaltyMultiplier(rule.getOvertimePenaltyMultiplier())
                .build();
    }
}
