package com.smartparking.payment.application.dto;

import com.smartparking.payment.domain.model.DisputeHistory;
import com.smartparking.payment.domain.model.DisputeHistoryEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisputeTimelineEntryDto {
    private Long id;
    private DisputeHistoryEventType eventType;
    private String actorRole;
    private String message;
    private DriverDisputeStatus resultingStatus;
    private LocalDateTime createdAt;

    public static DisputeTimelineEntryDto fromEntity(DisputeHistory entry) {
        DriverDisputeStatus mappedStatus = entry.getResultingStatus() != null
                ? DriverDisputeStatus.valueOf(entry.getResultingStatus())
                : null;
        return DisputeTimelineEntryDto.builder()
                .id(entry.getId())
                .eventType(entry.getEventType())
                .actorRole(entry.getActorRole())
                .message(entry.getMessage())
                .resultingStatus(mappedStatus)
                .createdAt(entry.getCreatedAt())
                .build();
    }
}

