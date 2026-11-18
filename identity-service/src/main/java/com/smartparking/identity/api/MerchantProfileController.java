package com.smartparking.identity.api;

import com.smartparking.common.dto.ApiResponse;
import com.smartparking.identity.application.dto.MerchantProfileResponse;
import com.smartparking.identity.application.dto.UpdateMerchantProfileRequest;
import com.smartparking.identity.application.service.MerchantProfileService;
import com.smartparking.identity.infrastructure.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/merchant/profile")
@RequiredArgsConstructor
public class MerchantProfileController {

    private final MerchantProfileService merchantProfileService;

    @GetMapping
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<ApiResponse<MerchantProfileResponse>> getProfile(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(merchantProfileService.getProfile(user.getId())));
    }

    @PutMapping
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<ApiResponse<MerchantProfileResponse>> updateProfile(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody UpdateMerchantProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật hồ sơ thành công",
                merchantProfileService.updateProfile(user.getId(), request)));
    }

    @PostMapping(path = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<ApiResponse<MerchantProfileResponse>> uploadDocuments(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) String businessLicense,
            @RequestParam(required = false) MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.ok("Đã lưu giấy tờ vào bản nháp hồ sơ xác minh",
                merchantProfileService.uploadDocument(user.getId(), businessLicense, file)));
    }
}
