package com.smartparking.payment.application.service;

import com.smartparking.common.enums.*;
import com.smartparking.payment.domain.model.Payment;
import com.smartparking.payment.domain.repository.PaymentRepository;
import com.smartparking.payment.infrastructure.client.BookingPaymentVerifier;
import com.smartparking.payment.infrastructure.persistence.PaymentAttemptGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.smartparking.payment.application.service.UnpaidHoldAssessmentService.Outcome.*;

class UnpaidHoldAssessmentServiceTest {
    final PaymentRepository payments=mock(PaymentRepository.class);
    final PaymentAttemptGuard guard=mock(PaymentAttemptGuard.class);
    final BookingPaymentVerifier bookings=mock(BookingPaymentVerifier.class);
    final PayOS payOS=mock(PayOS.class,RETURNS_DEEP_STUBS);
    final CircuitBreakerFactory<?,?> breakers=mock(CircuitBreakerFactory.class);
    final CircuitBreaker breaker=mock(CircuitBreaker.class);
    final UnpaidHoldAssessmentService service=new UnpaidHoldAssessmentService(payments,guard,bookings,payOS,breakers);
    @BeforeEach void setup() {
        when(breakers.create(anyString())).thenReturn(breaker);
        when(breaker.run(any(),any())).thenAnswer(call->{ Supplier<?> task=call.getArgument(0); return task.get(); });
        when(bookings.holdState(51L)).thenReturn(new BookingPaymentVerifier.HoldState(51L,7L,"EXPIRED",true));
    }
    Payment payment(PaymentStatusEnum status) {
        var p=Payment.builder().id(1L).bookingId(51L).driverId(7L).paymentType(PaymentTypeEnum.DEPOSIT)
                .gateway(PaymentGatewayEnum.PAYOS).status(status).amount(new BigDecimal("4000")).orderCode(511L).build();
        when(payments.findAttemptsForUpdate(51L,PaymentTypeEnum.DEPOSIT)).thenReturn(List.of(p)); return p;
    }
    PaymentLink link(PaymentLinkStatus status, Long paid) {
        var l=mock(PaymentLink.class);
        when(l.getStatus()).thenReturn(status); when(l.getOrderCode()).thenReturn(511L);
        when(l.getAmount()).thenReturn(4000L); when(l.getAmountPaid()).thenReturn(paid); return l;
    }
    @Test void noAttemptCanBeUnpaidOnlyAfterTerminalCandidateWasVerified() {
        assertEquals(UNPAID,service.assess(51L));
        var order=inOrder(guard,bookings,payments); order.verify(guard).lock(51L,PaymentTypeEnum.DEPOSIT);
        order.verify(bookings).holdState(51L); order.verify(payments).findAttemptsForUpdate(51L,PaymentTypeEnum.DEPOSIT);
        verifyNoInteractions(payOS);
    }
    @Test void activeBookingIsNeverCancelledOrPenalized() {
        when(bookings.holdState(51L)).thenReturn(new BookingPaymentVerifier.HoldState(51L,7L,"PENDING_PAYMENT",false));
        assertEquals(PROTECTED,service.assess(51L)); verifyNoInteractions(payments,payOS);
    }
    @Test void paidReviewAndRefundAreProtectedWithoutTouchingProvider() {
        for(var status:List.of(PaymentStatusEnum.SUCCESS,PaymentStatusEnum.REQUIRES_REVIEW,PaymentStatusEnum.REFUNDED)) {
            payment(status); assertEquals(PROTECTED,service.assess(51L));
        }
        verifyNoInteractions(payOS);
    }
    @Test void zeroPendingLinkIsCancelledAndOnlyConfirmedCancellationCounts() {
        var p=payment(PaymentStatusEnum.PENDING);
        when(payOS.paymentRequests().get(511L)).thenAnswer(inv -> link(PaymentLinkStatus.PENDING,0L));
        when(payOS.paymentRequests().cancel(eq(511L),anyString())).thenAnswer(inv -> link(PaymentLinkStatus.CANCELLED,0L));
        assertEquals(UNPAID,service.assess(51L)); assertEquals(PaymentStatusEnum.FAILED,p.getStatus());
        verify(payments).save(p);
    }
    @Test void processingWithNoReportedFundsIsNotAnUnpaidProof() {
        payment(PaymentStatusEnum.PENDING);
        when(payOS.paymentRequests().get(511L)).thenAnswer(inv -> link(PaymentLinkStatus.PROCESSING,0L));
        assertEquals(UNKNOWN,service.assess(51L));
        verify(payOS.paymentRequests(),never()).cancel(anyLong(),anyString());
    }
    @Test void cancellationReceivingMoneyIsProtectedAndReviewed() {
        var p=payment(PaymentStatusEnum.PENDING);
        when(payOS.paymentRequests().get(511L)).thenAnswer(inv -> link(PaymentLinkStatus.CANCELLED,1000L));
        assertEquals(PROTECTED,service.assess(51L)); assertEquals(PaymentStatusEnum.REQUIRES_REVIEW,p.getStatus());
        verify(payOS.paymentRequests(),never()).cancel(anyLong(),anyString());
    }
    @Test void pendingCancellationRaceReceivingMoneyIsNotPenalized() {
        payment(PaymentStatusEnum.PENDING);
        when(payOS.paymentRequests().get(511L)).thenAnswer(inv -> link(PaymentLinkStatus.PENDING,0L));
        when(payOS.paymentRequests().cancel(eq(511L),anyString())).thenAnswer(inv -> link(PaymentLinkStatus.PAID,4000L));
        assertEquals(PROTECTED,service.assess(51L));
    }
    @Test void providerFailureIsUnknownWithoutChangingPayment() {
        var p=payment(PaymentStatusEnum.PENDING);
        when(payOS.paymentRequests().get(511L)).thenThrow(new RuntimeException("provider down"));
        assertEquals(UNKNOWN,service.assess(51L)); assertEquals(PaymentStatusEnum.PENDING,p.getStatus());
        verify(payments,never()).save(any());
    }
    @Test void missingAmountDoesNotBecomeAFalseUnpaidStrike() {
        payment(PaymentStatusEnum.PENDING);
        when(payOS.paymentRequests().get(511L)).thenAnswer(inv -> link(PaymentLinkStatus.CANCELLED,null));
        assertEquals(UNKNOWN,service.assess(51L));
    }
    @Test void wrongOrderIsNotCancelledOrCounted() {
        payment(PaymentStatusEnum.PENDING);var l=link(PaymentLinkStatus.PENDING,0L);when(l.getOrderCode()).thenReturn(999L);
        when(payOS.paymentRequests().get(511L)).thenReturn(l);
        assertEquals(UNKNOWN,service.assess(51L));verify(payOS.paymentRequests(),never()).cancel(anyLong(),anyString());
    }
}
