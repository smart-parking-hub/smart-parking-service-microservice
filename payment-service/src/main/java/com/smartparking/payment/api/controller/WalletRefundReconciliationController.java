package com.smartparking.payment.api.controller;
import com.smartparking.common.dto.ApiResponse;
import com.smartparking.payment.application.dto.RefundRequestDto;
import com.smartparking.payment.application.service.impl.RefundServiceImpl;
import com.smartparking.payment.infrastructure.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class WalletRefundReconciliationController {
    private final RefundServiceImpl refunds;
    @PostMapping("/api/v1/admin/refunds/{id}/retry-wallet")
    public ApiResponse<RefundRequestDto> retry(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return ApiResponse.success(refunds.retryWalletRefund(id, user.getId()));
    }
}
