package com.smartparking.ai.infrastructure.tools;

import com.smartparking.ai.infrastructure.client.ParkingLotFeignClient;
import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.dto.PageResponse;
import com.smartparking.common.enums.VehicleTypeEnum;
import com.smartparking.ai.dto.ParkingLotSearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ParkingLotTools {

    private final ParkingLotFeignClient parkingLotClient;

    public List<ParkingLotSearchResponse> findAvailableParkingLots(VehicleTypeEnum vehicleType, Double latitude, Double longitude, Double radiusKm) {
        log.info("Executing Tool: findAvailableParkingLots for vehicleType={}, lat={}, lon={}, radius={}", 
                vehicleType, latitude, longitude, radiusKm);
        try {
            ApiResponse<PageResponse<ParkingLotSearchResponse>> response = parkingLotClient.searchParkingLots(
                    vehicleType != null ? vehicleType : VehicleTypeEnum.SEDAN_SUV,
                    false,
                    latitude,
                    longitude,
                    radiusKm != null ? radiusKm : 10.0,
                    0,
                    5
            );

            if (response != null && response.getData() != null && response.getData().getContent() != null) {
                return response.getData().getContent();
            }
        } catch (Exception e) {
            log.warn("ParkingLotFeignClient call failed: {}", e.getMessage());
        }
        return Collections.emptyList();
    }
}
