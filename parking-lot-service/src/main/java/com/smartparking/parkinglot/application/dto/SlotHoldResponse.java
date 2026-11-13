package com.smartparking.parkinglot.application.dto;

import com.smartparking.common.enums.VehicleTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlotHoldResponse {
    private boolean success;
    private Long slotId;
    private Long parkingLotId;
    private VehicleTypeEnum vehicleType;
    private Integer remainingCapacity;
    private BigDecimal baseHourlyPrice;
    private Long merchantId;
    private String parkingLotName;
    private String parkingLotAddress;
    private BigDecimal surgeMultiplier;
    private java.time.LocalTime surgeStartTime;
    private java.time.LocalTime surgeEndTime;
    private BigDecimal overtimePenaltyMultiplier;
    private String physicalSlotId;
    private String physicalSlotCode;
    private String layoutPublicationId;
    private String message;
}
