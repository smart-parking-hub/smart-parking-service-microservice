package com.smartparking.payment.application.dto;

import com.smartparking.common.enums.DisputeStatusEnum;
import com.smartparking.payment.domain.model.Dispute;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDisputeResponse {
    private Long id;
    private Long bookingId;
    private Long driverId;
    private Long merchantId;
    private String reason;
    private String description;
    private DisputeStatusEnum status;
    private String resolution;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AdminDisputeResponse fromEntity(Dispute entity) {
        return AdminDisputeResponse.builder()
                .id(entity.getId())
                .bookingId(entity.getBookingId())
                .driverId(entity.getDriverId())
                .merchantId(entity.getMerchantId())
                .reason(entity.getReason())
                .description(entity.getDescription())
                .status(entity.getStatus())
                .resolution(entity.getResolution())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
