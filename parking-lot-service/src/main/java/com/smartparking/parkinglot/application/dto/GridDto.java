package com.smartparking.parkinglot.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GridDto {
    @NotNull
    @Min(1)
    @Max(100)
    private Integer rows;

    @NotNull
    @Min(1)
    @Max(100)
    private Integer cols;

    @Builder.Default
    private Integer indexBase = 0;
}
