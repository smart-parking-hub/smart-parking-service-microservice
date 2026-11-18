package com.smartparking.parkinglot.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** A time allocation, distinct from the one vehicle currently occupying a physical slot. */
@Entity
@Table(name = "slot_reservations")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class SlotReservation {
    @Id private Long bookingId;
    @Column(nullable = false) private Long parkingLotId;
    @Column(nullable = false) private Long slotGroupId;
    @Column(length = 36) private String physicalSlotId;
    @Column(length = 36) private String publicationId;
    @Column(nullable = false) private LocalDateTime startTime;
    @Column(nullable = false) private LocalDateTime endTime;
    @Column(nullable = false) private LocalDateTime protectedStart;
    @Column(nullable = false) private LocalDateTime protectedUntil;
    private LocalDateTime holdExpiresAt;
    private LocalDateTime actualCheckout;
    @Column(length = 36) private String occupationToken;
    @Column(length = 20) private String importState;
    @Column(nullable = false, length = 20) private String state;
    @Column(nullable = false) private Integer policyVersion;
    @Column(nullable = false) private LocalDateTime updatedAt;
}
