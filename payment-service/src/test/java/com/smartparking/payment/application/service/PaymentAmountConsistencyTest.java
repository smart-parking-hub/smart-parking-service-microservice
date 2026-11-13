package com.smartparking.payment.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.common.enums.PaymentStatusEnum;
import com.smartparking.common.enums.PaymentTypeEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.payment.application.dto.CreatePaymentRequest;
import com.smartparking.payment.application.service.impl.PaymentServiceImpl;
import com.smartparking.payment.config.PayOSConfig;
import com.smartparking.payment.domain.model.Payment;
import com.smartparking.payment.domain.repository.*;
import com.smartparking.payment.infrastructure.client.BookingPaymentVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentAmountConsistencyTest {
    @Mock private BookingPaymentVerifier bookingPaymentVerifier;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS) private PayOS payOS;
    @Mock private PayOSConfig payOSConfig;
    @Mock private PaymentRepository paymentRepository;
    @Mock private MerchantWalletService merchantWalletService;
    @Mock private OutboxPublisherService outboxPublisherService;
    @Mock private ObjectMapper objectMapper;
    @Mock private WithdrawalRequestRepository withdrawalRequestRepository;
    @Mock private DisputeRepository disputeRepository;
    @Mock private WalletLedgerEntryRepository walletLedgerEntryRepository;
    @InjectMocks private PaymentServiceImpl service;

    private static final long BOOKING_ID = 41L;
    private static final long DRIVER_ID = 7L;
    private static final long ORDER_CODE = BOOKING_ID * 10 + PaymentTypeEnum.DEPOSIT.ordinal() + 1;
    private static final String CHECKOUT = "https://pay.payos.vn/web/test-4000";

    @BeforeEach
    void setUp() {
        lenient().when(bookingPaymentVerifier.require(BOOKING_ID, DRIVER_ID)).thenReturn(
                new BookingPaymentVerifier.Quote(BOOKING_ID, DRIVER_ID, 9L, 3L,
                        "PENDING_PAYMENT", new BigDecimal("4000.00"), new BigDecimal("20000"),
                        LocalDateTime.now().plusMinutes(15)));
    }

    @Test
    void sendsExactlyFourThousandDongToPayOSWithoutFiftyThousandFloorOrClientOverride() {
        givenCreateResponse(4000L, ORDER_CODE);
        assertEquals(CHECKOUT, service.createPaymentLink(DRIVER_ID, request()));
        var captor = ArgumentCaptor.forClass(CreatePaymentLinkRequest.class);
        verify(payOS.paymentRequests()).create(captor.capture());
        assertEquals(4000L, captor.getValue().getAmount());
        assertEquals(4000L, captor.getValue().getItems().getFirst().getPrice());
        var saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).saveAndFlush(saved.capture());
        assertEquals(0, new BigDecimal("4000").compareTo(saved.getValue().getAmount()));
    }

    @Test
    void neverReturnsProviderCheckoutForFiftyThousandWhenBookingOwesFourThousand() {
        givenCreateResponse(50000L, ORDER_CODE);
        AppException error = assertThrows(AppException.class,
                () -> service.createPaymentLink(DRIVER_ID, request()));
        assertEquals(ErrorCode.PAYMENT_FAILED, error.getErrorCode());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void neverReturnsProviderCheckoutForAnotherOrder() {
        givenCreateResponse(4000L, ORDER_CODE + 10);
        assertThrows(AppException.class, () -> service.createPaymentLink(DRIVER_ID, request()));
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void rejectsCachedFiftyThousandPaymentWhenCurrentDepositIsFourThousand() {
        givenCachedPayment(new BigDecimal("50000"));
        AppException error = assertThrows(AppException.class,
                () -> service.createPaymentLink(DRIVER_ID, request()));
        assertEquals(ErrorCode.CONFLICT, error.getErrorCode());
        verifyNoInteractions(payOS);
    }

    @Test
    void checksCachedProviderAmountBeforeReusingLink() {
        givenCachedPayment(new BigDecimal("4000"));
        givenHostedLink(50000L, PaymentLinkStatus.PENDING);
        AppException error = assertThrows(AppException.class,
                () -> service.createPaymentLink(DRIVER_ID, request()));
        assertEquals(ErrorCode.CONFLICT, error.getErrorCode());
        verify(paymentRepository, never()).saveAndFlush(any(Payment.class));
    }

    @Test
    void reusesMatchingCachedLinkWithoutCreatingAnotherCharge() {
        givenCachedPayment(new BigDecimal("4000"));
        givenHostedLink(4000L, PaymentLinkStatus.PENDING);
        assertEquals(CHECKOUT, service.createPaymentLink(DRIVER_ID, request()));
        verify(bookingPaymentVerifier).require(BOOKING_ID, DRIVER_ID);
        verify(payOS.paymentRequests(), never()).create(any(CreatePaymentLinkRequest.class));
        verify(paymentRepository, never()).saveAndFlush(any(Payment.class));
    }

    @Test
    void blocksCachedLinkIfPayOSCannotBeVerified() {
        givenCachedPayment(new BigDecimal("4000"));
        when(payOS.paymentRequests().get(ORDER_CODE)).thenThrow(new RuntimeException("network unavailable"));
        AppException error = assertThrows(AppException.class,
                () -> service.createPaymentLink(DRIVER_ID, request()));
        assertEquals(ErrorCode.PAYMENT_FAILED, error.getErrorCode());
    }

    @Test
    void blocksCancelledHostedLink() {
        givenCachedPayment(new BigDecimal("4000"));
        givenHostedLink(4000L, PaymentLinkStatus.CANCELLED);
        assertThrows(AppException.class, () -> service.createPaymentLink(DRIVER_ID, request()));
    }

    @Test
    void blocksCachedPaymentForDifferentMerchantOrLotEvenWhenAmountMatches() {
        Payment previous = givenCachedPayment(new BigDecimal("4000"));
        previous.setMerchantId(999L);
        assertThrows(AppException.class, () -> service.createPaymentLink(DRIVER_ID, request()));
        previous.setMerchantId(9L);
        previous.setParkingLotId(999L);
        assertThrows(AppException.class, () -> service.createPaymentLink(DRIVER_ID, request()));
        verifyNoInteractions(payOS);
    }

    @Test
    void rejectsMissingCheckoutUrlBeforeSavingIt() {
        var response = givenCreateResponse(4000L, ORDER_CODE);
        when(response.getCheckoutUrl()).thenReturn(" ");
        assertThrows(AppException.class, () -> service.createPaymentLink(DRIVER_ID, request()));
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    private CreatePaymentRequest request() {
        // The client's stale/tampered amount must never replace the authoritative quote.
        return CreatePaymentRequest.builder().bookingId(BOOKING_ID).paymentType("DEPOSIT")
                .amount(new BigDecimal("50000")).build();
    }

    private CreatePaymentLinkResponse givenCreateResponse(Long amount, Long orderCode) {
        when(payOSConfig.getReturnUrl()).thenReturn("http://localhost:3000/payment/return");
        when(payOSConfig.getCancelUrl()).thenReturn("http://localhost:3000/payment/cancel");
        var result = mock(CreatePaymentLinkResponse.class);
        lenient().when(result.getAmount()).thenReturn(amount);
        lenient().when(result.getOrderCode()).thenReturn(orderCode);
        lenient().when(result.getCheckoutUrl()).thenReturn(CHECKOUT);
        when(payOS.paymentRequests().create(any(CreatePaymentLinkRequest.class))).thenReturn(result);
        return result;
    }

    private Payment givenCachedPayment(BigDecimal amount) {
        Payment previous = Payment.builder().id(11L).bookingId(BOOKING_ID)
                .driverId(DRIVER_ID).merchantId(9L).parkingLotId(3L)
                .paymentType(PaymentTypeEnum.DEPOSIT).status(PaymentStatusEnum.PENDING)
                .amount(amount).orderCode(ORDER_CODE).checkoutUrl(CHECKOUT)
                .bookingExpiresAt(LocalDateTime.now().plusMinutes(15)).build();
        when(paymentRepository.findByIdempotencyKey("booking:" + BOOKING_ID + ":DEPOSIT"))
                .thenReturn(Optional.of(previous));
        return previous;
    }

    private void givenHostedLink(Long amount, PaymentLinkStatus status) {
        var link = mock(PaymentLink.class);
        lenient().when(link.getAmount()).thenReturn(amount);
        lenient().when(link.getOrderCode()).thenReturn(ORDER_CODE);
        lenient().when(link.getStatus()).thenReturn(status);
        when(payOS.paymentRequests().get(ORDER_CODE)).thenReturn(link);
    }
}
