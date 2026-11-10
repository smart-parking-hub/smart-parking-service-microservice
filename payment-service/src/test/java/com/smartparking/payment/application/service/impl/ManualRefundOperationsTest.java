package com.smartparking.payment.application.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.common.enums.PaymentStatusEnum;
import com.smartparking.common.event.BookingEvent;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.payment.application.dto.RefundRequestDto;
import com.smartparking.payment.application.dto.SubmitRefundEvidenceRequest;
import com.smartparking.payment.application.dto.VerifyRefundEvidenceRequest;
import com.smartparking.payment.application.service.MerchantWalletService;
import com.smartparking.payment.application.service.OutboxPublisherService;
import com.smartparking.payment.domain.model.Payment;
import com.smartparking.payment.domain.model.RefundRequest;
import com.smartparking.payment.domain.repository.PaymentRepository;
import com.smartparking.payment.domain.repository.ProcessedEventRepository;
import com.smartparking.payment.domain.repository.RefundRequestRepository;
import com.smartparking.payment.domain.repository.WalletLedgerEntryRepository;
import com.smartparking.payment.infrastructure.messaging.BookingEventListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ManualRefundOperationsTest {

    @Mock
    private RefundRequestRepository refundRequestRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private MerchantWalletService merchantWalletService;

    @Mock
    private OutboxPublisherService outboxPublisherService;

    @Mock
    private WalletLedgerEntryRepository ledgerRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    private RefundServiceImpl refundService;
    private BookingEventListener bookingEventListener;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        refundService = new RefundServiceImpl(
                refundRequestRepository,
                paymentRepository,
                merchantWalletService,
                outboxPublisherService
        );
        bookingEventListener = new BookingEventListener(
                paymentRepository,
                merchantWalletService,
                ledgerRepository,
                objectMapper
        );
        ReflectionTestUtils.setField(bookingEventListener, "refundService", refundService);
        ReflectionTestUtils.setField(bookingEventListener, "processedEventRepository", processedEventRepository);
    }

    @Test
    @DisplayName("BookingEventListener deduplicates BOOKING_CANCELLED by eventId and creates manual refund REQUESTED")
    void bookingEventListenerDeduplicatesAndCreatesRefundRequest() throws Exception {
        Long bookingId = 700L;
        Long merchantId = 80L;
        Long driverId = 90L;

        Payment paidDeposit = Payment.builder()
                .id(5000L)
                .bookingId(bookingId)
                .driverId(driverId)
                .merchantId(merchantId)
                .amount(new BigDecimal("60000.00"))
                .status(PaymentStatusEnum.SUCCESS)
                .build();

        when(processedEventRepository.existsById("evt-cancel-700")).thenReturn(false, true);
        when(paymentRepository.findByBookingId(bookingId)).thenReturn(List.of(paidDeposit));
        when(refundRequestRepository.findByIdempotencyKey("refund:booking:700")).thenReturn(Optional.empty());
        when(refundRequestRepository.save(any(RefundRequest.class))).thenAnswer(inv -> {
            RefundRequest r = inv.getArgument(0);
            r.setId(1L);
            return r;
        });

        String payload = """
                {
                  "eventId": "evt-cancel-700",
                  "eventType": "BOOKING_CANCELLED",
                  "bookingId": 700,
                  "userId": 90,
                  "merchantId": 80,
                  "parkingLotId": 12,
                  "reason": "Khách hủy trước 3h",
                  "advanceDeposit": 60000,
                  "refundAmount": 60000,
                  "refundPercentage": 100,
                  "refundRequested": true,
                  "refundPolicy": "FULL_100_AT_OR_BEFORE_2H",
                  "refundStatus": "REQUESTED"
                }
                """;

        bookingEventListener.handleBookingEvent(payload);
        // Second delivery of same eventId must be ignored
        bookingEventListener.handleBookingEvent(payload);

        verify(refundRequestRepository, times(1)).save(any(RefundRequest.class));
        verify(merchantWalletService, times(1)).recordRefundLiability(
                eq(merchantId), eq(bookingId), eq(5000L), eq(new BigDecimal("60000")), eq("REFUND_REQ:BOOKING:700"), anyString());
        verify(outboxPublisherService, times(1)).publishRefundStatusChanged(any(RefundRequest.class));
    }

    @Test
    @DisplayName("Manual refund lifecycle: REQUESTED -> PROCESSING on Merchant evidence -> COMPLETED only after Admin verification")
    void manualRefundLifecycleRequiresMerchantEvidenceAndAdminVerification() {
        Long bookingId = 701L;
        Long merchantId = 80L;
        Long adminId = 1L;

        RefundRequest refund = RefundRequest.builder()
                .id(10L)
                .idempotencyKey("refund:booking:701")
                .bookingId(bookingId)
                .paymentId(5001L)
                .driverId(91L)
                .merchantId(merchantId)
                .depositAmount(new BigDecimal("50000.00"))
                .refundAmount(new BigDecimal("25000.00"))
                .refundPercentage(50)
                .policyCode("PARTIAL_50_WITHIN_2H")
                .status(RefundServiceImpl.STATUS_REQUESTED)
                .dueAt(LocalDateTime.now(DailySettlementServiceImpl.BUSINESS_ZONE).plusHours(12))
                .build();

        when(refundRequestRepository.findByBookingIdForUpdate(bookingId)).thenReturn(Optional.of(refund));
        when(refundRequestRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(refund));
        when(refundRequestRepository.save(any(RefundRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment payment = Payment.builder()
                .id(5001L)
                .bookingId(bookingId)
                .status(PaymentStatusEnum.SUCCESS)
                .amount(new BigDecimal("50000.00"))
                .build();
        when(paymentRepository.findByIdForUpdate(5001L)).thenReturn(Optional.of(payment));

        // 1. Merchant submits valid transfer evidence before dueAt -> status becomes PROCESSING (NOT COMPLETED yet)
        SubmitRefundEvidenceRequest evidenceReq = SubmitRefundEvidenceRequest.builder()
                .transactionReference("FT26314998877")
                .evidenceUrl("https://cdn.smartparking.vn/evidence/ft26314998877.png")
                .merchantNote("Đã chuyển khoản Vietcombank 25,000đ")
                .disputed(false)
                .build();

        RefundRequestDto afterMerchantSubmit = refundService.submitTransferEvidence(merchantId, bookingId, evidenceReq);
        assertEquals(RefundServiceImpl.STATUS_PROCESSING, afterMerchantSubmit.getStatus(),
                "Merchant evidence submission must move status to PROCESSING, not COMPLETED");
        assertEquals("FT26314998877", afterMerchantSubmit.getTransactionReference());
        verify(merchantWalletService, never()).settleCompletedRefund(any(), any(), any(), any(), any(), any());

        // 2. Admin verifies transfer evidence -> status becomes COMPLETED and ledger settles REFUND_PAYABLE -> GATEWAY_CLEARING
        VerifyRefundEvidenceRequest verifyReq = VerifyRefundEvidenceRequest.builder()
                .approved(true)
                .disputed(false)
                .verificationNote("Sao kê khớp mã FT26314998877")
                .build();

        RefundRequestDto afterAdminVerify = refundService.verifyTransferEvidence(adminId, 10L, verifyReq);
        assertEquals(RefundServiceImpl.STATUS_COMPLETED, afterAdminVerify.getStatus());
        assertEquals(adminId, afterAdminVerify.getVerifiedBy());
        assertEquals(PaymentStatusEnum.REFUNDED, payment.getStatus());
        verify(merchantWalletService, times(1)).settleCompletedRefund(
                eq(merchantId), eq(bookingId), eq(5001L), eq(new BigDecimal("25000.00")),
                eq("REFUND_COMPLETED:BOOKING:701"), anyString());
    }

    @Test
    @DisplayName("Missing, late, or disputed evidence and overdue requests transition to REQUIRES_RECONCILIATION")
    void missingLateDisputedOrOverdueEvidenceTransitionsToRequiresReconciliation() {
        Long bookingId = 702L;
        Long merchantId = 80L;

        // Case A: Late evidence submitted after dueAt
        RefundRequest lateRefund = RefundRequest.builder()
                .id(11L)
                .idempotencyKey("refund:booking:702")
                .bookingId(bookingId)
                .merchantId(merchantId)
                .refundAmount(new BigDecimal("50000.00"))
                .status(RefundServiceImpl.STATUS_REQUESTED)
                .dueAt(LocalDateTime.now(DailySettlementServiceImpl.BUSINESS_ZONE).minusHours(2))
                .build();

        when(refundRequestRepository.findByBookingIdForUpdate(bookingId)).thenReturn(Optional.of(lateRefund));
        when(refundRequestRepository.save(any(RefundRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        RefundRequestDto lateResult = refundService.submitTransferEvidence(merchantId, bookingId,
                SubmitRefundEvidenceRequest.builder()
                        .transactionReference("FT-LATE-01")
                        .evidenceUrl("https://cdn.smartparking.vn/evidence/late.png")
                        .build());
        assertEquals(RefundServiceImpl.STATUS_REQUIRES_RECONCILIATION, lateResult.getStatus(),
                "Evidence submitted past dueAt must transition to REQUIRES_RECONCILIATION");

        // Case B: Missing transactionReference transitions to REQUIRES_RECONCILIATION
        lateRefund.setStatus(RefundServiceImpl.STATUS_REQUESTED);
        lateRefund.setDueAt(LocalDateTime.now(DailySettlementServiceImpl.BUSINESS_ZONE).plusHours(5));
        RefundRequestDto missingRefResult = refundService.submitTransferEvidence(merchantId, bookingId,
                SubmitRefundEvidenceRequest.builder()
                        .transactionReference("   ")
                        .merchantNote("Chưa có mã giao dịch")
                        .build());
        assertEquals(RefundServiceImpl.STATUS_REQUIRES_RECONCILIATION, missingRefResult.getStatus());

        // Case C: Admin rejects disputed evidence -> REQUIRES_RECONCILIATION
        lateRefund.setStatus(RefundServiceImpl.STATUS_PROCESSING);
        lateRefund.setTransactionReference("FT-DISPUTED");
        when(refundRequestRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(lateRefund));
        RefundRequestDto disputedResult = refundService.verifyTransferEvidence(1L, 11L,
                VerifyRefundEvidenceRequest.builder()
                        .approved(false)
                        .disputed(true)
                        .verificationNote("Ảnh chụp màn hình không khớp số tiền")
                        .build());
        assertEquals(RefundServiceImpl.STATUS_REQUIRES_RECONCILIATION, disputedResult.getStatus());

        // Case D: Non-owner merchant is forbidden
        AppException forbiddenEx = assertThrows(AppException.class,
                () -> refundService.submitTransferEvidence(999L, bookingId,
                        SubmitRefundEvidenceRequest.builder().transactionReference("FT1").build()));
        assertEquals(ErrorCode.FORBIDDEN, forbiddenEx.getErrorCode());

        // Case E: Overdue sweep moves REQUESTED past dueAt to REQUIRES_RECONCILIATION
        RefundRequest overdue = RefundRequest.builder()
                .id(12L)
                .bookingId(703L)
                .merchantId(merchantId)
                .refundAmount(new BigDecimal("30000.00"))
                .status(RefundServiceImpl.STATUS_REQUESTED)
                .dueAt(LocalDateTime.now(DailySettlementServiceImpl.BUSINESS_ZONE).minusMinutes(30))
                .build();
        when(refundRequestRepository.findByStatusAndDueAtBefore(eq(RefundServiceImpl.STATUS_REQUESTED), any()))
                .thenReturn(List.of(overdue));
        when(refundRequestRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(overdue));

        List<RefundRequestDto> swept = refundService.reconcileOverdueRefunds();
        assertEquals(1, swept.size());
        assertEquals(RefundServiceImpl.STATUS_REQUIRES_RECONCILIATION, swept.get(0).getStatus());
    }
}

