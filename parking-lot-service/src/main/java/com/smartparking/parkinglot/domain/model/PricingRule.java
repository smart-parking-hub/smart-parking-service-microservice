package com.smartparking.parkinglot.domain.model;

import com.smartparking.common.enums.VehicleTypeEnum;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalTime;

@Entity
@Table(name = "pricing_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PricingRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parking_lot_id", nullable = false)
    private ParkingLot parkingLot;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false)
    private VehicleTypeEnum vehicleType;

    @Column(name = "base_price_per_hour", nullable = false, precision = 10, scale = 2)
    private BigDecimal basePricePerHour;

    @Column(name = "surge_multiplier", nullable = false, precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal surgeMultiplier = new BigDecimal("1.00");

    @Column(name = "surge_start_time")
    private LocalTime surgeStartTime;

    @Column(name = "surge_end_time")
    private LocalTime surgeEndTime;

    @Column(name = "overtime_penalty_multiplier", nullable = false, precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal overtimePenaltyMultiplier = new BigDecimal("1.50");
}
