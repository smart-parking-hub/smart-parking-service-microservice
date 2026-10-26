package com.smartparking.ai.infrastructure.client;

import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.dto.PageResponse;
import com.smartparking.common.enums.VehicleTypeEnum;
import com.smartparking.ai.dto.ParkingLotSearchResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "parking-lot-service", url = "${services.parking-lot-service.url:http://localhost:8082}")
public interface ParkingLotFeignClient {

    @GetMapping("/api/v1/parking-lots/search")
    ApiResponse<PageResponse<ParkingLotSearchResponse>> searchParkingLots(
            @RequestParam("vehicleType") VehicleTypeEnum vehicleType,
            @RequestParam(value = "includeFull", defaultValue = "false") boolean includeFull,
            @RequestParam(value = "latitude", required = false) Double latitude,
            @RequestParam(value = "longitude", required = false) Double longitude,
            @RequestParam(value = "radiusKm", required = false) Double radiusKm,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size
    );
}
