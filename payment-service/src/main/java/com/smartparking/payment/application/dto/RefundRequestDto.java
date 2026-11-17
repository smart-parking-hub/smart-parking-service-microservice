package com.smartparking.payment.application.dto;

import com.smartparking.payment.domain.model.RefundRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundRequestDto {
    private Long id;
    private Long bookingId;
    private Long paymentId;
    private Long driverId;
    private Long merchantId;
    private Long parkingLotId;
    private BigDecimal depositAmount;
    private BigDecimal refundAmount;
    private Integer refundPercentage;
    private String currency;
    private String policyCode;
    private String status;
    private String refundChannel;
    private String cancellationReason;
    private String transactionReference;
    private String evidenceUrl;
    private String merchantNote;
    private LocalDateTime evidenceSubmittedAt;
    private Long verifiedBy;
    private LocalDateTime verifiedAt;
    private String verificationNote;
    private LocalDateTime dueAt;
    private LocalDateTime createdAt;

    public static RefundRequestDto fromEntity(RefundRequest entity) {
        if (entity == null) {
            return null;
        }
        return RefundRequestDto.builder()
                .id(entity.getId())
                .bookingId(entity.getBookingId())
                .paymentId(entity.getPaymentId())
                .driverId(entity.getDriverId())
                .merchantId(entity.getMerchantId())
                .parkingLotId(entity.getParkingLotId())
                .depositAmount(entity.getDepositAmount())
                .refundAmount(entity.getRefundAmount())
                .refundPercentage(entity.getRefundPercentage())
                .currency(entity.getCurrency())
                .policyCode(entity.getPolicyCode())
                .status(entity.getStatus())
                .refundChannel(entity.getRefundChannel())
                .cancellationReason(entity.getCancellationReason())
                .transactionReference(entity.getTransactionReference())
                .evidenceUrl(entity.getEvidenceUrl())
                .merchantNote(entity.getMerchantNote())
                .evidenceSubmittedAt(entity.getEvidenceSubmittedAt())
                .verifiedBy(entity.getVerifiedBy())
                .verifiedAt(entity.getVerifiedAt())
                .verificationNote(entity.getVerificationNote())
                .dueAt(entity.getDueAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}

