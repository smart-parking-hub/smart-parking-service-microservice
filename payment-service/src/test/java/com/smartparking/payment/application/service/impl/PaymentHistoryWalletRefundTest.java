package com.smartparking.payment.application.service.impl;

import com.smartparking.common.enums.*;
import com.smartparking.payment.domain.model.Payment;
import com.smartparking.payment.domain.model.RefundRequest;
import com.smartparking.payment.domain.repository.PaymentRepository;
import com.smartparking.payment.domain.repository.RefundRequestRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentHistoryWalletRefundTest {
    @ParameterizedTest
    @NullSource
    @ValueSource(longs = 41L)
    void historyKeepsRefundFieldsWithAndWithoutBookingFilter(Long bookingId) {
        var payments = mock(PaymentRepository.class);
        var refunds = mock(RefundRequestRepository.class);
        var service = new PaymentServiceImpl(null, null, null, payments, null, null, null, null, null, null, null, null);
        ReflectionTestUtils.setField(service, "refundRequests", refunds);
        var pageable = PageRequest.of(0, 20);
        var payment = Payment.builder().id(71L).driverId(7L).bookingId(41L).merchantId(9L)
                .gateway(PaymentGatewayEnum.WALLET).paymentType(PaymentTypeEnum.DEPOSIT)
                .status(PaymentStatusEnum.SUCCESS).amount(new BigDecimal("16000")).build();
        var page = new PageImpl<>(List.of(payment));
        if (bookingId == null) when(payments.findByDriverId(7L, pageable)).thenReturn(page);
        else when(payments.findByDriverIdAndBookingId(7L, bookingId, pageable)).thenReturn(page);
        when(refunds.findByPaymentIdIn(List.of(71L))).thenReturn(List.of(RefundRequest.builder()
                .paymentId(71L).refundAmount(new BigDecimal("8000")).status("COMPLETED")
                .refundChannel("WALLET").transactionReference("WALLET-REFUND-71").build()));

        var result = service.getMyPayments(7L, bookingId, pageable).getContent().getFirst();
        assertEquals(PaymentStatusEnum.SUCCESS, result.getStatus());
        assertEquals(new BigDecimal("8000"), result.getRefundAmount());
        assertEquals("COMPLETED", result.getRefundStatus());
        assertEquals("WALLET", result.getRefundChannel());
        assertEquals("WALLET-REFUND-71", result.getRefundReference());
        if (bookingId == null) verify(payments).findByDriverId(7L, pageable);
        else verify(payments).findByDriverIdAndBookingId(7L, bookingId, pageable);
        verifyNoMoreInteractions(payments);
    }
}
