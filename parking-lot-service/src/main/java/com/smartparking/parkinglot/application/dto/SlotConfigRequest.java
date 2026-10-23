package com.smartparking.parkinglot.application.dto;

import com.smartparking.common.enums.VehicleTypeEnum;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlotConfigRequest {
    @NotNull(message = "Loại xe không được để trống")
    private VehicleTypeEnum vehicleType;

    @NotNull(message = "Sức chứa không được để trống")
    @Min(value = 0, message = "Sức chứa phải lớn hơn hoặc bằng 0")
    private Integer totalCapacity;
}
