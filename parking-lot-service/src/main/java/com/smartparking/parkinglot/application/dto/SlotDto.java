package com.smartparking.parkinglot.application.dto;

import com.smartparking.common.enums.VehicleTypeEnum;
import com.smartparking.parkinglot.domain.model.Slot;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlotDto {
    private Long id;
    private VehicleTypeEnum vehicleType;
    private Integer totalCapacity;
    private Integer availableCapacity;

    public static SlotDto fromEntity(Slot slot) {
        return SlotDto.builder()
                .id(slot.getId())
                .vehicleType(slot.getVehicleType())
                .totalCapacity(slot.getTotalCapacity())
                .availableCapacity(slot.getAvailableCapacity())
                .build();
    }
}
