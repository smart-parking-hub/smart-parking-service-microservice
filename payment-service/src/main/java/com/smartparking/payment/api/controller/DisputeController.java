package com.smartparking.payment.api.controller;

import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.dto.PageResponse;
import com.smartparking.payment.application.dto.AdminDisputeResponse;
import com.smartparking.payment.application.dto.CreateDisputeRequest;
import com.smartparking.payment.application.dto.DisputeDto;
import com.smartparking.payment.application.dto.ResolveDisputeRequest;
import com.smartparking.payment.application.service.DisputeService;
import com.smartparking.payment.infrastructure.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/disputes")
@RequiredArgsConstructor
public class DisputeController {

    private final DisputeService disputeService;

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
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(disputeService.getMyDisputes(user.getId(), pageable)));
    }

    @GetMapping("/merchant")
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<ApiResponse<PageResponse<DisputeDto>>> getMerchantDisputes(
            @AuthenticationPrincipal AuthenticatedUser user,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(disputeService.getMerchantDisputes(user.getId(), pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('DRIVER','MERCHANT')")
    public ResponseEntity<ApiResponse<DisputeDto>> getDisputeDetail(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(disputeService.getDisputeDetail(user.getId(), id)));
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
