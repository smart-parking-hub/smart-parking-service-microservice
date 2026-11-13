package com.smartparking.parkinglot.application.dto;

import com.smartparking.common.enums.VehicleTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LayoutValidationSummaryDto {

    private boolean valid;

    @Builder.Default
    private List<String> errors = new ArrayList<>();

    @Builder.Default
    private Map<VehicleTypeEnum, Integer> configuredCapacities = new HashMap<>();

    @Builder.Default
    private Map<VehicleTypeEnum, Integer> layoutSlotCounts = new HashMap<>();
}
