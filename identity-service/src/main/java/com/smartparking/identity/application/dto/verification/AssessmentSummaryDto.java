package com.smartparking.identity.application.dto.verification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentSummaryDto {
    private String status; // NOT_STARTED, PENDING, COMPLETED, UNAVAILABLE
    @Builder.Default
    private List<AssessmentCheckDto> checks = new ArrayList<>();
    private LocalDateTime checkedAt;
    private AssessmentSourceDto source;
}
