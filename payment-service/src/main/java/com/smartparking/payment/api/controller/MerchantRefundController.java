package com.smartparking.payment.api.controller;

import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.dto.PageResponse;
import com.smartparking.payment.application.dto.RefundRequestDto;
import com.smartparking.payment.application.dto.SubmitRefundEvidenceRequest;
import com.smartparking.payment.application.service.RefundService;
import com.smartparking.payment.infrastructure.security.AuthenticatedUser;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/merchant/refunds")
@PreAuthorize("hasRole('MERCHANT')")
@RequiredArgsConstructor
public class MerchantRefundController {

    private final RefundService refundService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<RefundRequestDto>>> getMerchantRefunds(
            @AuthenticationPrincipal AuthenticatedUser user,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                refundService.getMerchantRefunds(user.getId(), pageable),
                "Lấy danh sách yêu cầu hoàn tiền thành công"));
    }

    @PostMapping("/{bookingId}/evidence")
    public ResponseEntity<ApiResponse<RefundRequestDto>> submitTransferEvidence(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long bookingId,
            @RequestBody(required = false) SubmitRefundEvidenceRequest request) {
        RefundRequestDto result = refundService.submitTransferEvidence(user.getId(), bookingId, request);
        return ResponseEntity.ok(ApiResponse.success(result, "Cập nhật bằng chứng hoàn tiền thành công"));
    }
}

