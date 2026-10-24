package com.smartparking.booking.application.service;

import com.smartparking.booking.application.dto.*;
import com.smartparking.booking.application.service.impl.BookingServiceImpl;
import com.smartparking.booking.domain.model.Booking;
import com.smartparking.booking.domain.repository.BookingRepository;
import com.smartparking.booking.infrastructure.client.ParkingLotClient;
import com.smartparking.booking.infrastructure.client.dto.SlotHoldRequest;
import com.smartparking.booking.infrastructure.client.dto.SlotHoldResponse;
import com.smartparking.common.dto.ApiResponse;
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
class BookingServiceImplTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private ParkingLotClient parkingLotClient;

    @Mock
    private OutboxPublisherService outboxPublisherService;

    @InjectMocks
    private BookingServiceImpl bookingService;

    private final Long driverUserId = 500L;
    private final Long merchantUserId = 800L;
    private final Long bookingId = 1L;
    private final Long parkingLotId = 100L;

    @Test
    @DisplayName("Create booking fails when check-in is after check-out")
    void testCreateBookingInvalidTime() {
        CreateBookingRequest req = CreateBookingRequest.builder()
                .parkingLotId(parkingLotId)
                .vehicleId(10L)
                .vehicleType(VehicleTypeEnum.CONTAINER)
                .expectedCheckInTime(LocalDateTime.now().plusHours(3))
                .expectedCheckOutTime(LocalDateTime.now().plusHours(1))
                .build();

        AppException ex = assertThrows(AppException.class, () -> bookingService.createBooking(driverUserId, req));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Giờ vào bãi phải trước giờ ra bãi"));
    }

    @Test
    @DisplayName("Create booking fails when parking lot has no available slots")
    void testCreateBookingSlotFull() {
        CreateBookingRequest req = CreateBookingRequest.builder()
                .parkingLotId(parkingLotId)
                .vehicleId(10L)
                .vehicleType(VehicleTypeEnum.CONTAINER)
                .expectedCheckInTime(LocalDateTime.now().plusHours(1))
                .expectedCheckOutTime(LocalDateTime.now().plusHours(2))
                .build();

        Booking saved = Booking.builder()
                .id(bookingId)
                .bookingCode("BK123")
                .driverId(driverUserId)
                .parkingLotId(parkingLotId)
                .vehicleType(VehicleTypeEnum.CONTAINER)
                .build();
        when(bookingRepository.save(any(Booking.class))).thenReturn(saved);

        SlotHoldResponse fullHold = SlotHoldResponse.builder().success(false).message("Full").build();
        when(parkingLotClient.holdSlot(eq(parkingLotId), any(SlotHoldRequest.class)))
                .thenReturn(ApiResponse.success(fullHold));

        AppException ex = assertThrows(AppException.class, () -> bookingService.createBooking(driverUserId, req));
        assertEquals(ErrorCode.SLOT_NOT_AVAILABLE, ex.getErrorCode());
    }

    @Test
    @DisplayName("Create booking successfully holds slot and creates outbox event")
    void testCreateBookingSuccess() {
        CreateBookingRequest req = CreateBookingRequest.builder()
                .parkingLotId(parkingLotId)
                .vehicleId(10L)
                .vehicleType(VehicleTypeEnum.CONTAINER)
                .expectedCheckInTime(LocalDateTime.now().plusHours(1))
                .expectedCheckOutTime(LocalDateTime.now().plusHours(2))
                .build();

        Booking saved = Booking.builder()
                .id(bookingId)
                .bookingCode("BK999999")
                .driverId(driverUserId)
                .parkingLotId(parkingLotId)
                .vehicleType(VehicleTypeEnum.CONTAINER)
                .status(BookingStatusEnum.PENDING_PAYMENT)
                .advanceDeposit(new BigDecimal("50000"))
                .build();
        when(bookingRepository.save(any(Booking.class))).thenReturn(saved);

        SlotHoldResponse successHold = SlotHoldResponse.builder().success(true).build();
        when(parkingLotClient.holdSlot(eq(parkingLotId), any(SlotHoldRequest.class)))
                .thenReturn(ApiResponse.success(successHold));

        BookingDto result = bookingService.createBooking(driverUserId, req);

        assertNotNull(result);
        assertEquals("BK999999", result.getBookingCode());
        assertEquals(BookingStatusEnum.PENDING_PAYMENT, result.getStatus());
        verify(outboxPublisherService, times(1)).publishBookingCreated(saved);
    }

    @Test
    @DisplayName("Get booking detail throws FORBIDDEN when user has no permission")
    void testGetBookingDetailForbidden() {
        Booking booking = Booking.builder()
                .id(bookingId)
                .driverId(driverUserId)
                .merchantId(merchantUserId)
                .build();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        AppException ex = assertThrows(AppException.class, () -> bookingService.getBookingDetail(9999L, bookingId));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("Cancel booking fails when not owned by driver")
    void testCancelBookingForbidden() {
        Booking booking = Booking.builder()
                .id(bookingId)
                .driverId(driverUserId)
                .build();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        CancelBookingRequest req = CancelBookingRequest.builder().reason("Change plan").build();

        AppException ex = assertThrows(AppException.class, () -> bookingService.cancelBooking(9999L, bookingId, req));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("Cancel booking fails when status is already COMPLETED or PARKING")
    void testCancelBookingInvalidStatus() {
        Booking booking = Booking.builder()
                .id(bookingId)
                .driverId(driverUserId)
                .status(BookingStatusEnum.PARKING)
                .build();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        CancelBookingRequest req = CancelBookingRequest.builder().reason("Change plan").build();

        AppException ex = assertThrows(AppException.class, () -> bookingService.cancelBooking(driverUserId, bookingId, req));
        assertEquals(ErrorCode.BOOKING_STATUS_INVALID, ex.getErrorCode());
    }

    @Test
    @DisplayName("Cancel booking in PENDING_CHECKIN triggers refund request")
    void testCancelBookingWithRefund() {
        Booking booking = Booking.builder()
                .id(bookingId)
                .bookingCode("BK1234")
                .driverId(driverUserId)
                .parkingLotId(parkingLotId)
                .vehicleType(VehicleTypeEnum.SEDAN_SUV)
                .status(BookingStatusEnum.PENDING_CHECKIN)
                .advanceDeposit(new BigDecimal("50000"))
                .build();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        CancelBookingRequest req = CancelBookingRequest.builder().reason("Khách hủy chuyến").build();

        CancelBookingResponse response = bookingService.cancelBooking(driverUserId, bookingId, req);

        assertNotNull(response);
        assertEquals("CANCELLED", response.getStatus());
        assertTrue(response.isRefundRequested());
        assertEquals(new BigDecimal("50000"), response.getRefundAmount());
        assertEquals(BookingStatusEnum.CANCELLED, booking.getStatus());
        verify(outboxPublisherService, times(1)).publishBookingCancelled(booking, "Khách hủy chuyến");
        verify(parkingLotClient, times(1)).releaseSlot(eq(parkingLotId), any(SlotHoldRequest.class));
    }
}
