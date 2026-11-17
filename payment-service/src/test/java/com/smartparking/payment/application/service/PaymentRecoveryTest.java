package com.smartparking.payment.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.common.enums.*;
import com.smartparking.common.exception.AppException;
import com.smartparking.payment.application.dto.CreatePaymentRequest;
import com.smartparking.payment.application.service.impl.PaymentServiceImpl;
import com.smartparking.payment.config.PayOSConfig;
import com.smartparking.payment.domain.model.Payment;
import com.smartparking.payment.domain.repository.*;
import com.smartparking.payment.infrastructure.client.BookingPaymentVerifier;
import com.smartparking.payment.infrastructure.persistence.PaymentAttemptGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentRecoveryTest {
    @Mock private com.smartparking.payment.domain.repository.CashCheckoutReceiptRepository cashReceipts;

    @Mock BookingPaymentVerifier verifier;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS) PayOS payOS;
    @Mock PayOSConfig config;
    @Mock PaymentRepository payments;
    @Mock PaymentAttemptGuard guard;
    @Mock MerchantWalletService wallet;
    @Mock OutboxPublisherService events;
    @Mock ObjectMapper mapper;
    @Mock WithdrawalRequestRepository withdrawals;
    @Mock DisputeRepository disputes;
    @Mock WalletLedgerEntryRepository ledger;
    @InjectMocks PaymentServiceImpl service;

    private Payment attempt(PaymentStatusEnum status) {
        var p = Payment.builder().id(1L).bookingId(51L).driverId(7L).merchantId(9L).parkingLotId(3L)
                .gateway(PaymentGatewayEnum.PAYOS).paymentType(PaymentTypeEnum.DEPOSIT).orderCode(511L)
                .amount(new BigDecimal("4000")).status(status).checkoutUrl("https://pay.payos.vn/web/old")
                .idempotencyKey("booking:51:DEPOSIT").bookingExpiresAt(LocalDateTime.now().plusMinutes(10)).build();
        lenient().when(payments.findByBookingId(51L)).thenReturn(List.of(p));
        lenient().when(payments.findAttemptsForUpdate(51L, PaymentTypeEnum.DEPOSIT)).thenReturn(List.of(p));
        lenient().when(payments.findByIdForUpdate(1L)).thenReturn(Optional.of(p));
        return p;
    }
    private void quote() {
        when(verifier.require(51L, 7L)).thenReturn(new BookingPaymentVerifier.Quote(51L, 7L, 9L, 3L,
                "PENDING_PAYMENT", new BigDecimal("4000"), new BigDecimal("20000"), LocalDateTime.now().plusMinutes(10)));
    }
    private void provider(long order, PaymentLinkStatus status, Long paid) {
        var link = mock(PaymentLink.class);
        lenient().when(link.getOrderCode()).thenReturn(order);
        lenient().when(link.getAmount()).thenReturn(4000L);
        lenient().when(link.getStatus()).thenReturn(status);
        lenient().when(link.getAmountPaid()).thenReturn(paid);
        when(payOS.paymentRequests().get(order)).thenReturn(link);
    }
    private CreatePaymentRequest request() { return CreatePaymentRequest.builder().bookingId(51L).paymentType("DEPOSIT").build(); }

    @Test void cancelledZeroMoneyCreatesANewOrderAndPreservesOldAttempt() {
        Payment old = attempt(PaymentStatusEnum.FAILED);
        quote(); provider(511, PaymentLinkStatus.CANCELLED, 0L);
        when(guard.nextOrderCode()).thenReturn(9001L);
        var result = mock(CreatePaymentLinkResponse.class);
        when(result.getOrderCode()).thenReturn(9001L); when(result.getAmount()).thenReturn(4000L);
        when(result.getCheckoutUrl()).thenReturn("https://pay.payos.vn/web/new");
        when(payOS.paymentRequests().create(any(CreatePaymentLinkRequest.class))).thenReturn(result);
        assertEquals("https://pay.payos.vn/web/new", service.createPaymentLink(7L, request()));
        var saved = ArgumentCaptor.forClass(Payment.class);
        verify(payments).saveAndFlush(saved.capture());
        assertEquals(9001L, saved.getValue().getOrderCode());
        assertNotEquals(old.getIdempotencyKey(), saved.getValue().getIdempotencyKey());
        assertEquals(511L, old.getOrderCode());
        assertEquals(PaymentStatusEnum.FAILED, old.getStatus());
        var order = inOrder(guard, payOS.paymentRequests());
        order.verify(guard).lock(51L, PaymentTypeEnum.DEPOSIT);
        order.verify(payOS.paymentRequests()).get(511L);
        order.verify(guard).nextOrderCode();
    }

    @Test void cancelledWithMoneyNeverCreatesAnotherCharge() {
        attempt(PaymentStatusEnum.FAILED); quote(); provider(511, PaymentLinkStatus.CANCELLED, 1000L);
        assertThrows(AppException.class, () -> service.createPaymentLink(7L, request()));
        verify(guard, never()).nextOrderCode();
        verify(payOS.paymentRequests(), never()).create(any(CreatePaymentLinkRequest.class));
    }

    @Test void syncRejectsOrderForAnotherBookingEvenForSameDriver() {
        var p = attempt(PaymentStatusEnum.PENDING);
        when(payments.findByOrderCode(511L)).thenReturn(Optional.of(p));
        assertThrows(AppException.class, () -> service.syncPayOSPayment(7L, 511L, 99L, null));
        verifyNoInteractions(payOS);
    }

    @Test void unknownOrderCannotFallBackToUnrelatedLatestPayment() {
        assertThrows(AppException.class, () -> service.syncPayOSPayment(7L, 999L, 51L, null));
        verify(payments, never()).findByBookingId(51L);
    }

    @Test void syncGrantsRetryOnlyForAuthoritativeZeroMoneyCancellation() {
        attempt(PaymentStatusEnum.FAILED); quote(); provider(511, PaymentLinkStatus.CANCELLED, 0L);
        var result = service.syncPayOSPayment(7L, null, 51L, null);
        assertTrue(result.isCanRetry()); assertFalse(result.isCanResume());
    }

    @Test void oldCancelledReturnUsesCurrentAttemptRatherThanOfferingDuplicatePayment() {
        var old = attempt(PaymentStatusEnum.FAILED);
        var current = Payment.builder().id(2L).bookingId(51L).driverId(7L).paymentType(PaymentTypeEnum.DEPOSIT)
                .status(PaymentStatusEnum.SUCCESS).orderCode(9001L).build();
        when(payments.findByOrderCode(511L)).thenReturn(Optional.of(old));
        when(payments.findAttemptsForUpdate(51L, PaymentTypeEnum.DEPOSIT)).thenReturn(List.of(old, current));
        when(payments.findByIdForUpdate(2L)).thenReturn(Optional.of(current));
        assertEquals(PaymentStatusEnum.SUCCESS, service.syncPayOSPayment(7L, 511L, 51L, null).getStatus());
        verifyNoInteractions(payOS);
    }

    @Test void latePaidWebhookForOlderAttemptGoesToReviewWithoutCreditingTwice() throws Exception {
        var old = attempt(PaymentStatusEnum.FAILED);
        var current = Payment.builder().id(2L).bookingId(51L).driverId(7L).paymentType(PaymentTypeEnum.DEPOSIT)
                .status(PaymentStatusEnum.PENDING).orderCode(9001L).build();
        when(payments.findAttemptsForUpdate(51L, PaymentTypeEnum.DEPOSIT)).thenReturn(List.of(old, current));
        when(payments.findByOrderCode(511L)).thenReturn(Optional.of(old));
        var webhook = mock(vn.payos.model.webhooks.Webhook.class);
        var data = mock(vn.payos.model.webhooks.WebhookData.class);
        when(mapper.readValue("signed", vn.payos.model.webhooks.Webhook.class)).thenReturn(webhook);
        when(payOS.webhooks().verify(webhook)).thenReturn(data);
        when(data.getOrderCode()).thenReturn(511L); when(data.getCode()).thenReturn("00");
        when(data.getAmount()).thenReturn(4000L); when(data.getCurrency()).thenReturn("VND");
        service.handleWebhook("signed");
        assertEquals(PaymentStatusEnum.REQUIRES_REVIEW, old.getStatus());
        verifyNoInteractions(verifier, wallet, events);
    }

    @Test void missingPayOsLinkWithZeroMoneyRetiresOldAttemptAndGeneratesNew() {
        Payment old = attempt(PaymentStatusEnum.FAILED);
        quote();
        when(payOS.paymentRequests().get(511L)).thenReturn(null);
        when(guard.nextOrderCode()).thenReturn(9002L);
        var result = mock(CreatePaymentLinkResponse.class);
        when(result.getOrderCode()).thenReturn(9002L);
        when(result.getAmount()).thenReturn(4000L);
        when(result.getCheckoutUrl()).thenReturn("https://pay.payos.vn/web/fresh");
        when(payOS.paymentRequests().create(any(CreatePaymentLinkRequest.class))).thenReturn(result);

        assertEquals("https://pay.payos.vn/web/fresh", service.createPaymentLink(7L, request()));
        assertEquals(PaymentStatusEnum.FAILED, old.getStatus());
        verify(guard).nextOrderCode();
    }

    @Test void disablingRecoveryBlocksRetryButKeepsHistory() {
        attempt(PaymentStatusEnum.FAILED); quote(); provider(511, PaymentLinkStatus.CANCELLED, 0L);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "payosRecoveryEnabled", false);
        assertFalse(service.syncPayOSPayment(7L, null, 51L, null).isCanRetry());
        assertThrows(AppException.class, () -> service.createPaymentLink(7L, request()));
        verify(guard, never()).nextOrderCode();
    }
}
