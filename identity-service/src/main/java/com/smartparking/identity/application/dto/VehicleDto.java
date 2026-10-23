package com.smartparking.identity.application.dto;

import com.smartparking.common.enums.VehicleStatusEnum;
import com.smartparking.common.enums.VehicleTypeEnum;
import com.smartparking.identity.domain.model.Vehicle;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleDto {
    private Long id;
    private String licensePlate;
    private VehicleTypeEnum vehicleType;
    private BigDecimal weightTon;
    private String brandModel;
    private VehicleStatusEnum status;

    public static VehicleDto fromEntity(Vehicle vehicle) {
        return VehicleDto.builder()
                .id(vehicle.getId())
                .licensePlate(vehicle.getLicensePlate())
                .vehicleType(vehicle.getVehicleType())
                .weightTon(vehicle.getWeightTon())
                .brandModel(vehicle.getBrandModel())
                .status(vehicle.getStatus())
                .build();
    }
}
