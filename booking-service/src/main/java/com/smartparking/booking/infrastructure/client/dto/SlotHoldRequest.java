package com.smartparking.booking.infrastructure.client.dto;

import com.smartparking.common.enums.VehicleTypeEnum;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlotHoldRequest {
    private Long parkingLotId;
    @NotNull
    private VehicleTypeEnum vehicleType;
    @NotNull
    private Long bookingId;
    private String physicalSlotId;
}
