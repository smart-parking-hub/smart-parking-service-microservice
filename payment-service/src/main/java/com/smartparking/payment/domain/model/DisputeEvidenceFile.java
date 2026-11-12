package com.smartparking.payment.domain.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "dispute_evidence_files", uniqueConstraints = {
        @UniqueConstraint(name = "uk_dispute_evidence_stored_name", columnNames = "stored_file_name")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisputeEvidenceFile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stored_file_name", nullable = false, length = 120)
    private String storedFileName;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "uploader_driver_id", nullable = false)
    private Long uploaderDriverId;

    @Column(name = "dispute_id")
    private Long disputeId;
}

