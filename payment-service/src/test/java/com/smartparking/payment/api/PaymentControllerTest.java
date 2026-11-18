package com.smartparking.payment.api;

import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.dto.PageResponse;
import com.smartparking.payment.api.controller.PaymentController;
import com.smartparking.payment.application.dto.*;
import com.smartparking.payment.application.service.PaymentService;
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
class PaymentControllerTest {

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentController controller;

    private AuthenticatedUser user;

    @BeforeEach
    void setUp() {
        user = new AuthenticatedUser(100L, "driver01", "DRIVER");
    }

    @Test
    @DisplayName("createPayment returns payment link")
    void testCreatePayment() {
        CreatePaymentRequest req = CreatePaymentRequest.builder().amount(BigDecimal.TEN).build();
        when(paymentService.createPaymentLink(100L, req)).thenReturn("http://payos.vn/checkout/123");

        ResponseEntity<ApiResponse<String>> res = controller.createPayment(user, req);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals("http://payos.vn/checkout/123", res.getBody().getData());
    }

    @Test
    @DisplayName("createGateFinalPayment returns payment link for merchant")
    void testCreateGateFinalPayment() {
        AuthenticatedUser merchant = new AuthenticatedUser(200L, "merchant01", "MERCHANT");
        GateFinalPaymentRequest req = GateFinalPaymentRequest.builder().bookingId(456L).build();
        when(paymentService.createGateFinalPaymentLink(200L, 456L)).thenReturn("http://payos.vn/checkout/456");

        ResponseEntity<ApiResponse<String>> res = controller.createGateFinalPayment(merchant, req);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals("http://payos.vn/checkout/456", res.getBody().getData());
    }

    @Test
    @DisplayName("confirmDemoPayment returns confirmed PaymentDto")
    void testConfirmDemoPayment() {
        DemoPaymentRequest req = new DemoPaymentRequest();
        req.setBookingId(1234L);
        PaymentDto dto = PaymentDto.builder().id(1L).bookingId(1234L).build();
        when(paymentService.confirmDemoDeposit(100L, 1234L)).thenReturn(dto);

        ResponseEntity<ApiResponse<PaymentDto>> res = controller.confirmDemoPayment(user, req);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(1234L, res.getBody().getData().getBookingId());
    }

    @Test
    @DisplayName("syncPayOSPayment handles null and populated request")
    void testSyncPayOSPayment() {
        PaymentDto dto = PaymentDto.builder().id(1L).build();
        when(paymentService.syncPayOSPayment(eq(100L), any(), any(), any())).thenReturn(dto);

        ResponseEntity<ApiResponse<PaymentDto>> res1 = controller.syncPayOSPayment(user, null);
        assertEquals(HttpStatus.OK, res1.getStatusCode());

        SyncPaymentRequest req = new SyncPaymentRequest();
        req.setOrderCode(123L);
        ResponseEntity<ApiResponse<PaymentDto>> res2 = controller.syncPayOSPayment(user, req);
        assertEquals(HttpStatus.OK, res2.getStatusCode());
    }

    @Test
    @DisplayName("getMyPayments returns paginated payments")
    void testGetMyPayments() {
        PageResponse<PaymentDto> page = new PageResponse<>(new PageImpl<>(Collections.emptyList()));
        when(paymentService.getMyPayments(eq(100L), any(), any())).thenReturn(page);

        ResponseEntity<ApiResponse<PageResponse<PaymentDto>>> res = controller.getMyPayments(user, null, PageRequest.of(0, 10));

        assertEquals(HttpStatus.OK, res.getStatusCode());
    }

    @Test
    @DisplayName("getMyPayments with bookingId returns filtered payments")
    void testGetMyPaymentsWithBookingId() {
        PageResponse<PaymentDto> page = new PageResponse<>(new PageImpl<>(Collections.emptyList()));
        when(paymentService.getMyPayments(eq(100L), eq(123L), any())).thenReturn(page);

        ResponseEntity<ApiResponse<PageResponse<PaymentDto>>> res = controller.getMyPayments(user, 123L, PageRequest.of(0, 10));

        assertEquals(HttpStatus.OK, res.getStatusCode());
    }

    @Test
    @DisplayName("handleWebhook delegates to paymentService")
    void testHandleWebhook() {
        ResponseEntity<ApiResponse<String>> res = controller.handleWebhook("{\"test\": 1}");

        assertEquals(HttpStatus.OK, res.getStatusCode());
        verify(paymentService, times(1)).handleWebhook("{\"test\": 1}");
    }
}
