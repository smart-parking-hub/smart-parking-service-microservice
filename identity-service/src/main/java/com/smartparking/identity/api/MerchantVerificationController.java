package com.smartparking.identity.api;

import com.smartparking.common.dto.ApiResponse;
import com.smartparking.identity.application.dto.verification.MerchantVerificationDetailResponse;
import com.smartparking.identity.application.dto.verification.SubmitVerificationRequest;
import com.smartparking.identity.application.dto.verification.UpdateVerificationDetailsRequest;
import com.smartparking.identity.application.service.verification.MerchantVerificationService;
import com.smartparking.identity.infrastructure.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/merchant/profile/verification")
@RequiredArgsConstructor
public class MerchantVerificationController {

    private final MerchantVerificationService verificationService;

    @GetMapping
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<ApiResponse<MerchantVerificationDetailResponse>> getVerification(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(verificationService.getMerchantVerification(user.getId())));
    }

    @PutMapping("/details")
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<ApiResponse<MerchantVerificationDetailResponse>> updateDetails(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody UpdateVerificationDetailsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật thông tin xác minh thành công",
                verificationService.updateVerificationDetails(user.getId(), request)));
    }

    @PutMapping(path = "/documents/{documentType}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<ApiResponse<MerchantVerificationDetailResponse>> replaceDocuments(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable String documentType,
            @RequestParam Long draftRevision,
            @RequestParam("files") List<MultipartFile> files) {
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật tài liệu thành công",
                verificationService.replaceDocuments(user.getId(), documentType, draftRevision, files)));
    }

    @DeleteMapping("/documents/{documentType}")
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<ApiResponse<MerchantVerificationDetailResponse>> deleteDocuments(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable String documentType,
            @RequestParam Long draftRevision) {
        return ResponseEntity.ok(ApiResponse.ok("Xóa tài liệu thành công",
                verificationService.deleteDocuments(user.getId(), documentType, draftRevision)));
    }

    @PostMapping("/submit")
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<ApiResponse<MerchantVerificationDetailResponse>> submit(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody SubmitVerificationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Gửi hồ sơ xác minh thành công",
                verificationService.submitVerification(user.getId(), request)));
    }

    @GetMapping("/documents/{documentId}/content")
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<byte[]> getDocumentContent(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable String documentId) {
        MerchantVerificationService.DocumentContentResponse response =
                verificationService.getMerchantDocumentContent(user.getId(), documentId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, response.contentType())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + response.originalName() + "\"")
                .body(response.content());
    }
}
