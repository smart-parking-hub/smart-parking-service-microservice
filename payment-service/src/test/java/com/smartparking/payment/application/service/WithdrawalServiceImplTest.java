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

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WithdrawalServiceImplTest {

    @Mock
    private WithdrawalRequestRepository withdrawalRequestRepository;

    @Mock
    private MerchantWalletService merchantWalletService;

    @Mock
    private com.smartparking.common.messaging.TransactionalEventOutbox adminEventOutbox;

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
        verifyNoInteractions(adminEventOutbox);
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
        verify(adminEventOutbox).enqueue(eq(com.smartparking.common.event.AdminOperationEvent.TOPIC), eq("withdrawal:55"),
                argThat(event -> event instanceof com.smartparking.common.event.AdminOperationEvent operation
                        && operation.getEventType().equals("WITHDRAWAL_REQUESTED")
                        && operation.getTargetId().equals(55L)
                        && operation.getAmount().compareTo(new BigDecimal("40000")) == 0));
    }

    @Test
    @DisplayName("Process withdrawal fails if status is not PENDING")
    void testProcessWithdrawalAlreadyProcessed() {
        WithdrawalRequest req = WithdrawalRequest.builder()
                .id(withdrawalId)
                .status(WithdrawalStatusEnum.APPROVED)
                .build();
        when(withdrawalRequestRepository.findForUpdate(withdrawalId)).thenReturn(Optional.of(req));

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
        when(withdrawalRequestRepository.findForUpdate(withdrawalId)).thenReturn(Optional.of(req));
        when(withdrawalRequestRepository.save(any(WithdrawalRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(merchantWalletService.getWalletBalance(merchantUserId)).thenReturn(new BigDecimal("100000"));

        WithdrawalResponse response = withdrawalService.processWithdrawal(1L, withdrawalId, false, "Sai số tài khoản");

        assertNotNull(response);
        assertEquals(WithdrawalStatusEnum.REJECTED, response.getStatus());
        verify(merchantWalletService, times(1)).refundWithdrawal(eq(merchantUserId), eq(new BigDecimal("50000")), any());
        verify(withdrawalRequestRepository, times(1)).save(req);
        verify(adminEventOutbox).enqueue(eq("withdrawal-decisions.events"), eq("withdrawal:55"),
                argThat(event -> "WITHDRAWAL_REJECTED".equals(event.getEventType())));
    }

    @Test
    @DisplayName("Process withdrawal approval updates status to APPROVED without refund")
    void testProcessWithdrawalApproved() {
        WithdrawalRequest req = WithdrawalRequest.builder()
                .id(withdrawalId)
                .merchantId(merchantUserId)
                .amount(new BigDecimal("50000"))
                .status(WithdrawalStatusEnum.PENDING)
                .build();
        when(withdrawalRequestRepository.findForUpdate(withdrawalId)).thenReturn(Optional.of(req));
        when(withdrawalRequestRepository.save(any(WithdrawalRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(merchantWalletService.getWalletBalance(merchantUserId)).thenReturn(new BigDecimal("50000"));

        WithdrawalResponse response = withdrawalService.processWithdrawal(1L, withdrawalId, true, "Đã chuyển khoản");

        assertNotNull(response);
        assertEquals(WithdrawalStatusEnum.APPROVED, response.getStatus());
        verify(merchantWalletService, never()).refundWithdrawal(any(), any(), any());
        verify(withdrawalRequestRepository, times(1)).save(req);
        verify(adminEventOutbox).enqueue(eq("withdrawal-decisions.events"), eq("withdrawal:55"),
                argThat(event -> "WITHDRAWAL_APPROVED".equals(event.getEventType())));
    }

    @Test
    @DisplayName("Process withdrawal throws NOT_FOUND when request does not exist")
    void testProcessWithdrawalNotFound() {
        when(withdrawalRequestRepository.findForUpdate(999L)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () ->
                withdrawalService.processWithdrawal(1L, 999L, true, "OK"));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("Get my withdrawals returns page of responses")
    void testGetMyWithdrawals() {
        WithdrawalRequest req = WithdrawalRequest.builder()
                .id(withdrawalId)
                .merchantId(merchantUserId)
                .amount(new BigDecimal("20000"))
                .status(WithdrawalStatusEnum.APPROVED)
                .build();
        when(merchantWalletService.getWalletBalance(merchantUserId)).thenReturn(new BigDecimal("80000"));
        when(withdrawalRequestRepository.findByMerchantIdOrderByRequestedAtDesc(eq(merchantUserId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(req), PageRequest.of(0, 10), 1));

        var page = withdrawalService.getMyWithdrawals(merchantUserId, PageRequest.of(0, 10));

        assertNotNull(page);
        assertEquals(1, page.getContent().size());
        assertEquals(new BigDecimal("80000"), page.getContent().get(0).getAvailableBalance());
    }

    @Test
    @DisplayName("getAllWithdrawalsForAdmin returns all withdrawal requests")
    void testGetAllWithdrawalsForAdmin() {
        WithdrawalRequest req = WithdrawalRequest.builder()
                .id(withdrawalId)
                .merchantId(merchantUserId)
                .amount(new BigDecimal("30000"))
                .status(WithdrawalStatusEnum.PENDING)
                .build();
        when(merchantWalletService.getWalletBalance(merchantUserId)).thenReturn(new BigDecimal("70000"));
        when(withdrawalRequestRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(req), PageRequest.of(0, 10), 1));

        var page = withdrawalService.getAllWithdrawalsForAdmin(PageRequest.of(0, 10));

        assertNotNull(page);
        assertEquals(1, page.getContent().size());
        assertEquals(new BigDecimal("70000"), page.getContent().get(0).getAvailableBalance());
        verify(withdrawalRequestRepository).findAll(argThat((Pageable p) ->
                p.getSort().getOrderFor("requestedAt").isDescending() && p.getSort().getOrderFor("id").isDescending()));
    }

    @Test void adminSortsAmountBeforePagination() {
        when(withdrawalRequestRepository.findAll(any(Pageable.class))).thenReturn(org.springframework.data.domain.Page.empty());
        withdrawalService.getAllWithdrawalsForAdmin(PageRequest.of(2, 10, org.springframework.data.domain.Sort.by("amount")));
        verify(withdrawalRequestRepository).findAll(argThat((Pageable p) -> p.getPageNumber() == 2
                && p.getSort().getOrderFor("amount").isAscending() && p.getSort().getOrderFor("id").isAscending()));
    }

    @Test void adminSortRejectsUnsupportedFields() {
        when(withdrawalRequestRepository.findAll(any(Pageable.class))).thenReturn(org.springframework.data.domain.Page.empty());
        withdrawalService.getAllWithdrawalsForAdmin(PageRequest.of(0, 10, org.springframework.data.domain.Sort.by("bankAccountNumber")));
        verify(withdrawalRequestRepository).findAll(argThat((Pageable p) -> p.getSort().getOrderFor("bankAccountNumber") == null
                && p.getSort().getOrderFor("requestedAt") != null));
    }
}
