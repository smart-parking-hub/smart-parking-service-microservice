package com.smartparking.payment.api.controller;

import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.dto.PageResponse;
import com.smartparking.payment.application.dto.AdminDisputeResponse;
import com.smartparking.payment.application.dto.CreateDisputeRequest;
import com.smartparking.payment.application.dto.CreateDisputeResponseRequest;
import com.smartparking.payment.application.dto.DisputeDetailDto;
import com.smartparking.payment.application.dto.DisputeDto;
import com.smartparking.payment.application.dto.DisputeEvidenceResponse;
import com.smartparking.payment.application.dto.DisputeTimelineEntryDto;
import com.smartparking.payment.application.dto.LegacyDisputeDto;
import com.smartparking.payment.application.dto.ResolveDisputeRequest;
import com.smartparking.payment.application.service.DisputeEvidenceService;
import com.smartparking.payment.application.service.DisputeService;
import com.smartparking.payment.infrastructure.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/disputes")
@RequiredArgsConstructor
@Validated
public class DisputeController {

    private final DisputeService disputeService;
    private final DisputeEvidenceService disputeEvidenceService;

    @PostMapping(value = "/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<List<DisputeEvidenceResponse>>> uploadEvidence(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestPart("files") @Size(min = 1, max = 5, message = "Chỉ được tải lên từ 1 đến 5 ảnh mỗi lần") List<MultipartFile> files) {
        List<DisputeEvidenceResponse> uploaded =
                disputeEvidenceService.upload(user.getId(), files, resolveRequestBaseUrl());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(uploaded, "Tải lên ảnh chứng từ thành công"));
    }

    @GetMapping("/evidence/{fileName:.+}")
    @PreAuthorize("hasAnyRole('DRIVER','MERCHANT','ADMIN')")
    public ResponseEntity<Resource> getEvidenceContent(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String fileName) {
        DisputeEvidenceService.StoredEvidence stored =
                disputeEvidenceService.getContent(user.getId(), user.getRole(), fileName);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(stored.contentType()))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(stored.originalFileName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(stored.resource());
    }

    private String resolveRequestBaseUrl() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes) {
            return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        }
        return "";
    }

    @PostMapping
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<DisputeDto>> createDispute(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateDisputeRequest request) {
        DisputeDto dto = disputeService.createDispute(user.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(dto, "Tạo khiếu nại thành công"));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<PageResponse<DisputeDto>>> getMyDisputes(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.success(
                disputeService.getMyDisputes(user.getId(), page, size, status)));
    }

    @GetMapping("/me/{id}")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<DisputeDetailDto>> getMyDisputeDetail(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                disputeService.getMyDisputeDetail(user.getId(), id)));
    }

    @PostMapping("/{id}/responses")
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<ApiResponse<DisputeTimelineEntryDto>> addMerchantResponse(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @Valid @RequestBody CreateDisputeResponseRequest request) {
        DisputeTimelineEntryDto entry = disputeService.addMerchantResponse(user.getId(), id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(entry, "Gửi phản hồi khiếu nại thành công"));
    }

    @GetMapping("/merchant")
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<ApiResponse<PageResponse<LegacyDisputeDto>>> getMerchantDisputes(
            @AuthenticationPrincipal AuthenticatedUser user,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(disputeService.getMerchantDisputes(user.getId(), pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('DRIVER','MERCHANT')")
    public ResponseEntity<ApiResponse<LegacyDisputeDto>> getDisputeDetail(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                disputeService.getDisputeDetail(user.getId(), user.getRole(), id)));
    }

    @PutMapping("/{id}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdminDisputeResponse>> resolveDispute(
            @AuthenticationPrincipal AuthenticatedUser admin,
            @PathVariable Long id,
            @Valid @RequestBody ResolveDisputeRequest request) {
        AdminDisputeResponse response = disputeService.resolveDispute(admin.getId(), id, request.getResolution());
        return ResponseEntity.ok(ApiResponse.success(response, "Đã giải quyết khiếu nại"));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<AdminDisputeResponse>>> adminList(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(disputeService.getAdminDisputes(pageable)));
    }

    @GetMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdminDisputeResponse>> adminDetail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(disputeService.getAdminDispute(id)));
    }
}
