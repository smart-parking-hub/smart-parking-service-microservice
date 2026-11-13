package com.smartparking.parkinglot.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "current_slot_allocations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CurrentSlotAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parking_lot_id", nullable = false)
    private Long parkingLotId;

    @Column(name = "physical_slot_id", nullable = false, unique = true, length = 36)
    private String physicalSlotId;

    @Column(name = "booking_id", nullable = false, unique = true)
    private Long bookingId;

    @Column(name = "slot_group_id", nullable = false)
    private Long slotGroupId;

    @CreationTimestamp
    @Column(name = "allocated_at", nullable = false, updatable = false)
    private LocalDateTime allocatedAt;
}
