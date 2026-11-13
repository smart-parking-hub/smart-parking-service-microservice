package com.smartparking.parkinglot.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceAssetUploadResponse {
    private String sourceAssetId;
    private Long parkingLotId;
    private String storedFileName;
    private String originalFileName;
    private String contentType;
    private Long sizeBytes;
    private Integer width;
    private Integer height;
    private LocalDateTime createdAt;
}
