package com.smartparking.parkinglot.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "parking_layout_publications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParkingLayoutPublication {

    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @Column(name = "parking_lot_id", nullable = false)
    private Long parkingLotId;

    @Column(name = "layout_version", nullable = false)
    private Integer layoutVersion;

    @Column(name = "storage_key", nullable = false, length = 255)
    private String storageKey;

    @Column(name = "source_asset_id", length = 36)
    private String sourceAssetId;

    @Column(name = "grid_rows", nullable = false)
    private Integer gridRows;

    @Column(name = "grid_cols", nullable = false)
    private Integer gridCols;

    @Column(name = "total_slots_count", nullable = false)
    private Integer totalSlotsCount;

    @Column(name = "slots_summary", nullable = false, columnDefinition = "JSON")
    private String slotsSummary;

    @Column(name = "canonical_document", columnDefinition = "JSON")
    private String canonicalDocument;

    @CreationTimestamp
    @Column(name = "published_at", nullable = false, updatable = false)
    private LocalDateTime publishedAt;

    @Column(name = "request_id", length = 64)
    private String requestId;
}
