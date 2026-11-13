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
public class SaveLayoutDraftRequest {

    @NotNull(message = "Thiếu expectedVersion")
    private Integer expectedVersion;

    private String sourceAssetId;

    @NotNull(message = "Thiếu cấu hình lưới grid")
    @Valid
    private GridDto grid;

    @Builder.Default
    @Valid
    private List<LayoutElementDto> elements = new ArrayList<>();
}
