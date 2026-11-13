package com.smartparking.parkinglot.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LayoutDocumentDto {

    @Builder.Default
    private Integer schemaVersion = 1;

    private Long parkingLotId;

    private Integer layoutVersion;

    private String sourceAssetId;

    @NotNull
    @Valid
    private GridDto grid;

    @Builder.Default
    @Valid
    private List<LayoutElementDto> elements = new ArrayList<>();
}
