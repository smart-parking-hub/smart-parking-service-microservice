package com.smartparking.payment.api;

import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.dto.PageResponse;
import com.smartparking.payment.api.controller.WithdrawalController;
import com.smartparking.payment.application.dto.CreateWithdrawalRequestDto;
import com.smartparking.payment.application.dto.WithdrawalResponse;
import com.smartparking.payment.application.service.WithdrawalService;
import com.smartparking.payment.infrastructure.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WithdrawalControllerTest {

    @Mock
    private WithdrawalService withdrawalService;

    @InjectMocks
    private WithdrawalController controller;

    private AuthenticatedUser user;
    private AuthenticatedUser admin;

    @BeforeEach
    void setUp() {
        user = new AuthenticatedUser(200L, "merchant01", "MERCHANT");
        admin = new AuthenticatedUser(1L, "admin", "ADMIN");
    }

    @Test
    @DisplayName("requestWithdrawal returns CREATED status")
    void testRequestWithdrawal() {
        CreateWithdrawalRequestDto req = new CreateWithdrawalRequestDto();
        req.setAmount(new BigDecimal("500000"));
        WithdrawalResponse resp = WithdrawalResponse.builder().id(1L).build();
        when(withdrawalService.requestWithdrawal(200L, req)).thenReturn(resp);

        ResponseEntity<ApiResponse<WithdrawalResponse>> res = controller.requestWithdrawal(user, req);

        assertEquals(HttpStatus.CREATED, res.getStatusCode());
        assertEquals(1L, res.getBody().getData().getId());
    }

    @Test
    @DisplayName("getMyWithdrawals returns paginated withdrawals")
    void testGetMyWithdrawals() {
        PageResponse<WithdrawalResponse> page = new PageResponse<>(new PageImpl<>(Collections.emptyList()));
        when(withdrawalService.getMyWithdrawals(eq(200L), any())).thenReturn(page);

        ResponseEntity<ApiResponse<PageResponse<WithdrawalResponse>>> res =
                controller.getMyWithdrawals(user, PageRequest.of(0, 10));

        assertEquals(HttpStatus.OK, res.getStatusCode());
    }

    @Test
    @DisplayName("processWithdrawal processes request for Admin")
    void testProcessWithdrawal() {
        WithdrawalResponse resp = WithdrawalResponse.builder().id(1L).build();
        when(withdrawalService.processWithdrawal(1L, 1L, true, "Approved")).thenReturn(resp);

        ResponseEntity<ApiResponse<WithdrawalResponse>> res = controller.processWithdrawal(admin, 1L, true, "Approved");

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(1L, res.getBody().getData().getId());
    }
}
