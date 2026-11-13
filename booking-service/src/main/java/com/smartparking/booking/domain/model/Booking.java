package com.smartparking.booking.domain.model;

import com.smartparking.common.enums.BookingStatusEnum;
import com.smartparking.common.enums.VehicleTypeEnum;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "request_key", unique = true, length = 100)
    private String requestKey;

    @Column(name = "booking_code", nullable = false, unique = true, length = 50)
    private String bookingCode;

    @Column(name = "driver_id", nullable = false)
    private Long driverId;

    @Column(name = "vehicle_id", nullable = false)
    private Long vehicleId;

    @Column(name = "parking_lot_id", nullable = false)
    private Long parkingLotId;

    @Column(name = "merchant_id")
    private Long merchantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 30)
    private VehicleTypeEnum vehicleType;

    @Column(name = "vehicle_plate", length = 30)
    private String vehiclePlate;

    @Column(name = "parking_lot_name", length = 150)
    private String parkingLotName;

    @Column(name = "parking_lot_address", length = 255)
    private String parkingLotAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BookingStatusEnum status;

    @Column(name = "advance_deposit", precision = 10, scale = 2)
    private BigDecimal advanceDeposit;

    @Column(name = "total_amount", precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "hourly_rate", precision = 10, scale = 2)
    private BigDecimal hourlyRate;

    @Column(name = "surge_multiplier", precision = 3, scale = 2)
    private BigDecimal surgeMultiplier;
    @Column(name = "surge_start_time")
    private java.time.LocalTime surgeStartTime;
    @Column(name = "surge_end_time")
    private java.time.LocalTime surgeEndTime;
    @Column(name = "overtime_penalty_multiplier", precision = 3, scale = 2)
    private BigDecimal overtimePenaltyMultiplier;


    @Column(name = "expected_check_in_time")
    private LocalDateTime expectedCheckInTime;

    @Column(name = "expected_check_out_time")
    private LocalDateTime expectedCheckOutTime;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "check_in_time")
    private LocalDateTime checkInTime;

    @Column(name = "check_out_time")
    private LocalDateTime checkOutTime;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "refund_amount", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal refundAmount = BigDecimal.ZERO;

    @Column(name = "refund_percentage")
    @Builder.Default
    private Integer refundPercentage = 0;

    @Column(name = "refund_policy", length = 60)
    private String refundPolicy;

    @Column(name = "refund_status", nullable = false, length = 40)
    @Builder.Default
    private String refundStatus = "NONE";

    @Column(name = "refund_reference", length = 120)
    private String refundReference;

    @Column(name = "physical_slot_id", length = 36)
    private String physicalSlotId;

    @Column(name = "physical_slot_code", length = 50)
    private String physicalSlotCode;

    @Column(name = "layout_publication_id", length = 36)
    private String layoutPublicationId;
}
