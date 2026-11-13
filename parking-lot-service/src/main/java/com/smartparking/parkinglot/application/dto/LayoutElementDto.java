package com.smartparking.parkinglot.application.dto;

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
public class LayoutElementDto {

    @NotNull
    private String elementId;

    @NotNull
    private CellTypeEnum cellType;

    @NotNull
    private Integer row;

    @NotNull
    private Integer col;

    private String physicalSlotId;
    private Long slotGroupId;
    private String code;
    private VehicleTypeEnum vehicleType;
    private String dimensionsMeters;
}
