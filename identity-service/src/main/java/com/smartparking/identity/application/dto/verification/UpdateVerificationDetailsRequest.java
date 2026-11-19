package com.smartparking.identity.application.dto.verification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateVerificationDetailsRequest {
    @NotNull(message = "draftRevision không được để trống")
    private Long draftRevision;

    @NotBlank(message = "Mã số doanh nghiệp không được để trống")
    private String businessRegistrationNumber;

    @NotBlank(message = "Tên doanh nghiệp không được để trống")
    private String legalName;

    private String resubmissionNote;
}
