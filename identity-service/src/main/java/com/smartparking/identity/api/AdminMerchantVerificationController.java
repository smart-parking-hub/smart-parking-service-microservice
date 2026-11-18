package com.smartparking.identity.api;

import com.smartparking.common.dto.ApiResponse;
import com.smartparking.identity.application.dto.verification.*;
import com.smartparking.identity.application.service.verification.MerchantTaxLookupService;
import com.smartparking.identity.application.service.verification.MerchantVerificationService;
import com.smartparking.identity.domain.model.verification.VerificationStatus;
import com.smartparking.identity.infrastructure.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/merchant-verifications")
@RequiredArgsConstructor
public class AdminMerchantVerificationController {

    private final MerchantVerificationService verificationService;
    private final MerchantTaxLookupService taxLookupService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<AdminVerificationSummaryResponse>>> listVerifications(
            @RequestParam(required = false) VerificationStatus status,
            @PageableDefault(size = 20, sort = {"submittedAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(verificationService.getAdminVerifications(status, pageable)));
    }

    @GetMapping("/{dossierId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MerchantVerificationDetailResponse>> getVerificationDetail(
            @PathVariable Long dossierId) {
        return ResponseEntity.ok(ApiResponse.ok(verificationService.getAdminVerificationDetail(dossierId)));
    }

    @PostMapping("/{dossierId}/submissions/{submissionVersion}/tax-lookup")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdminTaxLookupResponse>> lookupTaxCode(
            @PathVariable Long dossierId,
            @PathVariable Integer submissionVersion,
            @AuthenticationPrincipal CustomUserDetails adminUser,
            @RequestBody AdminTaxLookupRequest request) {
        var result = taxLookupService.lookup(dossierId, submissionVersion, adminUser.getId(), request);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(ApiResponse.ok(result));
    }

    @GetMapping("/{dossierId}/submissions/{submissionVersion}/documents/{documentId}/content")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> getDocumentContent(
            @PathVariable Long dossierId,
            @PathVariable Integer submissionVersion,
            @PathVariable String documentId) {
        MerchantVerificationService.DocumentContentResponse response =
                verificationService.getAdminDocumentContent(dossierId, submissionVersion, documentId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, response.contentType())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + response.originalName() + "\"")
                .body(response.content());
    }

    @PostMapping("/{dossierId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MerchantVerificationDetailResponse>> approve(
            @PathVariable Long dossierId,
            @AuthenticationPrincipal CustomUserDetails adminUser,
            @Valid @RequestBody AdminApproveVerificationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Phê duyệt hồ sơ xác minh thành công",
                verificationService.adminApprove(dossierId, adminUser, request)));
    }

    @PostMapping("/{dossierId}/request-changes")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MerchantVerificationDetailResponse>> requestChanges(
            @PathVariable Long dossierId,
            @AuthenticationPrincipal CustomUserDetails adminUser,
            @Valid @RequestBody AdminRequestChangesRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Yêu cầu bổ sung hồ sơ thành công",
                verificationService.adminRequestChanges(dossierId, adminUser, request)));
    }
}
