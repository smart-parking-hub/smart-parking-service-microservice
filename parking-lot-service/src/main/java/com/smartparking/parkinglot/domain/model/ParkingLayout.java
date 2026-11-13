package com.smartparking.parkinglot.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "parking_layouts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParkingLayout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parking_lot_id", nullable = false, unique = true)
    private ParkingLot parkingLot;

    @Column(name = "layout_version", nullable = false)
    @Builder.Default
    private Integer layoutVersion = 1;

    @Column(name = "active_publication_id", length = 36)
    private String activePublicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "allocation_mode", nullable = false, length = 20)
    @Builder.Default
    private AllocationMode allocationMode = AllocationMode.GROUP;

    @Column(name = "draft_document", columnDefinition = "JSON")
    private String draftDocument;

    @Column(name = "source_asset_id", length = 36)
    private String sourceAssetId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
