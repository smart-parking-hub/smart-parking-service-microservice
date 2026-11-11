package com.smartparking.payment.application.dto;

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
public class DisputeDetailDto {
    private Long id;
    private Long bookingId;
    private Long driverId;
    private Long merchantId;
    private Long parkingLotId;
    private String reason;
    private String description;
    @Builder.Default
    private List<String> evidenceUrls = Collections.emptyList();
    private DriverDisputeStatus status;
    private String resolution;
    @Builder.Default
    private List<DisputeTimelineEntryDto> timeline = Collections.emptyList();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static DisputeDetailDto fromEntity(
            Dispute entity,
            boolean hasMerchantResponse,
            List<DisputeTimelineEntryDto> timeline) {
        List<String> urls = entity.getEvidenceUrls() == null
                ? Collections.emptyList()
                : List.copyOf(entity.getEvidenceUrls());
        List<DisputeTimelineEntryDto> entries = timeline == null
                ? Collections.emptyList()
                : List.copyOf(timeline);
        return DisputeDetailDto.builder()
                .id(entity.getId())
                .bookingId(entity.getBookingId())
                .driverId(entity.getDriverId())
                .merchantId(entity.getMerchantId())
                .parkingLotId(entity.getParkingLotId())
                .reason(entity.getReason())
                .description(entity.getDescription())
                .evidenceUrls(urls)
                .status(DriverDisputeStatus.fromEntity(entity.getStatus(), hasMerchantResponse))
                .resolution(entity.getResolution())
                .timeline(entries)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}

