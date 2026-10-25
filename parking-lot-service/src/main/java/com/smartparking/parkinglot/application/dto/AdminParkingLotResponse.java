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
public class AdminParkingLotResponse {
    private Long id;
    private Long merchantId;
    private String name;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private ParkingLotStatusEnum status;
    private LocalDateTime createdAt;

    public static AdminParkingLotResponse from(ParkingLot lot) {
        return AdminParkingLotResponse.builder()
                .id(lot.getId())
                .merchantId(lot.getMerchantId())
                .name(lot.getName())
                .address(lot.getAddress())
                .latitude(lot.getLatitude())
                .longitude(lot.getLongitude())
                .status(lot.getStatus())
                .createdAt(lot.getCreatedAt())
                .build();
    }
}
