package com.smartparking.payment.api.controller;

import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.dto.PageResponse;
import com.smartparking.payment.application.dto.*;
import com.smartparking.payment.application.service.MerchantWalletService;
import com.smartparking.payment.application.service.PaymentService;
import com.smartparking.payment.infrastructure.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;
    private final MerchantWalletService merchantWalletService;

    @PostMapping
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<String>> createPayment(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreatePaymentRequest request) {
        String paymentUrl = paymentService.createPaymentLink(user.getId(), request);
        return ResponseEntity.ok(ApiResponse.success(paymentUrl, "Payment link created successfully"));
    }

    @PostMapping("/demo-confirm")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<PaymentDto>> confirmDemoPayment(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody DemoPaymentRequest request) {
        PaymentDto payment;
        if (request.getAmount() != null || request.getMerchantId() != null || request.getParkingLotId() != null) {
            payment = paymentService.confirmDemoDeposit(user.getId(), request);
        } else {
            payment = paymentService.confirmDemoDeposit(user.getId(), request.getBookingId());
        }
        return ResponseEntity.ok(ApiResponse.success(payment, "Demo payment confirmed"));
    }

    @PostMapping("/sync")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<PaymentDto>> syncPayOSPayment(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody(required = false) SyncPaymentRequest request) {
        if (request == null) {
            request = new SyncPaymentRequest();
        }
        PaymentDto payment = paymentService.syncPayOSPayment(
                user.getId(),
                request.getOrderCode(),
                request.getBookingId(),
                request.getPaymentLinkId());
        return ResponseEntity.ok(ApiResponse.success(payment, "Payment synced"));
    }

    @GetMapping
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<PageResponse<PaymentDto>>> getMyPayments(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(value = "bookingId", required = false) Long bookingId,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.getMyPayments(user.getId(), bookingId, pageable)));
    }

    @PostMapping("/webhook")
    public ResponseEntity<ApiResponse<String>> handleWebhook(
            @RequestBody(required = false) String rawBody) {
        log.info("PayOS webhook received");
        paymentService.handleWebhook(rawBody);
        return ResponseEntity.ok(ApiResponse.success(null, "Webhook processed"));
    }

    // --- Internal Endpoints for Feign / Inter-Service RPC ---

    @GetMapping("/internal/merchants/{merchantId}/revenue")
    public ResponseEntity<ApiResponse<MerchantRevenueResponse>> getMerchantRevenue(
            @PathVariable Long merchantId) {
        return ResponseEntity.ok(ApiResponse.success(merchantWalletService.getRevenueSummary(merchantId)));
    }
}
