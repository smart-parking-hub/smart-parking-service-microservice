package com.smartparking.booking.application.dto;

import com.smartparking.common.enums.BookingStatusEnum;
import com.smartparking.common.enums.VehicleTypeEnum;
import com.smartparking.booking.domain.model.Booking;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingDto {
    private Long id;
    private String bookingCode;
    private Long driverId;
    private Long vehicleId;
    private String vehiclePlate;
    private String vehicleLicensePlate;
    private Long parkingLotId;
    private String parkingLotName;
    private String parkingLotAddress;
    private Long merchantId;
    private VehicleTypeEnum vehicleType;
    private BookingStatusEnum status;
    private BigDecimal advanceDeposit;
    private BigDecimal totalAmount;
    private BigDecimal hourlyRate;

    private BigDecimal surgeMultiplier;
    private java.time.LocalTime surgeStartTime;
    private java.time.LocalTime surgeEndTime;
    private BigDecimal overtimePenaltyMultiplier;

    private LocalDateTime expectedCheckInTime;
    private LocalDateTime expectedCheckOutTime;
    private LocalDateTime expiresAt;
    private LocalDateTime checkInTime;
    private LocalDateTime checkOutTime;
    private LocalDateTime cancelledAt;
    private String cancellationReason;
    private BigDecimal refundAmount;
    private Integer refundPercentage;
    private String refundPolicy;
    private String refundStatus;
    private String refundReference;
    private LocalDateTime createdAt;

    public static BookingDto fromEntity(Booking entity) {
        return BookingDto.builder()
                .id(entity.getId())
                .bookingCode(entity.getBookingCode())
                .driverId(entity.getDriverId())
                .vehicleId(entity.getVehicleId())
                .vehiclePlate(entity.getVehiclePlate())
                .vehicleLicensePlate(entity.getVehiclePlate())
                .parkingLotId(entity.getParkingLotId())
                .parkingLotName(entity.getParkingLotName())
                .parkingLotAddress(entity.getParkingLotAddress())
                .merchantId(entity.getMerchantId())
                .vehicleType(entity.getVehicleType())
                .status(entity.getStatus())
                .advanceDeposit(entity.getAdvanceDeposit())
                .totalAmount(entity.getTotalAmount())
                .hourlyRate(entity.getHourlyRate())
                .surgeMultiplier(entity.getSurgeMultiplier())
                .surgeStartTime(entity.getSurgeStartTime())
                .surgeEndTime(entity.getSurgeEndTime())
                .overtimePenaltyMultiplier(entity.getOvertimePenaltyMultiplier())
                .expectedCheckInTime(entity.getExpectedCheckInTime())
                .expectedCheckOutTime(entity.getExpectedCheckOutTime())
                .expiresAt(entity.getExpiresAt())
                .checkInTime(entity.getCheckInTime())
                .checkOutTime(entity.getCheckOutTime())
                .cancelledAt(entity.getCancelledAt())
                .cancellationReason(entity.getCancellationReason())
                .refundAmount(entity.getRefundAmount())
                .refundPercentage(entity.getRefundPercentage())
                .refundPolicy(entity.getRefundPolicy())
                .refundStatus(entity.getRefundStatus())
                .refundReference(entity.getRefundReference())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
