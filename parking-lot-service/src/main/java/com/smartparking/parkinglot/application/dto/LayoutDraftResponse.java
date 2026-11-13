package com.smartparking.parkinglot.application.dto;

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
public class LayoutDraftResponse {

    private Long parkingLotId;
    private Integer layoutVersion;
    private String activePublicationId;
    private String sourceAssetId;
    private String sourceImageUrl;
    private GridDto grid;
    private List<LayoutElementDto> elements;
    private LayoutValidationSummaryDto validationSummary;
    private LocalDateTime updatedAt;
}
