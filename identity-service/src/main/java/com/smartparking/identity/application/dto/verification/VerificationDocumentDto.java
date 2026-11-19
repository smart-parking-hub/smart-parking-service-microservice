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
public class VerificationDocumentDto {
    private String id;
    private String type;
    private String originalName;
    private String contentType;
    private Long sizeBytes;
    private LocalDateTime uploadedAt;
}
