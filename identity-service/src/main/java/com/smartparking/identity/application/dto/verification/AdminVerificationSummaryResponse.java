package com.smartparking.identity.application.dto.verification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminVerificationSummaryResponse {
    private Long id;
    private Long merchantId;
    private String legalName;
    private String businessRegistrationNumber;
    private String status;
    private String assessmentStatus;
    private Integer submissionVersion;
    private LocalDateTime submittedAt;
}
