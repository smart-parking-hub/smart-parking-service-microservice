package com.smartparking.ai.dto;

import com.smartparking.common.enums.ParkingLotStatusEnum;
import com.smartparking.common.enums.VehicleTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParkingLotDetailResponse {

    private Long id;
    private Long merchantId;
    private String name;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private ParkingLotStatusEnum status;
    private BigDecimal ratingAverage;
    private List<SlotDto> slots;
    private List<PricingRuleDto> pricingRules;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SlotDto {
        private Long id;
        private VehicleTypeEnum vehicleType;
        private Integer totalCapacity;
        private Integer availableCapacity;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PricingRuleDto {
        private Long id;
        private VehicleTypeEnum vehicleType;
        private BigDecimal basePricePerHour;
        private BigDecimal surgeMultiplier;
        private LocalTime surgeStartTime;
        private LocalTime surgeEndTime;
        private BigDecimal overtimePenaltyMultiplier;
    }
}

