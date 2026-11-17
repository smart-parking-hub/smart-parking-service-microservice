package com.smartparking.payment.api.controller;

import com.smartparking.common.dto.*;
import com.smartparking.common.exception.*;
import com.smartparking.payment.application.service.CashCheckoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController @RequiredArgsConstructor
public class InternalCashCheckoutController {
    private final CashCheckoutService cash;
    @Value("${app.internal-service-token:}") private String serviceToken;
    @PostMapping("/api/v1/internal/payments/{id}/cash-checkout")
    public ApiResponse<Boolean> confirm(@PathVariable Long id,
            @RequestHeader(value = "X-Internal-Service-Token", required = false) String token,
            @RequestBody CashCheckoutCommand command) {
        if (serviceToken.isBlank() || token == null || !MessageDigest.isEqual(
                serviceToken.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8)))
            throw new AppException(ErrorCode.FORBIDDEN, "Không có quyền xác nhận nội bộ");
        return ApiResponse.ok(cash.confirm(id, command));
    }
}
