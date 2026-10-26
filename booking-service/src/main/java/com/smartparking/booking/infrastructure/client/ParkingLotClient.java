package com.smartparking.booking.infrastructure.client;

import com.smartparking.common.dto.ApiResponse;
import com.smartparking.booking.infrastructure.client.dto.SlotHoldRequest;
import com.smartparking.booking.infrastructure.client.dto.SlotHoldResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "parking-lot-service")
public interface ParkingLotClient {

    @PostMapping("/api/v1/internal/parking-lots/{id}/slots/hold")
    ApiResponse<SlotHoldResponse> holdSlot(
            @PathVariable("id") Long parkingLotId,
            @RequestBody SlotHoldRequest request);

    @PostMapping("/api/v1/internal/parking-lots/{id}/slots/release")
    ApiResponse<Void> releaseSlot(
            @PathVariable("id") Long parkingLotId,
            @RequestBody SlotHoldRequest request);

    @PostMapping("/api/v1/internal/parking-lots/{id}/rating")
    ApiResponse<Void> updateRating(
            @PathVariable("id") Long parkingLotId,
            @org.springframework.web.bind.annotation.RequestParam("rating") Double rating);
}
