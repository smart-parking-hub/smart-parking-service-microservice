package com.smartparking.parkinglot.api.controller;

import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.dto.PageResponse;
import com.smartparking.parkinglot.application.dto.AdminDecisionRequest;
import com.smartparking.parkinglot.application.dto.AdminParkingLotDetailResponse;
import com.smartparking.parkinglot.application.dto.AdminParkingLotResponse;
import com.smartparking.parkinglot.application.service.ParkingLotService;
import com.smartparking.parkinglot.infrastructure.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/parking-lots")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminParkingLotController {

    private final ParkingLotService parkingLotService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminParkingLotResponse>>> getAllParkingLots(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(parkingLotService.getAllParkingLotsForAdmin(pageable), "Lấy danh sách bãi thành công"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminParkingLotDetailResponse>> getParkingLotDetail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(parkingLotService.getParkingLotDetailForAdmin(id), "Lấy chi tiết bãi thành công"));
    }

    @PutMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<Void>> approveParkingLot(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) AdminDecisionRequest request) {
        String reason = request != null ? request.getReason() : null;
        parkingLotService.approveParkingLot(user.getId(), id, reason);
        return ResponseEntity.ok(ApiResponse.success(null, "Duyệt bãi đỗ thành công"));
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<Void>> rejectParkingLot(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) AdminDecisionRequest request) {
        String reason = request != null ? request.getReason() : null;
        parkingLotService.rejectParkingLot(user.getId(), id, reason);
        return ResponseEntity.ok(ApiResponse.success(null, "Từ chối bãi đỗ thành công"));
    }

    @PutMapping("/{id}/request-changes")
    public ResponseEntity<ApiResponse<Void>> requestParkingLotChanges(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) AdminDecisionRequest request) {
        String reason = request != null ? request.getReason() : null;
        parkingLotService.requestParkingLotChanges(user.getId(), id, reason);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã yêu cầu cập nhật hồ sơ bãi"));
    }
}
