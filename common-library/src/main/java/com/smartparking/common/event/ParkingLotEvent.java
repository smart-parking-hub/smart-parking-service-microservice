package com.smartparking.common.event;

import com.smartparking.common.enums.VehicleTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

public class ParkingLotEvent {

    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static class SlotAvailabilityChangedEvent extends DomainEvent {
        private Long parkingLotId;
        private Long slotId;
        private VehicleTypeEnum vehicleType;
        private Integer availableCapacity;
        private Integer totalCapacity;
    }
}
