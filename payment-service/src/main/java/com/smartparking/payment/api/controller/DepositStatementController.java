package com.smartparking.payment.api.controller;
import com.smartparking.common.dto.*;
import com.smartparking.payment.application.service.DepositStatementService;
import com.smartparking.payment.infrastructure.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class DepositStatementController {
    private final DepositStatementService statements;
    @GetMapping("/api/v1/merchant/revenue/deposits")
    @PreAuthorize("hasRole('MERCHANT')")
    public ApiResponse<PageResponse<DepositStatementService.Statement>> merchant(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return ApiResponse.success(statements.read(user.getId(), page, size));
    }
    @GetMapping("/api/v1/admin/deposits")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<DepositStatementService.Statement>> admin(
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return ApiResponse.success(statements.read(null, page, size));
    }
}
