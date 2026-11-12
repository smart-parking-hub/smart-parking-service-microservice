package com.smartparking.payment.application.dto;

import com.smartparking.common.enums.DisputeStatusEnum;
import com.smartparking.payment.domain.model.Dispute;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LegacyDisputeDto {
    private Long id;
    private Long bookingId;
    private Long driverId;
    private Long merchantId;
    private Long parkingLotId;
    private String reason;
    private String description;
    @Builder.Default
    private List<String> evidenceUrls = Collections.emptyList();
    private DisputeStatusEnum status;
    private String resolution;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static LegacyDisputeDto fromEntity(Dispute entity) {
        List<String> urls = entity.getEvidenceUrls() == null
                ? Collections.emptyList()
                : List.copyOf(entity.getEvidenceUrls());
        return LegacyDisputeDto.builder()
                .id(entity.getId())
                .bookingId(entity.getBookingId())
                .driverId(entity.getDriverId())
                .merchantId(entity.getMerchantId())
                .parkingLotId(entity.getParkingLotId())
                .reason(entity.getReason())
                .description(entity.getDescription())
                .evidenceUrls(urls)
                .status(entity.getStatus())
                .resolution(entity.getResolution())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}

