package com.smartparking.ai.infrastructure.tools;

import com.smartparking.ai.dto.ParkingLotDetailResponse;
import com.smartparking.ai.dto.ParkingLotSearchResponse;
import com.smartparking.ai.infrastructure.client.ParkingLotFeignClient;
import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.dto.PageResponse;
import com.smartparking.common.enums.VehicleTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

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

    public Optional<ParkingLotDetailResponse> getParkingLotDetail(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        log.info("Executing Tool: getParkingLotDetail for lotId={}", id);
        try {
            ApiResponse<ParkingLotDetailResponse> response = parkingLotClient.getParkingLotDetail(id);
            if (response != null && response.getData() != null) {
                return Optional.of(response.getData());
            }
        } catch (Exception e) {
            log.warn("ParkingLotFeignClient getParkingLotDetail failed for lotId={}: {}", id, e.getMessage());
        }
        return Optional.empty();
    }

    public List<ParkingLotSearchResponse> getAllApprovedParkingLots() {
        log.info("Executing Tool: getAllApprovedParkingLots");
        try {
            ApiResponse<PageResponse<ParkingLotSearchResponse>> response = parkingLotClient.searchParkingLots(
                    VehicleTypeEnum.SEDAN_SUV,
                    true,
                    null,
                    null,
                    null,
                    0,
                    50
            );

            if (response != null && response.getData() != null && response.getData().getContent() != null) {
                return response.getData().getContent();
            }
        } catch (Exception e) {
            log.warn("ParkingLotFeignClient getAllApprovedParkingLots failed: {}", e.getMessage());
        }
        return Collections.emptyList();
    }
}
