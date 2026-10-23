package com.smartparking.parkinglot.application.dto;

import com.smartparking.common.enums.ParkingLotStatusEnum;
import com.smartparking.parkinglot.domain.model.ParkingLot;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantParkingLotListResponse {
    private Long id;
    private String name;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private ParkingLotStatusEnum status;
    private BigDecimal ratingAverage;
    private LocalDateTime createdAt;

    public static MerchantParkingLotListResponse fromEntity(ParkingLot parkingLot) {
        return MerchantParkingLotListResponse.builder()
                .id(parkingLot.getId())
                .name(parkingLot.getName())
                .address(parkingLot.getAddress())
                .latitude(parkingLot.getLatitude())
                .longitude(parkingLot.getLongitude())
                .status(parkingLot.getStatus())
                .ratingAverage(parkingLot.getRatingAverage())
                .createdAt(parkingLot.getCreatedAt())
                .build();
    }
}
