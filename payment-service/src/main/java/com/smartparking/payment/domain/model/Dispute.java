package com.smartparking.payment.domain.model;

import com.smartparking.common.enums.DisputeStatusEnum;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "disputes")
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

    @Column(nullable = false, length = 200)
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DisputeStatusEnum status;

    @Column(columnDefinition = "TEXT")
    private String resolution;
}
