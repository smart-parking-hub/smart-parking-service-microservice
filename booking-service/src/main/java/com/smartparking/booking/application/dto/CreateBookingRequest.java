package com.smartparking.booking.application.dto;

import com.smartparking.common.enums.VehicleTypeEnum;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBookingRequest {
    @jakarta.validation.constraints.Pattern(regexp = "[a-zA-Z0-9-]{1,64}")
    private String requestId;
    @NotNull(message = "Thiếu vehicleId")
    private Long vehicleId;

    @NotNull(message = "Thiếu parkingLotId")
    private Long parkingLotId;

    @NotNull(message = "Thiếu vehicleType")
    private VehicleTypeEnum vehicleType;

    @NotNull(message = "Thiếu expectedCheckInTime")
    private LocalDateTime expectedCheckInTime;

    @NotNull(message = "Thiếu expectedCheckOutTime")
    private LocalDateTime expectedCheckOutTime;

    private String vehiclePlate;
    private String parkingLotName;
    private String parkingLotAddress;
    private Long merchantId;
    private java.math.BigDecimal totalAmount;
    private java.math.BigDecimal advanceDeposit;
    private String physicalSlotId;
}
