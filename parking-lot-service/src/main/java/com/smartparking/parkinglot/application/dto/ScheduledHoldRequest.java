package com.smartparking.parkinglot.application.dto;

import com.smartparking.common.enums.VehicleTypeEnum;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record ScheduledHoldRequest(@NotNull Long bookingId, @NotNull VehicleTypeEnum vehicleType,
        String physicalSlotId, @NotNull LocalDateTime startTime, @NotNull LocalDateTime endTime,
        @NotNull LocalDateTime holdExpiresAt, Integer schedulePolicyVersion) {}
