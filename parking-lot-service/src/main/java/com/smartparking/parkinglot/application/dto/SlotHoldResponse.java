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
    private String message;
}
