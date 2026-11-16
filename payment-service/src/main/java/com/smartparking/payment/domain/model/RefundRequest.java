package com.smartparking.payment.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "refund_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefundRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 120)
    private String idempotencyKey;

    @Column(name = "booking_id", nullable = false, unique = true)
    private Long bookingId;

    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "driver_id", nullable = false)
    private Long driverId;

    @Column(name = "merchant_id")
    private Long merchantId;

    @Column(name = "parking_lot_id")
    private Long parkingLotId;

    @Column(name = "deposit_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal depositAmount = BigDecimal.ZERO;

    @Column(name = "refund_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "refund_percentage", nullable = false)
    @Builder.Default
    private Integer refundPercentage = 0;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String currency = "VND";

    @Column(name = "policy_code", nullable = false, length = 60)
    private String policyCode;

    @Column(nullable = false, length = 40)
    @Builder.Default
    private String status = "REQUESTED";

    @Column(name = "refund_channel", nullable = false, length = 30)
    @Builder.Default
    private String refundChannel = "BANK_MANUAL";

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "transaction_reference", length = 120)
    private String transactionReference;

    @Column(name = "evidence_url", length = 1024)
    private String evidenceUrl;

    @Column(name = "merchant_note", length = 500)
    private String merchantNote;

    @Column(name = "evidence_submitted_at")
    private LocalDateTime evidenceSubmittedAt;

    @Column(name = "verified_by")
    private Long verifiedBy;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "verification_note", length = 500)
    private String verificationNote;

    @Column(name = "due_at", nullable = false)
    private LocalDateTime dueAt;
}

