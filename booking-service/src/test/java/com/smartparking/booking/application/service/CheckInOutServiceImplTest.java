package com.smartparking.booking.application.service;

import com.smartparking.booking.application.dto.CheckOutResponse;
import com.smartparking.booking.application.service.impl.CheckInOutServiceImpl;
import com.smartparking.booking.domain.model.Booking;
import com.smartparking.booking.domain.repository.BookingRepository;
import com.smartparking.booking.infrastructure.client.ParkingLotClient;
import com.smartparking.booking.infrastructure.client.dto.SlotHoldRequest;
import com.smartparking.common.enums.BookingStatusEnum;
import com.smartparking.common.enums.VehicleTypeEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckInOutServiceImplTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private ParkingLotClient parkingLotClient;

    @Mock
    private OutboxPublisherService outboxPublisherService;

    @Mock
    private QueueService queueService;

    @InjectMocks
    private CheckInOutServiceImpl checkInOutService;

    private final Long merchantUserId = 888L;
    private final String bookingCode = "BK-INOUT-123";
    private final Long parkingLotId = 50L;

    @Test
    @DisplayName("Check-in fails when booking not found")
    void testCheckInNotFound() {
        when(bookingRepository.findByBookingCodeForUpdate(bookingCode)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () ->
                checkInOutService.checkIn(merchantUserId, bookingCode, parkingLotId));
        assertEquals(ErrorCode.BOOKING_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("Check-in fails when status is not PENDING_CHECKIN")
    void testCheckInInvalidStatus() {
        Booking booking = Booking.builder()
                .bookingCode(bookingCode).merchantId(merchantUserId).parkingLotId(parkingLotId)
                .status(BookingStatusEnum.PENDING_PAYMENT)
                .build();
        when(bookingRepository.findByBookingCodeForUpdate(bookingCode)).thenReturn(Optional.of(booking));

        AppException ex = assertThrows(AppException.class, () ->
                checkInOutService.checkIn(merchantUserId, bookingCode, parkingLotId));
        assertEquals(ErrorCode.BOOKING_STATUS_INVALID, ex.getErrorCode());
    }

    @Test
    @DisplayName("Check-in fails when reservation QR code has expired")
    void testCheckInExpired() {
        Booking booking = Booking.builder()
                .bookingCode(bookingCode).merchantId(merchantUserId).parkingLotId(parkingLotId)
                .expectedCheckInTime(LocalDateTime.now().minusMinutes(5)).status(BookingStatusEnum.PENDING_CHECKIN)
                .expiresAt(LocalDateTime.now().minusMinutes(10))
                .build();
        when(bookingRepository.findByBookingCodeForUpdate(bookingCode)).thenReturn(Optional.of(booking));

        AppException ex = assertThrows(AppException.class, () ->
                checkInOutService.checkIn(merchantUserId, bookingCode, parkingLotId));
        assertEquals(ErrorCode.BOOKING_HOLD_EXPIRED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Check-in successfully updates status to PARKING and publishes event")
    void testCheckInSuccess() {
        Booking booking = Booking.builder()
                .bookingCode(bookingCode).merchantId(merchantUserId).parkingLotId(parkingLotId)
                .expectedCheckInTime(LocalDateTime.now().minusMinutes(5)).status(BookingStatusEnum.PENDING_CHECKIN)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        when(bookingRepository.findByBookingCodeForUpdate(bookingCode)).thenReturn(Optional.of(booking));

        checkInOutService.checkIn(merchantUserId, bookingCode, parkingLotId);

        assertEquals(BookingStatusEnum.PARKING, booking.getStatus());
        assertNotNull(booking.getCheckInTime());
        verify(outboxPublisherService, times(1)).publishBookingCheckedIn(booking);
        verify(bookingRepository, times(1)).save(booking);
    }

    @Test
    @DisplayName("Check-out calculates parking duration and requires additional payment when exceeding deposit")
    void testCheckOutAwaitingPayment() {
        // Checked in 3 hours ago: 3 hours * 20,000 = 60,000 VND. Deposit = 50,000 -> amountDue = 10,000
        Booking booking = Booking.builder()
                .id(1L)
                .bookingCode(bookingCode).merchantId(merchantUserId).parkingLotId(parkingLotId)
                .parkingLotId(parkingLotId)
                .vehicleType(VehicleTypeEnum.CONTAINER)
                .status(BookingStatusEnum.PARKING)
                .hourlyRate(new BigDecimal("20000")).checkInTime(LocalDateTime.now().minusMinutes(170))
                .advanceDeposit(new BigDecimal("50000"))
                .build();
        when(bookingRepository.findByBookingCodeForUpdate(bookingCode)).thenReturn(Optional.of(booking));

        CheckOutResponse response = checkInOutService.checkOut(merchantUserId, bookingCode, parkingLotId);

        assertNotNull(response);
        assertEquals(new BigDecimal("60000"), response.getTotalAmount());
        assertEquals(new BigDecimal("10000"), response.getAmountDue());
        assertEquals("AWAITING_FINAL_PAYMENT", response.getBookingStatus());
        assertEquals(BookingStatusEnum.AWAITING_FINAL_PAYMENT, booking.getStatus());
        verify(bookingRepository, times(1)).save(booking);
        verify(outboxPublisherService, never()).publishBookingCompleted(any());
    }

    @Test
    @DisplayName("Check-out completes directly when parking fee is within deposit")
    void testCheckOutCompletedWithinDeposit() {
        // Checked in 1 hour ago: 1 hour * 20,000 = 20,000 VND. Deposit = 50,000 -> amountDue = 0
        Booking booking = Booking.builder()
                .id(1L)
                .bookingCode(bookingCode).merchantId(merchantUserId).parkingLotId(parkingLotId)
                .parkingLotId(parkingLotId)
                .vehicleType(VehicleTypeEnum.CONTAINER)
                .status(BookingStatusEnum.PARKING)
                .hourlyRate(new BigDecimal("20000")).checkInTime(LocalDateTime.now().minusMinutes(50))
                .advanceDeposit(new BigDecimal("50000"))
                .build();
        when(bookingRepository.findByBookingCodeForUpdate(bookingCode)).thenReturn(Optional.of(booking));

        CheckOutResponse response = checkInOutService.checkOut(merchantUserId, bookingCode, parkingLotId);

        assertNotNull(response);
        assertEquals(new BigDecimal("20000"), response.getTotalAmount());
        assertEquals(BigDecimal.ZERO, response.getAmountDue());
        assertEquals("COMPLETED", response.getBookingStatus());
        assertEquals(BookingStatusEnum.COMPLETED, booking.getStatus());
        verify(outboxPublisherService, times(1)).publishBookingCompleted(booking);
        verify(parkingLotClient, times(1)).releaseSlot(eq(parkingLotId), any(SlotHoldRequest.class));
    }
}
