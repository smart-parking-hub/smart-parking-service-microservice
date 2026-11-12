package com.smartparking.parkinglot.api.controller;

import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.dto.PageResponse;
import com.smartparking.common.enums.ParkingLotStatusEnum;
import com.smartparking.common.enums.VehicleTypeEnum;
import com.smartparking.parkinglot.application.dto.*;
import com.smartparking.parkinglot.application.service.ParkingLotService;
import com.smartparking.parkinglot.application.service.SlotService;
import com.smartparking.parkinglot.infrastructure.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Validated
public class ParkingLotController {

    private final ParkingLotService parkingLotService;
    private final SlotService slotService;

    // --- Merchant Parking Lot Management ---

    @PostMapping("/api/v1/merchant/parking-lots")
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<ApiResponse<ParkingLotDto>> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateParkingLotRequest request) {
        ParkingLotDto created = parkingLotService.createParkingLot(user.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created, "Tạo bãi thành công"));
    }

    @PutMapping("/api/v1/merchant/parking-lots/{id}")
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<ApiResponse<ParkingLotDto>> update(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @Valid @RequestBody CreateParkingLotRequest request) {
        ParkingLotDto updated = parkingLotService.updateParkingLot(user.getId(), id, request);
        return ResponseEntity.ok(ApiResponse.success(updated, "Cập nhật hồ sơ bãi thành công"));
    }

    @GetMapping("/api/v1/merchant/parking-lots")
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<ApiResponse<PageResponse<MerchantParkingLotListResponse>>> getMerchantParkingLots(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(required = false) ParkingLotStatusEnum status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        PageResponse<MerchantParkingLotListResponse> response =
                parkingLotService.getMerchantParkingLots(user.getId(), status, keyword, page, size, sort);
        return ResponseEntity.ok(ApiResponse.success(response, "Lấy danh sách bãi thành công"));
    }

    @GetMapping("/api/v1/merchant/parking-lots/{id}")
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<ApiResponse<ParkingLotDto>> merchantDetail(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(parkingLotService.getMerchantDetail(user.getId(), id)));
    }

    @GetMapping("/api/v1/merchant/parking-lots/{id}/revenue")
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> getMerchantParkingLotRevenue(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        parkingLotService.getMerchantDetail(user.getId(), id);
        java.util.Map<String, Object> revenue = new java.util.HashMap<>();
        revenue.put("totalRevenue", java.math.BigDecimal.ZERO);
        revenue.put("completedBookings", 0);
        return ResponseEntity.ok(ApiResponse.success(revenue));
    }

    // --- Merchant Slot & Pricing Configuration ---

    @PutMapping("/api/v1/merchant/parking-lots/{id}/slots")
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<ApiResponse<SlotDto>> configSlot(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @Valid @RequestBody SlotConfigRequest request) {
        SlotDto dto = slotService.configureSlot(user.getId(), id, request);
        return ResponseEntity.ok(ApiResponse.success(dto, "Cập nhật slot thành công"));
    }

    @PutMapping("/api/v1/merchant/parking-lots/{id}/slots/bulk")
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<ApiResponse<List<SlotDto>>> configSlots(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @Valid @RequestBody BulkSlotConfigRequest request) {
        List<SlotDto> dtos = slotService.configureSlots(user.getId(), id, request.getSlots());
        return ResponseEntity.ok(ApiResponse.success(dtos, "Cập nhật toàn bộ slot thành công"));
    }

    @PutMapping("/api/v1/merchant/parking-lots/{id}/pricing")
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<ApiResponse<PricingRuleDto>> configPricing(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @Valid @RequestBody PricingRuleRequest request) {
        PricingRuleDto dto = slotService.configurePricing(user.getId(), id, request);
        return ResponseEntity.ok(ApiResponse.success(dto, "Cập nhật giá thành công"));
    }

    @PutMapping("/api/v1/merchant/parking-lots/{id}/pricing/bulk")
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<ApiResponse<List<PricingRuleDto>>> configPricingRules(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @Valid @RequestBody BulkPricingRuleRequest request) {
        List<PricingRuleDto> dtos = slotService.configurePricingRules(user.getId(), id, request.getPricingRules());
        return ResponseEntity.ok(ApiResponse.success(dtos, "Cập nhật toàn bộ bảng giá thành công"));
    }

    // --- Public / Driver Endpoints ---

    @GetMapping("/api/v1/parking-lots/search")
    public ResponseEntity<ApiResponse<PageResponse<ParkingLotSearchResponse>>> search(
            @RequestParam(required = false) VehicleTypeEnum vehicleType,
            @RequestParam(defaultValue = "false") boolean includeFull,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Double radiusKm,
            Pageable pageable) {
        PageResponse<ParkingLotSearchResponse> response =
                parkingLotService.searchParkingLots(vehicleType, includeFull, latitude, longitude, radiusKm, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/api/v1/parking-lots/{id}")
    public ResponseEntity<ApiResponse<ParkingLotDto>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(parkingLotService.getDetail(id)));
    }

    @GetMapping("/api/v1/parking-lots/{id}/slots")
    public ResponseEntity<ApiResponse<List<SlotDto>>> getSlots(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(slotService.getSlots(id)));
    }

    @GetMapping("/api/v1/parking-lots/{id}/pricing")
    public ResponseEntity<ApiResponse<List<PricingRuleDto>>> getPricing(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(slotService.getPricingRules(id)));
    }

    // --- Internal RPC Endpoints (For BookingService) ---

    @PostMapping("/api/v1/internal/parking-lots/{id}/slots/hold")
    public ResponseEntity<ApiResponse<SlotHoldResponse>> holdSlot(
            @PathVariable Long id,
            @Valid @RequestBody SlotHoldRequest request) {
        SlotHoldResponse response = slotService.holdSlot(id, request.getVehicleType(), request.getBookingId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/api/v1/internal/parking-lots/{id}/slots/release")
    public ResponseEntity<ApiResponse<Void>> releaseSlot(
            @PathVariable Long id,
            @Valid @RequestBody SlotHoldRequest request) {
        slotService.releaseSlot(id, request.getVehicleType(), request.getBookingId());
        return ResponseEntity.ok(ApiResponse.success(null, "Giải phóng slot thành công"));
    }

    @PostMapping("/api/v1/internal/parking-lots/{id}/rating")
    public ResponseEntity<ApiResponse<Void>> updateRating(
            @PathVariable Long id,
            @RequestParam("rating") Double rating) {
        parkingLotService.updateRating(id, rating);
        return ResponseEntity.ok(ApiResponse.success(null, "Cập nhật đánh giá thành công"));
    }
}
