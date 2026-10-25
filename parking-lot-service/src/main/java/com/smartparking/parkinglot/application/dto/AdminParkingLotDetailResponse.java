package com.smartparking.parkinglot.application.dto;

import com.smartparking.common.enums.ParkingLotStatusEnum;
import com.smartparking.parkinglot.domain.model.ParkingLot;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminParkingLotDetailResponse {
    private Long id;
    private Long merchantId;
    private String merchantName;
    private String merchantPhone;
    private String name;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private ParkingLotStatusEnum status;
    private List<SlotDto> slots;
    private List<PricingRuleDto> pricingRules;
    private List<ParkingLotImageResponse> images;
    private String reviewReason;
    private Long reviewedByUserId;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;

    public static AdminParkingLotDetailResponse from(ParkingLot lot, List<SlotDto> slots, List<PricingRuleDto> prices, List<ParkingLotImageResponse> images) {
        return AdminParkingLotDetailResponse.builder()
                .id(lot.getId())
                .merchantId(lot.getMerchantId())
                .merchantName("Merchant #" + lot.getMerchantId())
                .merchantPhone("")
                .name(lot.getName())
                .address(lot.getAddress())
                .latitude(lot.getLatitude())
                .longitude(lot.getLongitude())
                .status(lot.getStatus())
                .slots(slots)
                .pricingRules(prices)
                .images(images)
                .reviewReason(lot.getReviewReason())
                .reviewedByUserId(lot.getReviewedBy())
                .reviewedAt(lot.getReviewedAt())
                .createdAt(lot.getCreatedAt())
                .build();
    }
}
