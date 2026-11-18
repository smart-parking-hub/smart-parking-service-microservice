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
public class MerchantVerificationDetailResponse {
    private Long id;
    private Long merchantId;
    private String status;
    private Long draftRevision;
    private Integer submissionVersion;
    private String businessRegistrationNumber;
    private String legalName;
    private String resubmissionNote;
    @Builder.Default
    private List<VerificationDocumentDto> documents = new ArrayList<>();
    private AssessmentSummaryDto assessment;
    @Builder.Default
    private List<RequestedItemDto> requestedItems = new ArrayList<>();
    @Builder.Default
    private List<DocumentRequirementDto> requirements = new ArrayList<>();
    @Builder.Default
    private List<String> editableItems = new ArrayList<>();
    @Builder.Default
    private List<VerificationHistoryDto> history = new ArrayList<>();
    private LocalDateTime updatedAt;
}
