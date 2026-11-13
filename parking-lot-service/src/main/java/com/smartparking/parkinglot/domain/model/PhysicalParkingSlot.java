package com.smartparking.parkinglot.domain.model;

import com.smartparking.common.enums.VehicleTypeEnum;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "physical_parking_slots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PhysicalParkingSlot {

    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @Column(name = "parking_lot_id", nullable = false)
    private Long parkingLotId;

    @Column(name = "slot_group_id", nullable = false)
    private Long slotGroupId;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 30)
    private VehicleTypeEnum vehicleType;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(name = "normalized_code", nullable = false, length = 50)
    private String normalizedCode;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "AVAILABLE";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
