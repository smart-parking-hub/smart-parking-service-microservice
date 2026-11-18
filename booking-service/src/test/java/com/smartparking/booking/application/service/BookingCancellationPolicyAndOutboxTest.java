package com.smartparking.booking.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.booking.application.dto.CancelBookingRequest;
import com.smartparking.booking.application.service.impl.BookingServiceImpl;
import com.smartparking.booking.application.service.impl.OutboxPublisherServiceImpl;
import com.smartparking.booking.domain.BookingRefundPolicy;
import com.smartparking.booking.domain.model.Booking;
import com.smartparking.booking.domain.repository.BookingRepository;
import com.smartparking.booking.domain.repository.OutboxEventRepository;
import com.smartparking.booking.domain.repository.ProcessedEventRepository;
import com.smartparking.booking.infrastructure.client.OwnedVehicleClient;
import com.smartparking.booking.infrastructure.client.ParkingLotClient;
import com.smartparking.booking.infrastructure.messaging.PaymentEventListener;
import com.smartparking.common.enums.BookingStatusEnum;
import com.smartparking.common.enums.VehicleTypeEnum;
import com.smartparking.common.event.PaymentEvent;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingCancellationPolicyAndOutboxTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private ParkingLotClient parkingLotClient;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private QueueService queueService;

    @Mock
    private OwnedVehicleClient ownedVehicleClient;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Test
    @DisplayName("BookingRefundPolicy enforces exact 2h/0h boundaries, HALF_UP VND rounding, PENDING_PAYMENT=0%, and No-Show=0%")
    void refundPolicyEnforcesExactTimeBoundariesAndRounding() {
        LocalDateTime checkIn = LocalDateTime.of(2026, 11, 10, 14, 0, 0);
        BigDecimal oddDeposit = new BigDecimal("25001.00"); // 50% is 12500.5 -> rounds HALF_UP to 12501 VND

        // 1. Exactly T - 2h (12:00:00) -> 100% refund
        BookingRefundPolicy.RefundDecision atTwoHoursBefore = BookingRefundPolicy.evaluateDriverCancel(
                BookingStatusEnum.PENDING_CHECKIN,
                oddDeposit,
                checkIn,
                checkIn.minusHours(2)
        );
        assertEquals(100, atTwoHoursBefore.refundPercentage());
        assertEquals(0, new BigDecimal("25001").compareTo(atTwoHoursBefore.refundAmount()));
        assertTrue(atTwoHoursBefore.refundRequested());
        assertEquals(BookingRefundPolicy.STATUS_REQUESTED, atTwoHoursBefore.refundStatus());

        // 2. Inside (T - 2h, T): 1 second after T - 2h (12:00:01) -> 50% refund with HALF_UP integer VND rounding (12501)
        BookingRefundPolicy.RefundDecision justInsideTwoHours = BookingRefundPolicy.evaluateDriverCancel(
                BookingStatusEnum.PENDING_CHECKIN,
                oddDeposit,
                checkIn,
                checkIn.minusHours(2).plusSeconds(1)
        );
        assertEquals(50, justInsideTwoHours.refundPercentage());
        assertEquals(0, new BigDecimal("12501").compareTo(justInsideTwoHours.refundAmount()));
        assertTrue(justInsideTwoHours.refundRequested());
        assertEquals(BookingRefundPolicy.POLICY_PARTIAL_50, justInsideTwoHours.refundPolicy());

        // 3. 1 second before T (13:59:59) -> 50% refund
        BookingRefundPolicy.RefundDecision oneSecondBeforeCheckIn = BookingRefundPolicy.evaluateDriverCancel(
                BookingStatusEnum.PENDING_CHECKIN,
                new BigDecimal("50000.00"),
                checkIn,
                checkIn.minusSeconds(1)
        );
        assertEquals(50, oneSecondBeforeCheckIn.refundPercentage());
        assertEquals(0, new BigDecimal("25000").compareTo(oneSecondBeforeCheckIn.refundAmount()));

        // 4. Exactly at T (14:00:00) and after T -> 0% refund
        BookingRefundPolicy.RefundDecision atCheckIn = BookingRefundPolicy.evaluateDriverCancel(
                BookingStatusEnum.PENDING_CHECKIN,
                oddDeposit,
                checkIn,
                checkIn
        );
        assertEquals(0, atCheckIn.refundPercentage());
        assertEquals(0, BigDecimal.ZERO.compareTo(atCheckIn.refundAmount()));
        assertFalse(atCheckIn.refundRequested());
        assertEquals(BookingRefundPolicy.STATUS_NONE, atCheckIn.refundStatus());

        // 5. PENDING_PAYMENT cancelled well before T - 2h -> 0% refund
        BookingRefundPolicy.RefundDecision unpaidCancel = BookingRefundPolicy.evaluateDriverCancel(
                BookingStatusEnum.PENDING_PAYMENT,
                oddDeposit,
                checkIn,
                checkIn.minusHours(5)
        );
        assertEquals(0, unpaidCancel.refundPercentage());
        assertEquals(0, BigDecimal.ZERO.compareTo(unpaidCancel.refundAmount()));
        assertFalse(unpaidCancel.refundRequested());
        assertEquals(BookingRefundPolicy.POLICY_ZERO_UNPAID, unpaidCancel.refundPolicy());

        // 6. No-show expiration -> 0% refund
        BookingRefundPolicy.RefundDecision noShow = BookingRefundPolicy.evaluateNoShow(checkIn.plusMinutes(60));
        assertEquals(0, noShow.refundPercentage());
        assertEquals(0, BigDecimal.ZERO.compareTo(noShow.refundAmount()));
        assertFalse(noShow.refundRequested());
        assertEquals(BookingRefundPolicy.POLICY_ZERO_NO_SHOW, noShow.refundPolicy());
    }

    @Test
    @DisplayName("Outbox failure during cancelBooking throws IllegalStateException before releaseSlot is invoked")
    void outboxFailurePropagatesBeforeReleaseSlotAndRollsBackTransaction() throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("Outbox JSON failure") {});

        OutboxPublisherServiceImpl realOutboxPublisher = new OutboxPublisherServiceImpl(
                outboxEventRepository, kafkaTemplate, failingMapper);
        BookingServiceImpl bookingService = new BookingServiceImpl(
                bookingRepository,
                mock(com.smartparking.booking.application.service.UnpaidHoldPolicy.class),
                mock(com.smartparking.booking.application.service.BookingCreationTransaction.class),
                mock(com.smartparking.booking.infrastructure.persistence.HoldPolicyStore.class),
                ownedVehicleClient,
                parkingLotClient,
                realOutboxPublisher,
                queueService
        );

        Booking booking = Booking.builder()
                .id(50L)
                .bookingCode("BK-ROLLBACK")
                .driverId(100L)
                .parkingLotId(200L)
                .vehicleType(VehicleTypeEnum.SEDAN_SUV)
                .status(BookingStatusEnum.PENDING_CHECKIN)
                .advanceDeposit(new BigDecimal("50000"))
                .expectedCheckInTime(LocalDateTime.now(BookingRefundPolicy.BUSINESS_ZONE).plusHours(4))
                .build();
        when(bookingRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(booking));

        CancelBookingRequest request = CancelBookingRequest.builder().reason("Hủy lịch").build();

        assertThrows(IllegalStateException.class,
                () -> bookingService.cancelBooking(100L, 50L, request),
                "Outbox failure must throw RuntimeException so @Transactional rolls back booking cancellation");
        verify(outboxEventRepository, never()).save(any());
        verify(parkingLotClient, never()).releaseSlot(any(), any());
    }

    @Test
    @DisplayName("cancelBooking persists Outbox before calling releaseSlot, and throws INTERNAL_SERVER_ERROR if releaseSlot fails")
    void cancelBookingOrdersOutboxBeforeReleaseSlotAndRollsBackDbWhenReleaseSlotFails() {
        OutboxPublisherService mockOutbox = mock(OutboxPublisherService.class);
        BookingServiceImpl bookingService = new BookingServiceImpl(
                bookingRepository,
                mock(com.smartparking.booking.application.service.UnpaidHoldPolicy.class),
                mock(com.smartparking.booking.application.service.BookingCreationTransaction.class),
                mock(com.smartparking.booking.infrastructure.persistence.HoldPolicyStore.class),
                ownedVehicleClient,
                parkingLotClient,
                mockOutbox,
                queueService
        );

        Booking booking = Booking.builder()
                .id(51L)
                .bookingCode("BK-ORDER")
                .driverId(100L)
                .parkingLotId(200L)
                .vehicleType(VehicleTypeEnum.SEDAN_SUV)
                .status(BookingStatusEnum.PENDING_CHECKIN)
                .advanceDeposit(new BigDecimal("50000"))
                .expectedCheckInTime(LocalDateTime.now(BookingRefundPolicy.BUSINESS_ZONE).plusHours(4))
                .build();
        when(bookingRepository.findByIdForUpdate(51L)).thenReturn(Optional.of(booking));

        CancelBookingRequest request = CancelBookingRequest.builder().reason("Kiểm tra thứ tự").build();
        bookingService.cancelBooking(100L, 51L, request);

        InOrder inOrder = inOrder(bookingRepository, mockOutbox, parkingLotClient);
        inOrder.verify(bookingRepository).save(booking);
        inOrder.verify(mockOutbox).publishBookingCancelled(booking, "Kiểm tra thứ tự");
        inOrder.verify(parkingLotClient).releaseSlot(eq(200L), any());

        // When releaseSlot fails over HTTP, cancelBooking throws AppException(INTERNAL_SERVER_ERROR) to roll back DB
        booking.setStatus(BookingStatusEnum.PENDING_CHECKIN);
        doThrow(new RuntimeException("Parking lot service timeout"))
                .when(parkingLotClient).releaseSlot(eq(200L), any());
        AppException releaseEx = assertThrows(AppException.class,
                () -> bookingService.cancelBooking(100L, 51L, request));
        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR, releaseEx.getErrorCode());
    }

    @Test
    @DisplayName("PaymentEventListener idempotently updates booking refundStatus and refundReference on PAYMENT_REFUND_UPDATED")
    void paymentEventListenerUpdatesBookingRefundStatusIdempotently() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();

        PaymentEventListener listener = new PaymentEventListener(
                bookingRepository, parkingLotClient, mapper, queueService, mock(OutboxPublisherService.class));
        ReflectionTestUtils.setField(listener, "processedEventRepository", processedEventRepository);

        Booking booking = Booking.builder()
                .id(77L)
                .bookingCode("BK-77")
                .status(BookingStatusEnum.CANCELLED)
                .refundStatus("REQUESTED")
                .build();

        when(processedEventRepository.existsById("evt-refund-77")).thenReturn(false, true);
        when(bookingRepository.findByIdForUpdate(77L)).thenReturn(Optional.of(booking));

        PaymentEvent.RefundStatusChangedEvent refundEvent = PaymentEvent.RefundStatusChangedEvent.builder()
                .eventId("evt-refund-77")
                .eventType("PAYMENT_REFUND_UPDATED")
                .bookingId(77L)
                .refundStatus("COMPLETED")
                .transactionReference("FT26110888")
                .build();

        String jsonPayload = mapper.writeValueAsString(refundEvent);
        listener.handlePaymentEvent(jsonPayload);
        // Duplicate delivery of same eventId must be skipped
        listener.handlePaymentEvent(jsonPayload);

        assertEquals("COMPLETED", booking.getRefundStatus());
        assertEquals("FT26110888", booking.getRefundReference());
        verify(bookingRepository, times(1)).save(booking);
    }
}
