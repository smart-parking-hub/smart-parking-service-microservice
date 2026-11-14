package com.smartparking.payment.api.controller;

import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.dto.PageResponse;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.payment.application.dto.MerchantLedgerEntryResponse;
import com.smartparking.payment.application.service.MerchantLedgerQueryService;
import com.smartparking.payment.infrastructure.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping("/api/v1/merchant/revenue/ledger")
@PreAuthorize("hasRole('MERCHANT')")
@RequiredArgsConstructor
public class MerchantLedgerController {
    private final MerchantLedgerQueryService ledgerQueryService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<MerchantLedgerEntryResponse>>> getLedger(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                ledgerQueryService.getLedger(user.getId(), page, size)));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidPaging(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.badRequest().body(ApiResponse.error(
                ErrorCode.BAD_REQUEST.getCode(), "Trang và kích thước trang phải là số nguyên hợp lệ"));
    }
}
