package com.smartparking.payment.api.controller;

import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.dto.PageResponse;
import com.smartparking.payment.application.dto.CreateWithdrawalRequestDto;
import com.smartparking.payment.application.dto.WithdrawalResponse;
import com.smartparking.payment.application.service.WithdrawalService;
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
@RequestMapping("/api/v1/withdrawals")
@RequiredArgsConstructor
public class WithdrawalController {

    private final WithdrawalService withdrawalService;

    @PostMapping
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<ApiResponse<WithdrawalResponse>> requestWithdrawal(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateWithdrawalRequestDto request) {
        WithdrawalResponse response = withdrawalService.requestWithdrawal(user.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Yêu cầu rút tiền đã được ghi nhận"));
    }

    @GetMapping
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<ApiResponse<PageResponse<WithdrawalResponse>>> getMyWithdrawals(
            @AuthenticationPrincipal AuthenticatedUser user,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(withdrawalService.getMyWithdrawals(user.getId(), pageable)));
    }

    @PutMapping("/{id}/process")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<WithdrawalResponse>> processWithdrawal(
            @AuthenticationPrincipal AuthenticatedUser admin,
            @PathVariable Long id,
            @RequestParam boolean approved,
            @RequestParam(required = false) String reason) {
        WithdrawalResponse response = withdrawalService.processWithdrawal(admin.getId(), id, approved, reason);
        return ResponseEntity.ok(ApiResponse.success(response, "Đã xử lý yêu cầu rút tiền"));
    }
}
