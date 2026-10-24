package com.smartparking.payment.application.service;

import com.smartparking.common.enums.WithdrawalStatusEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.payment.application.dto.CreateWithdrawalRequestDto;
import com.smartparking.payment.application.dto.WithdrawalResponse;
import com.smartparking.payment.application.service.impl.WithdrawalServiceImpl;
import com.smartparking.payment.domain.model.WithdrawalRequest;
import com.smartparking.payment.domain.repository.WithdrawalRequestRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WithdrawalServiceImplTest {

    @Mock
    private WithdrawalRequestRepository withdrawalRequestRepository;

    @Mock
    private MerchantWalletService merchantWalletService;

    @InjectMocks
    private WithdrawalServiceImpl withdrawalService;

    private final Long merchantUserId = 100L;
    private final Long withdrawalId = 55L;

    @Test
    @DisplayName("Request withdrawal fails when balance is lower than request amount")
    void testRequestWithdrawalInsufficientBalance() {
        when(merchantWalletService.getWalletBalance(merchantUserId)).thenReturn(new BigDecimal("10000"));

        CreateWithdrawalRequestDto req = CreateWithdrawalRequestDto.builder()
                .amount(new BigDecimal("50000"))
                .build();

        AppException ex = assertThrows(AppException.class, () ->
                withdrawalService.requestWithdrawal(merchantUserId, req));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Số dư khả dụng không đủ"));
    }

    @Test
    @DisplayName("Request withdrawal successfully creates PENDING withdrawal and holds funds")
    void testRequestWithdrawalSuccess() {
        when(merchantWalletService.getWalletBalance(merchantUserId))
                .thenReturn(new BigDecimal("100000"))
                .thenReturn(new BigDecimal("60000"));

        WithdrawalRequest saved = WithdrawalRequest.builder()
                .id(withdrawalId)
                .merchantId(merchantUserId)
                .amount(new BigDecimal("40000"))
                .status(WithdrawalStatusEnum.PENDING)
                .build();
        when(withdrawalRequestRepository.save(any(WithdrawalRequest.class))).thenReturn(saved);

        CreateWithdrawalRequestDto req = CreateWithdrawalRequestDto.builder()
                .amount(new BigDecimal("40000"))
                .build();

        WithdrawalResponse response = withdrawalService.requestWithdrawal(merchantUserId, req);

        assertNotNull(response);
        assertEquals(WithdrawalStatusEnum.PENDING, response.getStatus());
        assertEquals(new BigDecimal("60000"), response.getAvailableBalance());
        verify(merchantWalletService, times(1)).holdWithdrawal(eq(merchantUserId), eq(new BigDecimal("40000")), any());
    }

    @Test
    @DisplayName("Process withdrawal fails if status is not PENDING")
    void testProcessWithdrawalAlreadyProcessed() {
        WithdrawalRequest req = WithdrawalRequest.builder()
                .id(withdrawalId)
                .status(WithdrawalStatusEnum.APPROVED)
                .build();
        when(withdrawalRequestRepository.findById(withdrawalId)).thenReturn(Optional.of(req));

        AppException ex = assertThrows(AppException.class, () ->
                withdrawalService.processWithdrawal(1L, withdrawalId, true, "OK"));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("đã được xử lý"));
    }

    @Test
    @DisplayName("Process withdrawal rejection refunds held balance to merchant")
    void testProcessWithdrawalRejection() {
        WithdrawalRequest req = WithdrawalRequest.builder()
                .id(withdrawalId)
                .merchantId(merchantUserId)
                .amount(new BigDecimal("50000"))
                .status(WithdrawalStatusEnum.PENDING)
                .build();
        when(withdrawalRequestRepository.findById(withdrawalId)).thenReturn(Optional.of(req));
        when(withdrawalRequestRepository.save(any(WithdrawalRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(merchantWalletService.getWalletBalance(merchantUserId)).thenReturn(new BigDecimal("100000"));

        WithdrawalResponse response = withdrawalService.processWithdrawal(1L, withdrawalId, false, "Sai số tài khoản");

        assertNotNull(response);
        assertEquals(WithdrawalStatusEnum.REJECTED, response.getStatus());
        verify(merchantWalletService, times(1)).refundWithdrawal(eq(merchantUserId), eq(new BigDecimal("50000")), any());
        verify(withdrawalRequestRepository, times(1)).save(req);
    }
}
