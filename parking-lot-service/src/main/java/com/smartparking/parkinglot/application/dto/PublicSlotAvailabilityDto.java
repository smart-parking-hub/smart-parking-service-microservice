package com.smartparking.parkinglot.application.dto;

import com.smartparking.common.enums.VehicleTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicSlotAvailabilityDto {
    private String physicalSlotId;
    private String code;
    private VehicleTypeEnum vehicleType;
    private String status; // AVAILABLE, HELD, UNAVAILABLE
}
