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

    @Test
    void paymentDeadlineUsesVietnamTimeOnUtcHost() {
        java.util.TimeZone original = java.util.TimeZone.getDefault();
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
            LocalDateTime now = LocalDateTime.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
            var request = CreateBookingRequest.builder().parkingLotId(100L).vehicleId(10L)
                    .vehicleType(VehicleTypeEnum.CONTAINER).expectedCheckInTime(now.plusHours(1))
                    .expectedCheckOutTime(now.plusHours(3)).build();
            when(bookingRepository.saveAndFlush(any())).thenAnswer(inv -> { Booking b = inv.getArgument(0); b.setId(1L); return b; });
            when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(parkingLotClient.holdSlot(eq(100L), any())).thenReturn(ApiResponse.success(
                    SlotHoldResponse.builder().success(true).merchantId(800L).baseHourlyPrice(new BigDecimal("20000")).build()));
            var result = bookingService.createBooking(500L, request);
            assertFalse(result.getExpiresAt().isBefore(now.plusMinutes(15)));
            assertTrue(result.getExpiresAt().isBefore(now.plusMinutes(16)));
            assertEquals(0, new BigDecimal("8000").compareTo(result.getAdvanceDeposit()));
        } finally {
            java.util.TimeZone.setDefault(original);
        }
    }

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private ParkingLotClient parkingLotClient;

    @Mock
    private OutboxPublisherService outboxPublisherService;

    @Mock private QueueService queueService;

    @Mock
    private com.smartparking.booking.infrastructure.client.OwnedVehicleClient ownedVehicleClient;

    @InjectMocks
    private BookingServiceImpl bookingService;

    @org.junit.jupiter.api.BeforeEach
    void verifiedVehicle() {
        lenient().when(ownedVehicleClient.require(any(Long.class))).thenAnswer(inv -> new com.smartparking.booking.infrastructure.client.OwnedVehicleClient.Vehicle(inv.getArgument(0), "51C-TEST", VehicleTypeEnum.CONTAINER, "ACTIVE"));
    }
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
        assertTrue(ex.getMessage().contains("Giờ vào phải ở tương lai"));
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
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(inv -> { Booking b = inv.getArgument(0); b.setId(bookingId); b.setBookingCode(saved.getBookingCode()); return b; });
        lenient().when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        SlotHoldResponse fullHold = SlotHoldResponse.builder().merchantId(merchantUserId).baseHourlyPrice(new BigDecimal("25000")).surgeMultiplier(BigDecimal.ONE).overtimePenaltyMultiplier(new BigDecimal("1.5")).success(false).message("Full").build();
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
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(inv -> { Booking b = inv.getArgument(0); b.setId(bookingId); b.setBookingCode(saved.getBookingCode()); return b; });
        lenient().when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        SlotHoldResponse successHold = SlotHoldResponse.builder().merchantId(merchantUserId).baseHourlyPrice(new BigDecimal("25000")).surgeMultiplier(BigDecimal.ONE).overtimePenaltyMultiplier(new BigDecimal("1.5")).success(true).build();
        when(parkingLotClient.holdSlot(eq(parkingLotId), any(SlotHoldRequest.class)))
                .thenReturn(ApiResponse.success(successHold));

        BookingDto result = bookingService.createBooking(driverUserId, req);

        assertNotNull(result);
        assertEquals("BK999999", result.getBookingCode());
        assertEquals(BookingStatusEnum.PENDING_PAYMENT, result.getStatus());
        verify(outboxPublisherService, times(1)).publishBookingCreated(argThat(b -> b.getId().equals(bookingId) && b.getMerchantId().equals(merchantUserId) && b.getAdvanceDeposit().signum() > 0));
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
        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));

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
        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));

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
        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));

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

    @Test
    @DisplayName("getMerchantBookingDetail returns booking details when merchant owns the parking lot")
    void testGetMerchantBookingDetailSuccess() {
        // Arrange
        Booking booking = Booking.builder()
                .id(bookingId)
                .bookingCode("BK-MERCHANT-01")
                .merchantId(merchantUserId)
                .parkingLotId(parkingLotId)
                .parkingLotName("Bãi đỗ số 1")
                .vehiclePlate("51C-12345")
                .vehicleType(VehicleTypeEnum.CONTAINER)
                .status(BookingStatusEnum.PENDING_CHECKIN)
                .advanceDeposit(new BigDecimal("100000"))
                .build();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        // Act
        BookingDto result = bookingService.getMerchantBookingDetail(merchantUserId, bookingId);

        // Assert
        assertNotNull(result);
        assertEquals(bookingId, result.getId());
        assertEquals("BK-MERCHANT-01", result.getBookingCode());
        assertEquals("51C-12345", result.getVehiclePlate());
        assertEquals(merchantUserId, result.getMerchantId());
        verify(bookingRepository, times(1)).findById(bookingId);
    }

    @Test
    @DisplayName("getMerchantBookingDetail throws FORBIDDEN when merchant does not own the booking")
    void testGetMerchantBookingDetailForbidden() {
        // Arrange
        Long otherMerchantId = 9999L;
        Booking booking = Booking.builder()
                .id(bookingId)
                .bookingCode("BK-OTHER-01")
                .merchantId(merchantUserId)
                .build();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        // Act & Assert
        AppException ex = assertThrows(AppException.class,
                () -> bookingService.getMerchantBookingDetail(otherMerchantId, bookingId));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Bạn không có quyền truy cập thông tin booking này"));
    }

    @Test
    @DisplayName("getMerchantBookingDetail throws BOOKING_NOT_FOUND when booking does not exist")
    void testGetMerchantBookingDetailNotFound() {
        // Arrange
        Long nonExistentId = 8888L;
        when(bookingRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        // Act & Assert
        AppException ex = assertThrows(AppException.class,
                () -> bookingService.getMerchantBookingDetail(merchantUserId, nonExistentId));
        assertEquals(ErrorCode.BOOKING_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("AC5 & AC6: Create booking snapshots physicalSlotId, physicalSlotCode, and layoutPublicationId")
    void testCreateBookingWithPhysicalSlotSuccess() {
        LocalDateTime checkIn = LocalDateTime.now().plusHours(2);
        LocalDateTime checkOut = checkIn.plusHours(2);

        CreateBookingRequest req = CreateBookingRequest.builder()
                .requestId("unique-1")
                .parkingLotId(parkingLotId)
                .vehicleId(10L)
                .vehicleType(VehicleTypeEnum.CONTAINER)
                .expectedCheckInTime(checkIn)
                .expectedCheckOutTime(checkOut)
                .physicalSlotId("ps-501")
                .build();

        Booking savedInit = Booking.builder()
                .id(bookingId)
                .bookingCode("BK123456")
                .driverId(driverUserId)
                .vehicleId(10L)
                .parkingLotId(parkingLotId)
                .vehicleType(VehicleTypeEnum.CONTAINER)
                .status(BookingStatusEnum.PENDING_PAYMENT)
                .build();

        when(bookingRepository.saveAndFlush(any(Booking.class))).thenReturn(savedInit);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        SlotHoldResponse holdResp = SlotHoldResponse.builder()
                .success(true)
                .merchantId(merchantUserId)
                .parkingLotName("Central Lot")
                .parkingLotAddress("123 Street")
                .baseHourlyPrice(new BigDecimal("20000"))
                .physicalSlotId("ps-501")
                .physicalSlotCode("A-01")
                .layoutPublicationId("pub-101")
                .build();
        when(parkingLotClient.holdSlot(eq(parkingLotId), any(SlotHoldRequest.class)))
                .thenReturn(ApiResponse.success(holdResp));

        BookingDto result = bookingService.createBooking(driverUserId, req);

        assertNotNull(result);
        assertEquals("ps-501", result.getPhysicalSlotId());
        assertEquals("A-01", result.getPhysicalSlotCode());
        assertEquals("pub-101", result.getLayoutPublicationId());
        verify(parkingLotClient).holdSlot(eq(parkingLotId), argThat(h -> "ps-501".equals(h.getPhysicalSlotId())));
    }

    @Test
    @DisplayName("AC6: Retry createBooking with same requestKey and same physicalSlotId returns existing booking")
    void testCreateBookingIdempotentSamePhysicalSlot() {
        CreateBookingRequest req = CreateBookingRequest.builder()
                .requestId("idem-1")
                .parkingLotId(parkingLotId)
                .vehicleId(10L)
                .vehicleType(VehicleTypeEnum.CONTAINER)
                .physicalSlotId("ps-501")
                .build();

        Booking existing = Booking.builder()
                .id(bookingId)
                .bookingCode("BK-EXISTING")
                .requestKey(driverUserId + ":idem-1")
                .parkingLotId(parkingLotId)
                .vehicleId(10L)
                .vehicleType(VehicleTypeEnum.CONTAINER)
                .physicalSlotId("ps-501")
                .status(BookingStatusEnum.PENDING_PAYMENT)
                .build();

        when(bookingRepository.findByRequestKey(driverUserId + ":idem-1")).thenReturn(Optional.of(existing));

        BookingDto result = bookingService.createBooking(driverUserId, req);

        assertNotNull(result);
        assertEquals(bookingId, result.getId());
        assertEquals("ps-501", result.getPhysicalSlotId());
        verify(parkingLotClient, never()).holdSlot(any(), any());
    }

    @Test
    @DisplayName("AC6: Retry createBooking with same requestKey but altered physicalSlotId throws CONFLICT (409)")
    void testCreateBookingConflictAlteredPhysicalSlot() {
        CreateBookingRequest req = CreateBookingRequest.builder()
                .requestId("idem-1")
                .parkingLotId(parkingLotId)
                .vehicleId(10L)
                .vehicleType(VehicleTypeEnum.CONTAINER)
                .physicalSlotId("ps-502") // Changed from ps-501
                .build();

        Booking existing = Booking.builder()
                .id(bookingId)
                .bookingCode("BK-EXISTING")
                .requestKey(driverUserId + ":idem-1")
                .parkingLotId(parkingLotId)
                .vehicleId(10L)
                .vehicleType(VehicleTypeEnum.CONTAINER)
                .physicalSlotId("ps-501")
                .build();

        when(bookingRepository.findByRequestKey(driverUserId + ":idem-1")).thenReturn(Optional.of(existing));

        AppException ex = assertThrows(AppException.class, () ->
                bookingService.createBooking(driverUserId, req));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
        verify(parkingLotClient, never()).holdSlot(any(), any());
    }
}
