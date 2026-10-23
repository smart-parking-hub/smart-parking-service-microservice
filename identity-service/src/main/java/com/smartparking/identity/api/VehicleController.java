package com.smartparking.identity.api;

import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.dto.PageResponse;
import com.smartparking.identity.application.dto.AddVehicleRequest;
import com.smartparking.identity.application.dto.UpdateVehicleRequest;
import com.smartparking.identity.application.dto.VehicleDto;
import com.smartparking.identity.application.service.VehicleService;
import com.smartparking.identity.infrastructure.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/vehicles")
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleService vehicleService;

    @GetMapping
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<PageResponse<VehicleDto>>> getMyVehicles(
            @AuthenticationPrincipal CustomUserDetails user,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(vehicleService.getMyVehicles(user.getId(), pageable)));
    }

    @PostMapping
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<VehicleDto>> addVehicle(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody AddVehicleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Thêm xe thành công", vehicleService.addVehicle(user.getId(), request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<VehicleDto>> updateVehicle(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long id,
            @Valid @RequestBody UpdateVehicleRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật xe thành công",
                vehicleService.updateVehicle(user.getId(), id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<Void>> deleteVehicle(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long id) {
        vehicleService.deleteVehicle(user.getId(), id);
        return ResponseEntity.ok(ApiResponse.ok("Đã xóa xe", null));
    }
}
