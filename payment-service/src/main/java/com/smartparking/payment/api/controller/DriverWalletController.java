package com.smartparking.payment.api.controller;

import com.smartparking.common.dto.*;
import com.smartparking.payment.application.dto.PaymentDto;
import com.smartparking.payment.application.service.*;
import com.smartparking.payment.infrastructure.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('DRIVER')")
public class DriverWalletController {
    private final DriverWalletLedger wallet;
    private final DriverWalletPaymentService payments;
    public record WalletPaymentRequest(@NotNull @Positive Long bookingId) {}

    @GetMapping("/api/v1/driver/wallet")
    public ApiResponse<DriverWalletLedger.Balance> balance(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(wallet.balance(user.getId(), payments.enabled()));
    }
    @GetMapping("/api/v1/driver/wallet/transactions")
    public ApiResponse<PageResponse<DriverWalletLedger.Entry>> entries(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(wallet.entries(user.getId(), page, size));
    }
    @PostMapping("/api/v1/payments/wallet")
    public ApiResponse<PaymentDto> pay(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody WalletPaymentRequest request) {
        return ApiResponse.success(payments.pay(user.getId(), request.bookingId()));
    }
    @GetMapping("/api/v1/payments/wallet")
    public ApiResponse<PaymentDto> paymentForBooking(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam Long bookingId) {
        return ApiResponse.success(payments.getForBooking(user.getId(), bookingId));
    }
    @GetMapping("/api/v1/payments/{id}")
    public ApiResponse<PaymentDto> payment(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return ApiResponse.success(payments.get(user.getId(), id));
    }
}
