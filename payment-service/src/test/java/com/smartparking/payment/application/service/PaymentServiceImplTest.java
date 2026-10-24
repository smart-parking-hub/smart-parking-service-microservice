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
import com.smartparking.payment.domain.repository.PaymentRepository;
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

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private final Long driverUserId = 200L;
    private final Long merchantUserId = 300L;
    private final Long bookingId = 1234L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentService, "demoPaymentEnabled", true);
    }

    @Test
    @DisplayName("Create payment link falls back to simulated URL if PayOS is offline")
    void testCreatePaymentLinkFallback() {
        when(payOSConfig.getReturnUrl()).thenReturn("http://localhost:3000/payment-result");
        when(payOSConfig.getCancelUrl()).thenReturn("http://localhost:3000/payment-cancel");

        when(payOS.paymentRequests()).thenThrow(new RuntimeException("PayOS network timeout"));

        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .bookingId(bookingId)
                .merchantId(merchantUserId)
                .parkingLotId(10L)
                .amount(new BigDecimal("50000"))
                .paymentType("DEPOSIT")
                .build();

        String url = paymentService.createPaymentLink(driverUserId, request);

        assertNotNull(url);
        assertTrue(url.contains("simulated=true"));
        assertTrue(url.contains("bookingId=" + bookingId));
        verify(paymentRepository, times(1)).save(any(Payment.class));
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
        when(paymentRepository.existsByBookingIdAndPaymentTypeAndStatus(
                bookingId, PaymentTypeEnum.DEPOSIT, PaymentStatusEnum.SUCCESS)).thenReturn(false);

        Payment saved = Payment.builder()
                .id(1L)
                .bookingId(bookingId)
                .driverId(driverUserId)
                .gateway(PaymentGatewayEnum.DEMO)
                .amount(new BigDecimal("50000"))
                .paymentType(PaymentTypeEnum.DEPOSIT)
                .status(PaymentStatusEnum.SUCCESS)
                .build();
        when(paymentRepository.save(any(Payment.class))).thenReturn(saved);

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
        when(paymentRepository.existsByBookingIdAndPaymentTypeAndStatus(
                bookingId, PaymentTypeEnum.DEPOSIT, PaymentStatusEnum.SUCCESS)).thenReturn(true);

        Payment existing = Payment.builder()
                .id(1L)
                .bookingId(bookingId)
                .paymentType(PaymentTypeEnum.DEPOSIT)
                .status(PaymentStatusEnum.SUCCESS)
                .build();
        when(paymentRepository.findByBookingId(bookingId)).thenReturn(List.of(existing));

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
                .orderCode(111L)
                .status(PaymentStatusEnum.SUCCESS)
                .build();
        when(paymentRepository.findByOrderCode(111L)).thenReturn(Optional.of(paid));

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
                .bookingId(bookingId)
                .status(PaymentStatusEnum.PENDING)
                .orderCode(222L)
                .build();
        when(paymentRepository.findByBookingId(bookingId)).thenReturn(List.of(pending));

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
}
