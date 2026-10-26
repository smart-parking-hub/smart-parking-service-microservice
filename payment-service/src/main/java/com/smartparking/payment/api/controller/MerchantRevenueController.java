package com.smartparking.payment.api.controller;

import com.smartparking.common.dto.ApiResponse;
import com.smartparking.payment.application.dto.MerchantRevenueResponse;
import com.smartparking.payment.application.service.MerchantWalletService;
import com.smartparking.payment.infrastructure.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/v1/merchant/revenue", "/api/merchant/revenue"})
@RequiredArgsConstructor
@Slf4j
public class MerchantRevenueController {

    private final MerchantWalletService merchantWalletService;

    @GetMapping
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<ApiResponse<MerchantRevenueResponse>> getMerchantRevenue(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "DAY") String granularity) {
        log.info("Merchant {} requested revenue summary", user.getId());
        MerchantRevenueResponse response = merchantWalletService.getRevenueSummary(user.getId());
        return ResponseEntity.ok(ApiResponse.success(response, "Lấy thông tin doanh thu thành công"));
    }
}
