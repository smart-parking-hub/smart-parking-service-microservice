package com.smartparking.payment.api;

import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.dto.PageResponse;
import com.smartparking.common.enums.WithdrawalStatusEnum;
import com.smartparking.payment.api.controller.AdminFinanceController;
import com.smartparking.payment.application.dto.AdminDashboardResponse;
import com.smartparking.payment.application.dto.AdminDecisionRequest;
import com.smartparking.payment.application.dto.AdminTransactionResponse;
import com.smartparking.payment.application.dto.WithdrawalResponse;
import com.smartparking.payment.application.service.PaymentService;
import com.smartparking.payment.application.service.WithdrawalService;
import com.smartparking.payment.infrastructure.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminFinanceControllerTest {

    @Mock
    private PaymentService paymentService;

    @Mock
    private WithdrawalService withdrawalService;

    @InjectMocks
    private AdminFinanceController controller;

    private AuthenticatedUser adminUser;

    @BeforeEach
    void setUp() {
        adminUser = new AuthenticatedUser(1L, "0987654321", "ROLE_ADMIN");
    }

    @Test
    @DisplayName("getDashboard returns dashboard stats")
    void testGetDashboard() {
        AdminDashboardResponse dashboard = AdminDashboardResponse.builder()
                .totalBookings(50L)
                .platformRevenue(120000.0)
                .build();
        when(paymentService.getDashboardStats()).thenReturn(dashboard);

        ResponseEntity<ApiResponse<AdminDashboardResponse>> res = controller.getDashboard();

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(50L, res.getBody().getData().getTotalBookings());
    }

    @Test
    @DisplayName("getTransactions returns page of transactions")
    void testGetTransactions() {
        PageResponse<AdminTransactionResponse> pageResponse = new PageResponse<>(List.of(
                AdminTransactionResponse.builder().id(1L).bookingCode("BK-1").build()
        ), 0, 1, 1);
        when(paymentService.getAllTransactionsForAdmin(any())).thenReturn(pageResponse);

        ResponseEntity<ApiResponse<PageResponse<AdminTransactionResponse>>> res =
                controller.getTransactions(PageRequest.of(0, 10));

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(1, res.getBody().getData().getContent().size());
    }

    @Test
    @DisplayName("getTransaction returns transaction detail")
    void testGetTransaction() {
        AdminTransactionResponse tx = AdminTransactionResponse.builder().id(1L).amount(new BigDecimal("50000")).build();
        when(paymentService.getTransactionForAdmin(1L)).thenReturn(tx);

        ResponseEntity<ApiResponse<AdminTransactionResponse>> res = controller.getTransaction(1L);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(1L, res.getBody().getData().getId());
    }

    @Test
    @DisplayName("getWithdrawals returns page of withdrawals")
    void testGetWithdrawals() {
        PageResponse<WithdrawalResponse> pageResponse = new PageResponse<>(List.of(
                WithdrawalResponse.builder().id(1L).status(WithdrawalStatusEnum.PENDING).build()
        ), 0, 1, 1);
        when(withdrawalService.getAllWithdrawalsForAdmin(any())).thenReturn(pageResponse);

        ResponseEntity<ApiResponse<PageResponse<WithdrawalResponse>>> res =
                controller.getWithdrawals(PageRequest.of(0, 10));

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(1, res.getBody().getData().getContent().size());
    }

    @Test
    @DisplayName("approveWithdrawal delegates to withdrawalService")
    void testApproveWithdrawal() {
        WithdrawalResponse resp = WithdrawalResponse.builder().id(1L).status(WithdrawalStatusEnum.APPROVED).build();
        when(withdrawalService.processWithdrawal(eq(1L), eq(1L), eq(true), eq("Hop le"))).thenReturn(resp);

        AdminDecisionRequest req = new AdminDecisionRequest("Hop le");
        ResponseEntity<ApiResponse<WithdrawalResponse>> res = controller.approveWithdrawal(adminUser, 1L, req);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        verify(withdrawalService).processWithdrawal(1L, 1L, true, "Hop le");
    }

    @Test
    @DisplayName("approveWithdrawal handles null request")
    void testApproveWithdrawalNullRequest() {
        WithdrawalResponse resp = WithdrawalResponse.builder().id(1L).status(WithdrawalStatusEnum.APPROVED).build();
        when(withdrawalService.processWithdrawal(eq(1L), eq(1L), eq(true), any())).thenReturn(resp);

        ResponseEntity<ApiResponse<WithdrawalResponse>> res = controller.approveWithdrawal(adminUser, 1L, null);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        verify(withdrawalService).processWithdrawal(1L, 1L, true, "Chấp thuận rút tiền");
    }

    @Test
    @DisplayName("rejectWithdrawal delegates to withdrawalService")
    void testRejectWithdrawal() {
        WithdrawalResponse resp = WithdrawalResponse.builder().id(1L).status(WithdrawalStatusEnum.REJECTED).build();
        when(withdrawalService.processWithdrawal(eq(1L), eq(1L), eq(false), eq("Sai STK"))).thenReturn(resp);

        AdminDecisionRequest req = new AdminDecisionRequest("Sai STK");
        ResponseEntity<ApiResponse<WithdrawalResponse>> res = controller.rejectWithdrawal(adminUser, 1L, req);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        verify(withdrawalService).processWithdrawal(1L, 1L, false, "Sai STK");
    }

    @Test
    @DisplayName("rejectWithdrawal handles null request")
    void testRejectWithdrawalNullRequest() {
        WithdrawalResponse resp = WithdrawalResponse.builder().id(1L).status(WithdrawalStatusEnum.REJECTED).build();
        when(withdrawalService.processWithdrawal(eq(1L), eq(1L), eq(false), any())).thenReturn(resp);

        ResponseEntity<ApiResponse<WithdrawalResponse>> res = controller.rejectWithdrawal(adminUser, 1L, null);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        verify(withdrawalService).processWithdrawal(1L, 1L, false, "Từ chối rút tiền");
    }
}
