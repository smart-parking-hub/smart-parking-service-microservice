package com.smartparking.parkinglot.application.dto;

import com.smartparking.common.enums.ParkingLotStatusEnum;
import com.smartparking.parkinglot.domain.model.ParkingLot;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParkingLotDto {
    private Long id;
    private Long merchantId;
    private String name;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private ParkingLotStatusEnum status;
    private String reviewReason;
    private BigDecimal ratingAverage;
    private List<SlotDto> slots;
    private List<PricingRuleDto> pricingRules;
    private List<ParkingLotImageResponse> images;

    public static ParkingLotDto fromEntity(ParkingLot lot) {
        return ParkingLotDto.builder()
                .id(lot.getId())
                .merchantId(lot.getMerchantId())
                .name(lot.getName())
                .address(lot.getAddress())
                .latitude(lot.getLatitude())
                .longitude(lot.getLongitude())
                .status(lot.getStatus())
                .reviewReason(lot.getReviewReason())
                .ratingAverage(lot.getRatingAverage() != null ? lot.getRatingAverage() : BigDecimal.ZERO)
                .images(List.of())
                .build();
    }
}
