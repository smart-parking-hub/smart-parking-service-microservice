package com.smartparking.parkinglot.api.controller;

import com.smartparking.common.dto.ApiResponse;
import com.smartparking.parkinglot.application.dto.*;
import com.smartparking.parkinglot.application.service.ParkingLayoutService;
import com.smartparking.parkinglot.infrastructure.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@Validated
public class ParkingLayoutController {

    private final ParkingLayoutService parkingLayoutService;

    // --- Merchant Layout Endpoints ---

    @PostMapping(value = "/api/v1/merchant/parking-lots/{id}/layout/sources", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<ApiResponse<SourceAssetUploadResponse>> uploadSource(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        SourceAssetUploadResponse response = parkingLayoutService.uploadSourceImage(user.getId(), id, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "Tải ảnh nguồn thành công"));
    }

    @GetMapping("/api/v1/merchant/parking-lots/{id}/layout/draft")
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<ApiResponse<LayoutDraftResponse>> getDraft(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        LayoutDraftResponse response = parkingLayoutService.getDraft(user.getId(), id);
        return ResponseEntity.ok(ApiResponse.success(response, "Lấy bản nháp thành công"));
    }

    @PutMapping("/api/v1/merchant/parking-lots/{id}/layout/draft")
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<ApiResponse<LayoutDraftResponse>> saveDraft(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @Valid @RequestBody SaveLayoutDraftRequest request) {
        LayoutDraftResponse response = parkingLayoutService.saveDraft(user.getId(), id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Lưu bản nháp thành công"));
    }

    @PostMapping("/api/v1/merchant/parking-lots/{id}/layout/publish")
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<ApiResponse<PublishLayoutResponse>> publish(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @Valid @RequestBody PublishLayoutRequest request) {
        PublishLayoutResponse response = parkingLayoutService.publishLayout(user.getId(), id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Công bố sơ đồ bãi xe thành công"));
    }

    @PostMapping("/api/v1/merchant/parking-lots/{id}/layout/activate-physical")
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<ApiResponse<Void>> activatePhysical(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        parkingLayoutService.activatePhysicalMode(user.getId(), id);
        return ResponseEntity.ok(ApiResponse.success(null, "Kích hoạt chế độ chọn ô vật lý thành công"));
    }

    @GetMapping("/api/v1/merchant/parking-lots/{id}/layout/sources/{assetId}/content")
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<byte[]> getSourceContent(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @PathVariable String assetId) {
        byte[] bytes = parkingLayoutService.getSourceImageContent(user.getId(), id, assetId);
        MediaType mediaType = MediaType.IMAGE_PNG;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, mediaType.toString())
                .body(bytes);
    }

    // --- Public Layout Endpoints ---

    @GetMapping("/api/v1/parking-lots/{id}/layout")
    public ResponseEntity<ApiResponse<PublicLayoutResponse>> getPublicLayout(@PathVariable Long id) {
        PublicLayoutResponse response = parkingLayoutService.getPublicLayout(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/api/v1/parking-lots/{id}/layout/background")
    public ResponseEntity<byte[]> getPublicBackground(@PathVariable Long id) {
        byte[] bytes = parkingLayoutService.getPublicBackground(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE)
                .body(bytes);
    }

    @GetMapping("/api/v1/parking-lots/{id}/layout/availability")
    public ResponseEntity<ApiResponse<PublicAvailabilityResponse>> getPublicAvailability(@PathVariable Long id) {
        PublicAvailabilityResponse response = parkingLayoutService.getPublicAvailability(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @ExceptionHandler(com.smartparking.common.exception.AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppException(com.smartparking.common.exception.AppException ex) {
        HttpStatus status = switch (ex.getErrorCode()) {
            case BAD_REQUEST, LAYOUT_INVALID_CAPACITY -> HttpStatus.BAD_REQUEST;
            case UNAUTHORIZED, INVALID_CREDENTIALS, TOKEN_EXPIRED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND, USER_NOT_FOUND, PARKING_LOT_NOT_FOUND, LAYOUT_NOT_PUBLISHED, PHYSICAL_SLOT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT, SLOT_NOT_AVAILABLE, LAYOUT_VERSION_CONFLICT, PHYSICAL_SLOT_ALREADY_HELD -> HttpStatus.CONFLICT;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status)
                .body(ApiResponse.error(ex.getErrorCode().getCode(), ex.getMessage()));
    }
}
