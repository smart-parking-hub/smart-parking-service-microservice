package com.smartparking.identity.application.dto.verification;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitVerificationRequest {
    @NotNull(message = "draftRevision không được để trống")
    private Long draftRevision;

    @Size(max = 64)
    private String ocrDocumentId;
}
