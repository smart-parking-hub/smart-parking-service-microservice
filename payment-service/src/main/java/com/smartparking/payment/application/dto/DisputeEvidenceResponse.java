package com.smartparking.payment.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisputeEvidenceResponse {
    private String fileName;
    private String originalFileName;
    private String contentType;
    private Long sizeBytes;
    private String contentUrl;
    private String url;
}

