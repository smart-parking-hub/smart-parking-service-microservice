package com.smartparking.parkinglot.application.dto;

import com.smartparking.common.enums.VehicleTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishLayoutResponse {

    private String publicationId;
    private Long parkingLotId;
    private Integer layoutVersion;
    private LocalDateTime publishedAt;
    private Integer totalSlotsCount;
    private Map<VehicleTypeEnum, Integer> slotsSummary;
    private String storageKey;
}
