package com.smartparking.payment.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.common.enums.PaymentStatusEnum;
import com.smartparking.payment.application.service.impl.PaymentServiceImpl;
import com.smartparking.payment.config.PayOSConfig;
import com.smartparking.payment.domain.model.Payment;
import com.smartparking.payment.domain.repository.*;
import com.smartparking.payment.infrastructure.client.BookingPaymentVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayOsCancellationSyncTest {
    @Mock BookingPaymentVerifier verifier;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS) PayOS payOS;
    @Mock PayOSConfig config;
    @Mock PaymentRepository payments;
    @Mock MerchantWalletService wallet;
    @Mock OutboxPublisherService events;
    @Mock ObjectMapper mapper;
    @Mock WithdrawalRequestRepository withdrawals;
    @Mock DisputeRepository disputes;
    @Mock WalletLedgerEntryRepository ledger;
    @Mock private com.smartparking.payment.infrastructure.persistence.PaymentAttemptGuard attemptGuard;
    @InjectMocks PaymentServiceImpl service;

    private Payment payment(PaymentStatusEnum status) {
        Payment payment = Payment.builder().id(1L).bookingId(51L).driverId(7L)
                .orderCode(511L).amount(new BigDecimal("4000")).status(status).build();
        when(payments.findByBookingId(51L)).thenReturn(List.of(payment));
        lenient().when(payments.findAttemptsForUpdate(51L, null)).thenReturn(List.of(payment));
        when(payments.findByIdForUpdate(1L)).thenReturn(Optional.of(payment));
        return payment;
    }

    private void cancelled(Long amountPaid, Long orderCode) {
        var link = mock(PaymentLink.class);
        lenient().when(link.getStatus()).thenReturn(PaymentLinkStatus.CANCELLED);
        lenient().when(link.getOrderCode()).thenReturn(orderCode);
        lenient().when(link.getAmount()).thenReturn(4000L);
        lenient().when(link.getAmountPaid()).thenReturn(amountPaid);
        lenient().when(payOS.paymentRequests().get(511L)).thenReturn(link);
    }

    @Test void cancelledUnpaidLinkBecomesFailedWithoutBookingOrLedgerSideEffects() {
        Payment payment = payment(PaymentStatusEnum.PENDING);
        cancelled(0L, 511L);
        assertEquals(PaymentStatusEnum.FAILED, service.syncPayOSPayment(7L, null, 51L, null).getStatus());
        verify(payments).save(payment);
        verifyNoInteractions(wallet, events);
    }

    @Test void cancellationWithMoneyReceivedRequiresReconciliation() {
        Payment payment = payment(PaymentStatusEnum.PENDING);
        cancelled(1000L, 511L);
        assertEquals(PaymentStatusEnum.REQUIRES_REVIEW, service.syncPayOSPayment(7L, null, 51L, null).getStatus());
        assertNotNull(payment.getReviewReason());
        verifyNoInteractions(wallet, events);
    }

    @Test void missingPaidAmountDoesNotDeclarePaymentFailed() {
        payment(PaymentStatusEnum.PENDING);
        cancelled(null, 511L);
        assertEquals(PaymentStatusEnum.PENDING, service.syncPayOSPayment(7L, null, 51L, null).getStatus());
        verify(payments, never()).save(any());
    }

    @Test void mismatchedProviderOrderCannotChangePayment() {
        payment(PaymentStatusEnum.PENDING);
        cancelled(0L, 999L);
        assertEquals(PaymentStatusEnum.PENDING, service.syncPayOSPayment(7L, null, 51L, null).getStatus());
        verify(payments, never()).save(any());
    }

    @Test void cancellationCannotDowngradeReconciliationOrRefundedPayment() {
        for (var status : List.of(PaymentStatusEnum.REQUIRES_REVIEW, PaymentStatusEnum.REFUNDED)) {
            reset(payments, payOS);
            payment(status);
            cancelled(0L, 511L);
            assertEquals(status, service.syncPayOSPayment(7L, null, 51L, null).getStatus());
            verify(payments, never()).save(any());
        }
    }
}
