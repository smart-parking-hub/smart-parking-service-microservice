package com.smartparking.parkinglot.domain.model;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name = "slot_holds") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SlotHold {
    @Id private Long bookingId;
    @org.hibernate.annotations.CreationTimestamp
    @Column(nullable = false, updatable = false) private java.time.LocalDateTime createdAt;
    @Column(nullable = false) private Long slotId;
    @Column(nullable = false) private boolean released;
    @Column(name = "physical_slot_id") private String physicalSlotId;
    @Column(name = "publication_id") private String publicationId;
}
