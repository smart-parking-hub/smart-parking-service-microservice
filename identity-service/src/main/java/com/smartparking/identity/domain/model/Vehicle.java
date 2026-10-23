package com.smartparking.identity.domain.model;

import com.smartparking.common.enums.VehicleStatusEnum;
import com.smartparking.common.enums.VehicleTypeEnum;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "vehicles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vehicle extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @Column(name = "license_plate", nullable = false, unique = true, length = 20)
    private String licensePlate;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false)
    private VehicleTypeEnum vehicleType;

    @Column(name = "weight_ton", precision = 5, scale = 2)
    private BigDecimal weightTon;

    @Column(name = "brand_model", length = 100)
    private String brandModel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VehicleStatusEnum status;
}
