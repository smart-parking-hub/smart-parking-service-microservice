package com.smartparking.booking.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.booking.domain.model.Booking;
import com.smartparking.booking.domain.repository.BookingRepository;
import com.smartparking.booking.infrastructure.client.ParkingLotClient;
import com.smartparking.booking.infrastructure.client.dto.SlotHoldRequest;
import com.smartparking.common.enums.BookingStatusEnum;
import com.smartparking.common.enums.VehicleTypeEnum;
import com.smartparking.common.event.PaymentEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.TimeZone;

import static com.smartparking.booking.domain.BookingRefundPolicy.BUSINESS_ZONE;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentEventListenerTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private ParkingLotClient parkingLotClient;

    @Mock
    private ObjectMapper objectMapper;

    @Mock private com.smartparking.booking.application.service.QueueService queueService;



    @Mock private com.smartparking.booking.application.service.OutboxPublisherService outbox;

    @InjectMocks
    private PaymentEventListener listener;

    @Test
    @DisplayName("Handle deposit payment completed transitions PENDING_PAYMENT to PENDING_CHECKIN")
    void testDepositPaymentCompleted() throws Exception {
        String payload = "{\"bookingId\":10,\"amount\":50000}";
        PaymentEvent.PaymentCompletedEvent event = PaymentEvent.PaymentCompletedEvent.builder()
                .bookingId(10L).paymentType("DEPOSIT")
                .amount(new BigDecimal("50000"))
                .build();
        when(objectMapper.readValue(payload, PaymentEvent.PaymentCompletedEvent.class)).thenReturn(event);

        Booking booking = Booking.builder()
                .id(10L)
                .bookingCode("BK10")
                .advanceDeposit(new BigDecimal("50000")).expiresAt(LocalDateTime.now(BUSINESS_ZONE).plusMinutes(10)).status(BookingStatusEnum.PENDING_PAYMENT)
                .build();
        when(bookingRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(booking));

        listener.handlePaymentEvent(payload);

        assertEquals(BookingStatusEnum.PENDING_CHECKIN, booking.getStatus());
        assertEquals(new BigDecimal("50000"), booking.getAdvanceDeposit());
        verify(bookingRepository, times(1)).save(booking);
    }

    @Test
    @DisplayName("Handle final payment completed transitions AWAITING_FINAL_PAYMENT to COMPLETED and releases slot")
    void testFinalPaymentCompleted() throws Exception {
        String payload = "{\"bookingId\":20,\"amount\":30000}";
        PaymentEvent.PaymentCompletedEvent event = PaymentEvent.PaymentCompletedEvent.builder()
                .bookingId(20L).paymentId(100L).paymentType("FINAL_PAYMENT")
                .amount(new BigDecimal("30000"))
                .build();
        when(objectMapper.readValue(payload, PaymentEvent.PaymentCompletedEvent.class)).thenReturn(event);

        Booking booking = Booking.builder()
                .id(20L)
                .parkingLotId(99L)
                .vehicleType(VehicleTypeEnum.CONTAINER)
                .bookingCode("BK20")
                .totalAmount(new BigDecimal("80000")).advanceDeposit(new BigDecimal("50000")).status(BookingStatusEnum.AWAITING_FINAL_PAYMENT)
                .build();
        when(bookingRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(booking));

        listener.handlePaymentEvent(payload);

        assertEquals(BookingStatusEnum.COMPLETED, booking.getStatus());
        verify(bookingRepository, times(1)).save(booking);
        listener.handlePaymentEvent(payload);
        verify(parkingLotClient, times(1)).releaseSlot(eq(99L), any(SlotHoldRequest.class));
        verify(outbox, times(1)).publishBookingCompleted(booking);
    }
    @Test void replayedDepositCannotSettleFinalBill() {
        Booking b = Booking.builder().id(20L).status(BookingStatusEnum.AWAITING_FINAL_PAYMENT)
                .advanceDeposit(new BigDecimal("50000")).totalAmount(new BigDecimal("100000")).build();
        when(bookingRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(b));
        listener.handlePaymentEvent(PaymentEvent.PaymentCompletedEvent.builder().bookingId(20L)
                .paymentType("DEPOSIT").amount(new BigDecimal("50000")).build());
        assertEquals(BookingStatusEnum.AWAITING_FINAL_PAYMENT,b.getStatus());
        verifyNoInteractions(parkingLotClient);
    }

    @ParameterizedTest
    @ValueSource(ints = {-5, -1, 0})
    void expiredDepositCannotConfirmOnUtcHost(int minutesUntilExpiry) {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            LocalDateTime expiry = LocalDateTime.now(BUSINESS_ZONE).plusMinutes(minutesUntilExpiry);
            Booking booking = pendingDeposit(expiry, LocalDateTime.now(BUSINESS_ZONE).plusHours(1));

            listener.handlePaymentEvent(depositEvent());

            assertEquals(BookingStatusEnum.PENDING_PAYMENT, booking.getStatus());
            assertEquals(expiry, booking.getExpiresAt());
            verify(bookingRepository, never()).save(any());
            verifyNoInteractions(parkingLotClient, queueService);
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void validDepositKeepsExpectedCheckInDeadlineOnUtcHost() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
            Booking booking = pendingDeposit(now.plusMinutes(10), now.plusHours(4));

            listener.handlePaymentEvent(depositEvent());

            assertEquals(BookingStatusEnum.PENDING_CHECKIN, booking.getStatus());
            assertEquals(now.plusHours(5), booking.getExpiresAt());
            verify(bookingRepository).save(booking);
            verifyNoInteractions(parkingLotClient, queueService);
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void missingExpectedCheckInUsesVietnamFallbackOnUtcHost() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            LocalDateTime before = LocalDateTime.now(BUSINESS_ZONE);
            Booking booking = pendingDeposit(before.plusMinutes(10), null);

            listener.handlePaymentEvent(depositEvent());

            LocalDateTime after = LocalDateTime.now(BUSINESS_ZONE);
            assertEquals(BookingStatusEnum.PENDING_CHECKIN, booking.getStatus());
            assertFalse(booking.getExpiresAt().isBefore(before.plusHours(2)));
            assertFalse(booking.getExpiresAt().isAfter(after.plusHours(2)));
            verify(bookingRepository).save(booking);
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void depositAtExactExpiryIsRejected() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 15, 14, 15);
        Booking booking = pendingDeposit(now, now.plusHours(4));
        try (var time = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            time.when(() -> LocalDateTime.now(BUSINESS_ZONE)).thenReturn(now);

            listener.handlePaymentEvent(depositEvent());

            assertEquals(BookingStatusEnum.PENDING_PAYMENT, booking.getStatus());
            assertEquals(now, booking.getExpiresAt());
            verify(bookingRepository, never()).save(any());
            verifyNoInteractions(parkingLotClient, queueService);
        }
    }

    @Test
    void missingDepositDeadlineCannotConfirm() {
        Booking booking = pendingDeposit(null, LocalDateTime.now(BUSINESS_ZONE).plusHours(4));

        listener.handlePaymentEvent(depositEvent());

        assertEquals(BookingStatusEnum.PENDING_PAYMENT, booking.getStatus());
        assertNull(booking.getExpiresAt());
        verify(bookingRepository, never()).save(any());
        verifyNoInteractions(parkingLotClient, queueService);
    }

    @Test
    void repeatedDepositDoesNotExtendConfirmedDeadline() {
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        Booking booking = pendingDeposit(now.plusMinutes(10), now.plusHours(4));

        listener.handlePaymentEvent(depositEvent());
        LocalDateTime deadline = booking.getExpiresAt();
        listener.handlePaymentEvent(depositEvent());

        assertEquals(BookingStatusEnum.PENDING_CHECKIN, booking.getStatus());
        assertEquals(now.plusHours(5), deadline);
        assertEquals(deadline, booking.getExpiresAt());
        verify(bookingRepository, times(1)).save(booking);
        verifyNoInteractions(parkingLotClient, queueService);
    }

    private Booking pendingDeposit(LocalDateTime expiry, LocalDateTime expectedCheckIn) {
        Booking booking = Booking.builder().id(10L).bookingCode("BK10")
                .advanceDeposit(new BigDecimal("50000")).status(BookingStatusEnum.PENDING_PAYMENT)
                .expiresAt(expiry).expectedCheckInTime(expectedCheckIn).build();
        when(bookingRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(booking));
        return booking;
    }

    private PaymentEvent.PaymentCompletedEvent depositEvent() {
        return PaymentEvent.PaymentCompletedEvent.builder().bookingId(10L)
                .paymentType("DEPOSIT").amount(new BigDecimal("50000")).build();
    }
}
