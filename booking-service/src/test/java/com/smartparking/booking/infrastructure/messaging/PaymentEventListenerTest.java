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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

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

    @InjectMocks
    private PaymentEventListener listener;

    @Test
    @DisplayName("Handle deposit payment completed transitions PENDING_PAYMENT to PENDING_CHECKIN")
    void testDepositPaymentCompleted() throws Exception {
        String payload = "{\"bookingId\":10,\"amount\":50000}";
        PaymentEvent.PaymentCompletedEvent event = PaymentEvent.PaymentCompletedEvent.builder()
                .bookingId(10L)
                .amount(new BigDecimal("50000"))
                .build();
        when(objectMapper.readValue(payload, PaymentEvent.PaymentCompletedEvent.class)).thenReturn(event);

        Booking booking = Booking.builder()
                .id(10L)
                .bookingCode("BK10")
                .status(BookingStatusEnum.PENDING_PAYMENT)
                .build();
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));

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
                .bookingId(20L)
                .amount(new BigDecimal("30000"))
                .build();
        when(objectMapper.readValue(payload, PaymentEvent.PaymentCompletedEvent.class)).thenReturn(event);

        Booking booking = Booking.builder()
                .id(20L)
                .parkingLotId(99L)
                .vehicleType(VehicleTypeEnum.CONTAINER)
                .bookingCode("BK20")
                .status(BookingStatusEnum.AWAITING_FINAL_PAYMENT)
                .build();
        when(bookingRepository.findById(20L)).thenReturn(Optional.of(booking));

        listener.handlePaymentEvent(payload);

        assertEquals(BookingStatusEnum.COMPLETED, booking.getStatus());
        verify(bookingRepository, times(1)).save(booking);
        verify(parkingLotClient, times(1)).releaseSlot(eq(99L), any(SlotHoldRequest.class));
    }
}
