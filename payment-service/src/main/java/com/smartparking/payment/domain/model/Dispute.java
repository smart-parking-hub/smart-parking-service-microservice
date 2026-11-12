package com.smartparking.payment.domain.model;

import com.smartparking.common.enums.DisputeStatusEnum;
import com.smartparking.payment.infrastructure.persistence.EvidenceUrlsConverter;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "disputes", uniqueConstraints = {
        @UniqueConstraint(name = "uk_disputes_booking_id", columnNames = "booking_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dispute extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "driver_id", nullable = false)
    private Long driverId;

    @Column(name = "merchant_id")
    private Long merchantId;

    @Column(name = "parking_lot_id")
    private Long parkingLotId;

    @Column(nullable = false, length = 200)
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Convert(converter = EvidenceUrlsConverter.class)
    @Column(name = "evidence_urls", columnDefinition = "JSON")
    @Builder.Default
    private List<String> evidenceUrls = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DisputeStatusEnum status;

    @Column(columnDefinition = "TEXT")
    private String resolution;
}
