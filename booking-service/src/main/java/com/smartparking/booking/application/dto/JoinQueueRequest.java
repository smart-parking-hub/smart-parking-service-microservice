package com.smartparking.booking.application.dto;

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
public class JoinQueueRequest {
    @NotNull(message = "Thiếu parkingLotId")
    private Long parkingLotId;

    @NotNull(message = "Thiếu vehicleId")
    private Long vehicleId;

    @NotNull(message = "Thiếu vehicleType")
    private VehicleTypeEnum vehicleType;
}
