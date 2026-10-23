package com.smartparking.booking.domain.model;

import com.smartparking.common.enums.QueueStatusEnum;
import com.smartparking.common.enums.VehicleTypeEnum;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "queues")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Queue extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "driver_id", nullable = false)
    private Long driverId;

    @Column(name = "vehicle_id", nullable = false)
    private Long vehicleId;

    @Column(name = "parking_lot_id", nullable = false)
    private Long parkingLotId;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 30)
    private VehicleTypeEnum vehicleType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private QueueStatusEnum status;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    @Column(name = "response_deadline")
    private LocalDateTime responseDeadline;

    @PrePersist
    @Override
    protected void onCreate() {
        super.onCreate();
        if (joinedAt == null) {
            joinedAt = LocalDateTime.now();
        }
    }
}
