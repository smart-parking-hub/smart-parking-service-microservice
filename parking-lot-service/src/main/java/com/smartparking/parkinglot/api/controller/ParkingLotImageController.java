package com.smartparking.parkinglot.api.controller;

import com.smartparking.common.dto.ApiResponse;
import com.smartparking.parkinglot.application.dto.ParkingLotImageResponse;
import com.smartparking.parkinglot.application.service.ParkingLotImageService;
import com.smartparking.parkinglot.infrastructure.security.AuthenticatedUser;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Validated
public class ParkingLotImageController {

    private final ParkingLotImageService imageService;

    @PostMapping(value = "/api/v1/merchant/parking-lots/{parkingLotId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<ApiResponse<List<ParkingLotImageResponse>>> upload(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long parkingLotId,
            @RequestPart("files") @Size(min = 1, max = 5) List<MultipartFile> files) {
        List<ParkingLotImageResponse> responses = imageService.upload(user.getId(), parkingLotId, files);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(responses, "Upload ảnh thành công"));
    }

    @GetMapping("/api/v1/parking-lots/{parkingLotId}/images")
    public ResponseEntity<ApiResponse<List<ParkingLotImageResponse>>> list(@PathVariable Long parkingLotId) {
        return ResponseEntity.ok(ApiResponse.success(imageService.getImages(parkingLotId)));
    }

    @GetMapping("/api/v1/parking-lots/{parkingLotId}/images/{imageId}/content")
    public ResponseEntity<Resource> content(
            @PathVariable Long parkingLotId,
            @PathVariable Long imageId) {
        ParkingLotImageService.StoredImage image = imageService.getContent(parkingLotId, imageId);
        if (image.redirectUrl() != null && !image.redirectUrl().isBlank()) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(java.net.URI.create(image.redirectUrl()))
                    .build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(image.originalFileName(), StandardCharsets.UTF_8).build().toString())
                .body(image.resource());
    }

    @DeleteMapping("/api/v1/merchant/parking-lots/{parkingLotId}/images/{imageId}")
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long parkingLotId,
            @PathVariable Long imageId) {
        imageService.delete(user.getId(), parkingLotId, imageId);
        return ResponseEntity.ok(ApiResponse.success(null, "Xóa ảnh thành công"));
    }

    @PatchMapping("/api/v1/merchant/parking-lots/{parkingLotId}/images/{imageId}/cover")
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<ApiResponse<ParkingLotImageResponse>> setCover(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long parkingLotId,
            @PathVariable Long imageId) {
        ParkingLotImageResponse response = imageService.setCover(user.getId(), parkingLotId, imageId);
        return ResponseEntity.ok(ApiResponse.success(response, "Đặt ảnh đại diện thành công"));
    }
}
