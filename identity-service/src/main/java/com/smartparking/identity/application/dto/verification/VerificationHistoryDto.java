package com.smartparking.identity.application.dto.verification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationHistoryDto {
    private Integer submissionVersion;
    private String action; // SUBMITTED, CHANGES_REQUESTED, APPROVED
    private String actor;
    private String note;
    private List<RequestedItemDto> items;
    private LocalDateTime occurredAt;
}
