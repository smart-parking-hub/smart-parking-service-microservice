package com.smartparking.payment.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.common.enums.PaymentGatewayEnum;
import com.smartparking.common.enums.PaymentStatusEnum;
import com.smartparking.common.enums.PaymentTypeEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.payment.application.dto.CreatePaymentRequest;
import com.smartparking.payment.application.dto.PaymentDto;
import com.smartparking.payment.application.service.impl.PaymentServiceImpl;
import com.smartparking.payment.config.PayOSConfig;
import com.smartparking.payment.domain.model.Payment;
import com.smartparking.payment.domain.repository.DisputeRepository;
import com.smartparking.payment.domain.repository.PaymentRepository;
import com.smartparking.payment.domain.repository.WalletLedgerEntryRepository;
import com.smartparking.payment.domain.repository.WithdrawalRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import vn.payos.PayOS;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PayOS payOS;

    @Mock
    private PayOSConfig payOSConfig;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private MerchantWalletService merchantWalletService;

    @Mock
    private OutboxPublisherService outboxPublisherService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private WithdrawalRequestRepository withdrawalRequestRepository;

    @Mock
    private DisputeRepository disputeRepository;

    @Mock
    private WalletLedgerEntryRepository walletLedgerEntryRepository;

    @Mock
    private com.smartparking.payment.infrastructure.client.BookingPaymentVerifier bookingPaymentVerifier;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private final Long driverUserId = 200L;
    private final Long merchantUserId = 300L;
    private final Long bookingId = 1234L;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(bookingPaymentVerifier.require(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong()))
            .thenAnswer(inv -> new com.smartparking.payment.infrastructure.client.BookingPaymentVerifier.Quote(inv.getArgument(0), inv.getArgument(1), merchantUserId, 10L, "PENDING_PAYMENT", new BigDecimal("50000"), new BigDecimal("250000"), java.time.LocalDateTime.now().plusMinutes(15)));
        org.mockito.Mockito.lenient().when(bookingPaymentVerifier.confirm(org.mockito.ArgumentMatchers.any())).thenReturn(true);

        ReflectionTestUtils.setField(paymentService, "demoPaymentEnabled", true);
    }

    @Test
    @DisplayName("PayOS failure never becomes simulated success")
    void testCreatePaymentLinkFallback() {
        when(payOSConfig.getReturnUrl()).thenReturn("http://localhost:3000/payment-result");
        when(payOSConfig.getCancelUrl()).thenReturn("http://localhost:3000/payment-cancel");

        when(payOS.paymentRequests()).thenThrow(new RuntimeException("PayOS network timeout"));

        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .bookingId(bookingId).merchantId(merchantUserId)
                .merchantId(merchantUserId)
                .parkingLotId(10L)
                .amount(new BigDecimal("50000"))
                .paymentType("DEPOSIT")
                .build();

        AppException failure = assertThrows(AppException.class, () -> paymentService.createPaymentLink(driverUserId, request));
        assertEquals(ErrorCode.PAYMENT_FAILED, failure.getErrorCode());
        verify(paymentRepository).saveAndFlush(any(Payment.class));
    }

    @Test
    @DisplayName("Confirm demo deposit throws BAD_REQUEST when demo is disabled")
    void testConfirmDemoDepositDisabled() {
        ReflectionTestUtils.setField(paymentService, "demoPaymentEnabled", false);

        AppException ex = assertThrows(AppException.class, () ->
                paymentService.confirmDemoDeposit(driverUserId, bookingId));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    @DisplayName("Confirm demo deposit successfully saves payment and publishes outbox event")
    void testConfirmDemoDepositSuccess() {

        Payment saved = Payment.builder()
                .id(1L)
                .bookingId(bookingId).driverId(driverUserId).merchantId(merchantUserId)
                .driverId(driverUserId)
                .gateway(PaymentGatewayEnum.DEMO)
                .amount(new BigDecimal("50000"))
                .paymentType(PaymentTypeEnum.DEPOSIT)
                .status(PaymentStatusEnum.PENDING)
                .build();
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenReturn(saved);
        when(paymentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(saved));

        PaymentDto dto = paymentService.confirmDemoDeposit(driverUserId, bookingId);

        assertNotNull(dto);
        assertEquals(bookingId, dto.getBookingId());
        assertEquals(PaymentGatewayEnum.DEMO, dto.getGateway());
        assertEquals(PaymentStatusEnum.SUCCESS, dto.getStatus());
        verify(outboxPublisherService, times(1)).publishPaymentCompleted(saved);
    }

    @Test
    @DisplayName("Sync PayOS payment throws NOT_FOUND when transaction does not exist")
    void testSyncPayOSNotFound() {
        when(paymentRepository.findByOrderCode(99999L)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () ->
                paymentService.syncPayOSPayment(driverUserId, 99999L, null, null));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("Handle webhook throws BAD_REQUEST when webhook body is empty")
    void testHandleWebhookEmpty() {
        AppException ex = assertThrows(AppException.class, () -> paymentService.handleWebhook("  "));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    @DisplayName("Confirm demo deposit returns existing payment if already paid")
    void testConfirmDemoDepositAlreadyPaid() {

        Payment existing = Payment.builder()
                .id(1L)
                .bookingId(bookingId).driverId(driverUserId).merchantId(merchantUserId)
                .paymentType(PaymentTypeEnum.DEPOSIT)
                .status(PaymentStatusEnum.SUCCESS)
                .build();
        when(paymentRepository.findByIdempotencyKey("booking:" + bookingId + ":DEPOSIT")).thenReturn(Optional.of(existing));

        PaymentDto dto = paymentService.confirmDemoDeposit(driverUserId, bookingId);

        assertNotNull(dto);
        assertEquals(bookingId, dto.getBookingId());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Sync PayOS returns immediately if payment is already SUCCESS")
    void testSyncPayOSPaymentAlreadySuccess() {
        Payment paid = Payment.builder()
                .id(1L)
                .orderCode(111L).driverId(driverUserId).driverId(driverUserId)
                .status(PaymentStatusEnum.SUCCESS)
                .build();
        when(paymentRepository.findByOrderCode(111L)).thenReturn(Optional.of(paid));
        when(paymentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(paid));
        when(paymentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(paid));

        PaymentDto dto = paymentService.syncPayOSPayment(driverUserId, 111L, null, null);

        assertNotNull(dto);
        assertEquals(PaymentStatusEnum.SUCCESS, dto.getStatus());
        verify(payOS, never()).paymentRequests();
    }

    @Test
    @DisplayName("Sync PayOS finds pending payment by bookingId if orderCode is null")
    void testSyncPayOSPaymentWithBookingId() {
        Payment pending = Payment.builder()
                .id(1L)
                .bookingId(bookingId).driverId(driverUserId).merchantId(merchantUserId)
                .status(PaymentStatusEnum.PENDING)
                .orderCode(222L)
                .build();
        when(paymentRepository.findByBookingId(bookingId)).thenReturn(List.of(pending));
        when(paymentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pending));
        when(paymentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pending));

        PaymentDto dto = paymentService.syncPayOSPayment(driverUserId, null, bookingId, null);

        assertNotNull(dto);
        assertEquals(PaymentStatusEnum.PENDING, dto.getStatus());
    }

    @Test
    @DisplayName("getMyPayments returns paginated list of driver payments")
    void testGetMyPayments() {
        Payment p = Payment.builder().id(1L).driverId(driverUserId).build();
        when(paymentRepository.findByDriverId(eq(driverUserId), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(p)));

        var res = paymentService.getMyPayments(driverUserId, org.springframework.data.domain.PageRequest.of(0, 10));

        assertNotNull(res);
        assertEquals(1, res.getContent().size());
    }

    @Test
    @DisplayName("getAllTransactionsForAdmin returns page of admin transactions")
    void testGetAllTransactionsForAdmin() {
        Payment p = Payment.builder().id(1L).bookingId(10L).driverId(20L).merchantId(30L)
                .amount(new BigDecimal("50000")).gateway(PaymentGatewayEnum.PAYOS)
                .paymentType(PaymentTypeEnum.FINAL_PAYMENT).status(PaymentStatusEnum.SUCCESS).build();
        when(paymentRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(p)));

        var res = paymentService.getAllTransactionsForAdmin(org.springframework.data.domain.PageRequest.of(0, 10));

        assertNotNull(res);
        assertEquals(1, res.getContent().size());
        assertEquals("BK-10", res.getContent().get(0).getBookingCode());
    }

    @Test
    @DisplayName("getTransactionForAdmin returns transaction when found")
    void testGetTransactionForAdmin() {
        Payment p = Payment.builder().id(1L).bookingId(10L).driverId(20L).merchantId(30L)
                .amount(new BigDecimal("50000")).gateway(PaymentGatewayEnum.PAYOS)
                .paymentType(PaymentTypeEnum.FINAL_PAYMENT).status(PaymentStatusEnum.SUCCESS).build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(p));

        var res = paymentService.getTransactionForAdmin(1L);

        assertNotNull(res);
        assertEquals(1L, res.getId());
    }

    @Test
    @DisplayName("getTransactionForAdmin throws NOT_FOUND when payment does not exist")
    void testGetTransactionForAdminNotFound() {
        when(paymentRepository.findById(999L)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> paymentService.getTransactionForAdmin(999L));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("getDashboardStats aggregates counts and revenue")
    void testGetDashboardStats() {
        when(paymentRepository.count()).thenReturn(100L);
        when(withdrawalRequestRepository.countByStatus(any())).thenReturn(5L);
        when(disputeRepository.countByStatus(any())).thenReturn(2L);
        when(walletLedgerEntryRepository.sumTotalPlatformFee()).thenReturn(new BigDecimal("1500000"));

        var stats = paymentService.getDashboardStats();

        assertNotNull(stats);
        assertEquals(100L, stats.getTotalBookings());
        assertEquals(5L, stats.getPendingWithdrawals());
        assertEquals(2L, stats.getOpenDisputes());
        assertEquals(1500000.0, stats.getPlatformRevenue());
    }
    @Test void latePaymentRequiresReviewWithoutCreditingMerchant() {
        Payment pending = Payment.builder().id(81L).bookingId(bookingId).driverId(driverUserId).merchantId(merchantUserId)
                .gateway(PaymentGatewayEnum.DEMO).paymentType(PaymentTypeEnum.DEPOSIT)
                .amount(new BigDecimal("50000")).status(PaymentStatusEnum.PENDING).build();
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenReturn(pending);
        when(paymentRepository.findByIdForUpdate(81L)).thenReturn(Optional.of(pending));
        when(bookingPaymentVerifier.confirm(pending)).thenReturn(false);
        assertEquals(PaymentStatusEnum.REQUIRES_REVIEW,paymentService.confirmDemoDeposit(driverUserId,bookingId).getStatus());
        verifyNoInteractions(merchantWalletService,outboxPublisherService);
    }
    @Test void paymentReplayFromAnotherDriverIsForbidden() {
        Payment other = Payment.builder().driverId(999L).status(PaymentStatusEnum.SUCCESS).build();
        when(paymentRepository.findByIdempotencyKey("booking:"+bookingId+":DEPOSIT")).thenReturn(Optional.of(other));
        var failure = assertThrows(AppException.class,()->paymentService.confirmDemoDeposit(driverUserId,bookingId));
        assertEquals(ErrorCode.FORBIDDEN,failure.getErrorCode());
        verifyNoInteractions(merchantWalletService,outboxPublisherService);
    }
    @Test void expiredQuoteCannotCreatePaymentLink() {
        when(bookingPaymentVerifier.require(bookingId,driverUserId)).thenReturn(
            new com.smartparking.payment.infrastructure.client.BookingPaymentVerifier.Quote(bookingId,driverUserId,merchantUserId,10L,"PENDING_PAYMENT",new BigDecimal("50000"),new BigDecimal("250000"),java.time.LocalDateTime.now().minusSeconds(1)));
        var request=CreatePaymentRequest.builder().bookingId(bookingId).paymentType("DEPOSIT").amount(BigDecimal.ONE).build();
        assertThrows(AppException.class,()->paymentService.createPaymentLink(driverUserId,request));
        verifyNoInteractions(payOS); verify(paymentRepository,never()).saveAndFlush(any());
    }
    @Test void signedWebhookWithWrongAmountRequiresReviewWithoutSettlement() throws Exception {
        var webhook = mock(vn.payos.model.webhooks.Webhook.class);
        var data = mock(vn.payos.model.webhooks.WebhookData.class);
        var webhooks = mock(vn.payos.service.blocking.webhooks.WebhooksService.class);
        when(objectMapper.readValue("{}",vn.payos.model.webhooks.Webhook.class)).thenReturn(webhook);
        when(payOS.webhooks()).thenReturn(webhooks); when(webhooks.verify(webhook)).thenReturn(data);
        when(data.getOrderCode()).thenReturn(123L); when(data.getCode()).thenReturn("00"); when(data.getAmount()).thenReturn(1L);
        Payment pending=Payment.builder().id(81L).amount(new BigDecimal("50000")).status(PaymentStatusEnum.PENDING).build();
        when(paymentRepository.findByOrderCode(123L)).thenReturn(Optional.of(pending));
        when(paymentRepository.findByIdForUpdate(81L)).thenReturn(Optional.of(pending));
        paymentService.handleWebhook("{}");
        assertEquals(PaymentStatusEnum.REQUIRES_REVIEW,pending.getStatus());
        verifyNoInteractions(merchantWalletService,outboxPublisherService); verify(bookingPaymentVerifier,never()).confirm(any());
    }
    @Test void invalidWebhookSignatureNeverChangesPaymentOrCreditsWallet() throws Exception {
        var webhook=mock(vn.payos.model.webhooks.Webhook.class);
        var webhooks=mock(vn.payos.service.blocking.webhooks.WebhooksService.class);
        when(objectMapper.readValue("{}",vn.payos.model.webhooks.Webhook.class)).thenReturn(webhook);
        when(payOS.webhooks()).thenReturn(webhooks); when(webhooks.verify(webhook)).thenThrow(new SecurityException("invalid signature"));
        assertThrows(AppException.class,()->paymentService.handleWebhook("{}"));
        verifyNoInteractions(paymentRepository,merchantWalletService,outboxPublisherService);
    }
}
