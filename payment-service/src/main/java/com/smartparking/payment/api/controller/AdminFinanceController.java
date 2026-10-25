package com.smartparking.payment.api.controller;

import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.dto.PageResponse;
import com.smartparking.payment.application.dto.AdminDashboardResponse;
import com.smartparking.payment.application.dto.AdminDecisionRequest;
import com.smartparking.payment.application.dto.AdminTransactionResponse;
import com.smartparking.payment.application.dto.WithdrawalResponse;
import com.smartparking.payment.application.service.PaymentService;
import com.smartparking.payment.application.service.WithdrawalService;
import com.smartparking.payment.infrastructure.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminFinanceController {

    private final PaymentService paymentService;
    private final WithdrawalService withdrawalService;

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<AdminDashboardResponse>> getDashboard() {
        return ResponseEntity.ok(ApiResponse.success(
                paymentService.getDashboardStats(),
                "Lấy thông tin dashboard thành công"));
    }

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<PageResponse<AdminTransactionResponse>>> getTransactions(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                paymentService.getAllTransactionsForAdmin(pageable),
                "Lấy danh sách giao dịch thành công"));
    }

    @GetMapping("/transactions/{id}")
    public ResponseEntity<ApiResponse<AdminTransactionResponse>> getTransaction(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                paymentService.getTransactionForAdmin(id),
                "Lấy chi tiết giao dịch thành công"));
    }

    @GetMapping("/withdrawals")
    public ResponseEntity<ApiResponse<PageResponse<WithdrawalResponse>>> getWithdrawals(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                withdrawalService.getAllWithdrawalsForAdmin(pageable),
                "Lấy danh sách yêu cầu rút tiền thành công"));
    }

    @PutMapping("/withdrawals/{id}/approve")
    public ResponseEntity<ApiResponse<WithdrawalResponse>> approveWithdrawal(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) AdminDecisionRequest request) {
        String reason = request != null ? request.getReason() : "Chấp thuận rút tiền";
        WithdrawalResponse res = withdrawalService.processWithdrawal(user.getId(), id, true, reason);
        return ResponseEntity.ok(ApiResponse.success(res, "Duyệt yêu cầu rút tiền thành công"));
    }

    @PutMapping("/withdrawals/{id}/reject")
    public ResponseEntity<ApiResponse<WithdrawalResponse>> rejectWithdrawal(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) AdminDecisionRequest request) {
        String reason = request != null ? request.getReason() : "Từ chối rút tiền";
        WithdrawalResponse res = withdrawalService.processWithdrawal(user.getId(), id, false, reason);
        return ResponseEntity.ok(ApiResponse.success(res, "Từ chối yêu cầu rút tiền thành công"));
    }
}
