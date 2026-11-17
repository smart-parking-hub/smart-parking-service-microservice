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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static com.smartparking.booking.domain.BookingRefundPolicy.BUSINESS_ZONE;

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

    @ParameterizedTest
    @CsvSource({"31000", "40000"})
    void rejectsCashAmountDifferentFromOutstandingBalance(String amount) {
        Booking booking = cashBooking();
        when(bookingRepository.findByBookingCodeForUpdate(bookingCode)).thenReturn(Optional.of(booking));
        assertThrows(AppException.class, () -> checkInOutService.confirmCashPayment(
                merchantUserId, bookingCode, parkingLotId, new BigDecimal(amount)));
        verifyNoInteractions(parkingLotClient, queueService, outboxPublisherService);
        assertEquals(BookingStatusEnum.AWAITING_FINAL_PAYMENT, booking.getStatus());
    }

    @Test void missingCashAmountCannotCompleteBooking() {
        Booking booking = cashBooking();
        when(bookingRepository.findByBookingCodeForUpdate(bookingCode)).thenReturn(Optional.of(booking));
        assertThrows(AppException.class, () -> checkInOutService.confirmCashPayment(merchantUserId, bookingCode, parkingLotId, null));
        verifyNoInteractions(parkingLotClient, queueService, outboxPublisherService);
    }

    @Test void cashRetryAndCompletedRescanDoNotRepeatReleaseOrOutbox() {
        Booking booking = cashBooking();
        when(bookingRepository.findByBookingCodeForUpdate(bookingCode)).thenReturn(Optional.of(booking));
        checkInOutService.confirmCashPayment(merchantUserId, bookingCode, parkingLotId, new BigDecimal("32000"));
        checkInOutService.confirmCashPayment(merchantUserId, bookingCode, parkingLotId, new BigDecimal("32000"));
        var result = checkInOutService.checkOut(merchantUserId, bookingCode, parkingLotId);
        assertEquals(BigDecimal.ZERO, result.getAmountDue());
        assertEquals("CASH", result.getFinalPaymentMethod());
        assertEquals(1L, result.getBookingId());
        verify(parkingLotClient).releaseSlot(eq(parkingLotId), any());
        verify(outboxPublisherService).publishBookingCompleted(booking);
    }

    @Test void recordedPayOsCannotBeConfirmedAsCash() {
        Booking booking = cashBooking(); booking.setFinalPaymentMethod("PAYOS");
        when(bookingRepository.findByBookingCodeForUpdate(bookingCode)).thenReturn(Optional.of(booking));
        assertThrows(AppException.class, () -> checkInOutService.confirmCashPayment(merchantUserId, bookingCode, parkingLotId, new BigDecimal("32000")));
        verifyNoInteractions(parkingLotClient, queueService, outboxPublisherService);
    }

    private Booking cashBooking() {
        return Booking.builder().id(1L).bookingCode(bookingCode).merchantId(merchantUserId).parkingLotId(parkingLotId)
                .vehicleType(VehicleTypeEnum.CONTAINER).status(BookingStatusEnum.AWAITING_FINAL_PAYMENT)
                .totalAmount(new BigDecimal("40000")).advanceDeposit(new BigDecimal("8000"))
                .checkInTime(LocalDateTime.now(BUSINESS_ZONE).minusHours(2)).checkOutTime(LocalDateTime.now(BUSINESS_ZONE)).build();
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0, 40000", "0, 10, 40000", "30, 40, 40000", "0, 60, 40000", "0, 120, 40000",
            "90, 125, 55000", "90, 155, 70000", "-30, 120, 60000"
    })
    void checkoutCollectsFullReservedFeeEvenForShortStay(int entryMinutes, int exitMinutes, String expectedTotal) {
        LocalDateTime reservedStart = LocalDateTime.of(2026, 11, 17, 8, 0);
        LocalDateTime exit = reservedStart.plusMinutes(exitMinutes);
        Booking booking = Booking.builder().id(1L).bookingCode(bookingCode)
                .merchantId(merchantUserId).parkingLotId(parkingLotId)
                .status(BookingStatusEnum.PARKING)
                .expectedCheckInTime(reservedStart).expectedCheckOutTime(reservedStart.plusHours(2))
                .checkInTime(reservedStart.plusMinutes(entryMinutes))
                .hourlyRate(new BigDecimal("20000")).overtimePenaltyMultiplier(new BigDecimal("1.5"))
                .totalAmount(new BigDecimal("40000")).advanceDeposit(new BigDecimal("8000")).build();
        when(bookingRepository.findByBookingCodeForUpdate(bookingCode)).thenReturn(Optional.of(booking));

        try (var clock = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            clock.when(() -> LocalDateTime.now(BUSINESS_ZONE)).thenReturn(exit);
            CheckOutResponse response = checkInOutService.checkOut(merchantUserId, bookingCode, parkingLotId);

            assertEquals(new BigDecimal(expectedTotal), response.getTotalAmount());
            assertEquals(new BigDecimal(expectedTotal).subtract(new BigDecimal("8000")), response.getAmountDue());
            assertEquals(exitMinutes - entryMinutes, response.getParkingMinutes());
            assertEquals(exit, booking.getCheckOutTime());
            assertEquals(response.getTotalAmount(), booking.getTotalAmount());
            assertEquals(BookingStatusEnum.AWAITING_FINAL_PAYMENT, booking.getStatus());
            verify(parkingLotClient, never()).releaseSlot(any(), any());
        }
    }

    @Test
    void earlyCheckoutIncludesReservedPeakHoursAndSubtractsDepositOnce() {
        LocalDateTime reservedStart = LocalDateTime.of(2026, 11, 17, 8, 0);
        LocalDateTime exit = reservedStart.plusMinutes(10);
        Booking booking = Booking.builder().id(1L).bookingCode(bookingCode)
                .merchantId(merchantUserId).parkingLotId(parkingLotId)
                .status(BookingStatusEnum.PARKING).checkInTime(reservedStart)
                .expectedCheckInTime(reservedStart).expectedCheckOutTime(reservedStart.plusHours(2))
                .hourlyRate(new BigDecimal("20000")).surgeMultiplier(new BigDecimal("2"))
                .surgeStartTime(LocalTime.of(9, 0)).surgeEndTime(LocalTime.of(10, 0))
                .totalAmount(new BigDecimal("60000")).advanceDeposit(new BigDecimal("12000")).build();
        when(bookingRepository.findByBookingCodeForUpdate(bookingCode)).thenReturn(Optional.of(booking));

        try (var clock = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            clock.when(() -> LocalDateTime.now(BUSINESS_ZONE)).thenReturn(exit);
            CheckOutResponse response = checkInOutService.checkOut(merchantUserId, bookingCode, parkingLotId);

            assertEquals(new BigDecimal("60000"), response.getTotalAmount());
            assertEquals(new BigDecimal("48000"), response.getAmountDue());
            assertEquals(10, response.getParkingMinutes());
        }
    }

    @Test
    void earlyCheckoutCompletesWhenFullReservedFeeIsAlreadyCovered() {
        LocalDateTime reservedStart = LocalDateTime.of(2026, 11, 17, 8, 0);
        LocalDateTime exit = reservedStart.plusMinutes(10);
        Booking booking = Booking.builder().id(1L).bookingCode(bookingCode)
                .merchantId(merchantUserId).parkingLotId(parkingLotId).vehicleType(VehicleTypeEnum.CONTAINER)
                .status(BookingStatusEnum.PARKING).checkInTime(reservedStart)
                .expectedCheckInTime(reservedStart).expectedCheckOutTime(reservedStart.plusHours(2))
                .hourlyRate(new BigDecimal("20000")).advanceDeposit(new BigDecimal("40000")).build();
        when(bookingRepository.findByBookingCodeForUpdate(bookingCode)).thenReturn(Optional.of(booking));

        try (var clock = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            clock.when(() -> LocalDateTime.now(BUSINESS_ZONE)).thenReturn(exit);
            CheckOutResponse response = checkInOutService.checkOut(merchantUserId, bookingCode, parkingLotId);

            assertEquals(new BigDecimal("40000"), response.getTotalAmount());
            assertEquals(BigDecimal.ZERO, response.getAmountDue());
            assertEquals(BookingStatusEnum.COMPLETED, booking.getStatus());
            verify(parkingLotClient).releaseSlot(eq(parkingLotId), any(SlotHoldRequest.class));
            verify(queueService).promoteNextInQueue(parkingLotId, VehicleTypeEnum.CONTAINER);
            verify(outboxPublisherService).publishBookingCompleted(booking);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "0, 40000", "1, 55000", "300, 55000", "1800, 55000",
            "1801, 70000", "2100, 70000", "3600, 70000", "3601, 85000"
    })
    void checkoutRoundsOvertimeUpToThirtyMinuteBlocks(long overtimeSeconds, String expectedTotal) {
        LocalDateTime expectedEnd = LocalDateTime.of(2026, 11, 17, 10, 0);
        LocalDateTime exit = expectedEnd.plusSeconds(overtimeSeconds);
        Booking booking = Booking.builder().id(1L).bookingCode(bookingCode)
                .merchantId(merchantUserId).parkingLotId(parkingLotId)
                .status(BookingStatusEnum.PARKING)
                .expectedCheckInTime(expectedEnd.minusHours(2))
                .checkInTime(expectedEnd.minusMinutes(90)).expectedCheckOutTime(expectedEnd)
                .hourlyRate(new BigDecimal("20000")).overtimePenaltyMultiplier(new BigDecimal("1.5"))
                .advanceDeposit(new BigDecimal("8000")).build();
        when(bookingRepository.findByBookingCodeForUpdate(bookingCode)).thenReturn(Optional.of(booking));

        try (var clock = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            clock.when(() -> LocalDateTime.now(BUSINESS_ZONE)).thenReturn(exit);
            CheckOutResponse response = checkInOutService.checkOut(merchantUserId, bookingCode, parkingLotId);

            assertEquals(new BigDecimal(expectedTotal), response.getTotalAmount());
            assertEquals(new BigDecimal(expectedTotal).subtract(new BigDecimal("8000")), response.getAmountDue());
            assertEquals(90 + overtimeSeconds / 60, response.getParkingMinutes());
            assertEquals(BookingStatusEnum.AWAITING_FINAL_PAYMENT, booking.getStatus());
            verify(bookingRepository).save(booking);
        }
    }

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
    @DisplayName("Check-out is idempotent when already in AWAITING_FINAL_PAYMENT")
    void testCheckOutIdempotentWhenAlreadyAwaitingFinalPayment() {
        Booking booking = Booking.builder()
                .id(1L)
                .bookingCode(bookingCode)
                .merchantId(merchantUserId)
                .parkingLotId(parkingLotId)
                .status(BookingStatusEnum.AWAITING_FINAL_PAYMENT)
                .checkInTime(LocalDateTime.now().minusMinutes(120))
                .checkOutTime(LocalDateTime.now())
                .totalAmount(new BigDecimal("40000"))
                .advanceDeposit(new BigDecimal("8000"))
                .build();
        when(bookingRepository.findByBookingCodeForUpdate(bookingCode)).thenReturn(Optional.of(booking));

        CheckOutResponse response = checkInOutService.checkOut(merchantUserId, bookingCode, parkingLotId);

        assertNotNull(response);
        assertEquals(new BigDecimal("40000"), response.getTotalAmount());
        assertEquals(new BigDecimal("8000"), response.getAdvanceDeposit());
        assertEquals(new BigDecimal("32000"), response.getAmountDue());
        assertEquals("AWAITING_FINAL_PAYMENT", response.getBookingStatus());
        verify(parkingLotClient, never()).releaseSlot(any(), any());
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

    @Test
    @DisplayName("confirmCashPayment succeeds, updates status to COMPLETED, releases slot, promotes queue and publishes outbox event")
    void testConfirmCashPayment_Success() {
        // Arrange
        Booking booking = Booking.builder()
                .id(1L)
                .bookingCode(bookingCode)
                .merchantId(merchantUserId)
                .parkingLotId(parkingLotId)
                .vehicleType(VehicleTypeEnum.CONTAINER)
                .status(BookingStatusEnum.AWAITING_FINAL_PAYMENT)
                .totalAmount(new BigDecimal("60000"))
                .advanceDeposit(new BigDecimal("50000"))
                .build();
        when(bookingRepository.findByBookingCodeForUpdate(bookingCode)).thenReturn(Optional.of(booking));

        // Act
        checkInOutService.confirmCashPayment(merchantUserId, bookingCode, parkingLotId, new BigDecimal("10000"));

        // Assert
        assertEquals(BookingStatusEnum.COMPLETED, booking.getStatus());
        verify(bookingRepository, times(1)).save(booking);
        verify(parkingLotClient, times(1)).releaseSlot(eq(parkingLotId), any(SlotHoldRequest.class));
        verify(queueService, times(1)).promoteNextInQueue(parkingLotId, VehicleTypeEnum.CONTAINER);
        verify(outboxPublisherService, times(1)).publishBookingCompleted(booking);
    }

    @Test
    @DisplayName("confirmCashPayment throws BOOKING_NOT_FOUND when booking does not exist")
    void testConfirmCashPayment_BookingNotFound() {
        // Arrange
        when(bookingRepository.findByBookingCodeForUpdate(bookingCode)).thenReturn(Optional.empty());

        // Act & Assert
        AppException ex = assertThrows(AppException.class, () ->
                checkInOutService.confirmCashPayment(merchantUserId, bookingCode, parkingLotId, null));

        assertEquals(ErrorCode.BOOKING_NOT_FOUND, ex.getErrorCode());
        assertEquals("Booking không tồn tại!", ex.getMessage());
        verify(bookingRepository, never()).save(any());
        verify(outboxPublisherService, never()).publishBookingCompleted(any());
    }

    @Test
    @DisplayName("confirmCashPayment throws BOOKING_STATUS_INVALID when booking is not AWAITING_FINAL_PAYMENT")
    void testConfirmCashPayment_InvalidStatus() {
        // Arrange
        Booking booking = Booking.builder()
                .id(1L)
                .bookingCode(bookingCode)
                .merchantId(merchantUserId)
                .parkingLotId(parkingLotId)
                .status(BookingStatusEnum.PARKING)
                .build();
        when(bookingRepository.findByBookingCodeForUpdate(bookingCode)).thenReturn(Optional.of(booking));

        // Act & Assert
        AppException ex = assertThrows(AppException.class, () ->
                checkInOutService.confirmCashPayment(merchantUserId, bookingCode, parkingLotId, null));

        assertEquals(ErrorCode.BOOKING_STATUS_INVALID, ex.getErrorCode());
        assertEquals("Booking không ở trạng thái chờ thanh toán phụ phí!", ex.getMessage());
        verify(bookingRepository, never()).save(any());
        verify(outboxPublisherService, never()).publishBookingCompleted(any());
    }

    @Test
    @DisplayName("confirmCashPayment throws BAD_REQUEST when parkingLotId does not match booking")
    void testConfirmCashPayment_MismatchedParkingLot() {
        // Arrange
        Long differentLotId = 999L;
        Booking booking = Booking.builder()
                .id(1L)
                .bookingCode(bookingCode)
                .merchantId(merchantUserId)
                .parkingLotId(differentLotId)
                .status(BookingStatusEnum.AWAITING_FINAL_PAYMENT)
                .build();
        when(bookingRepository.findByBookingCodeForUpdate(bookingCode)).thenReturn(Optional.of(booking));

        // Act & Assert
        AppException ex = assertThrows(AppException.class, () ->
                checkInOutService.confirmCashPayment(merchantUserId, bookingCode, parkingLotId, null));

        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        assertEquals("Mã đặt chỗ này thuộc về bãi đỗ xe khác!", ex.getMessage());
        verify(bookingRepository, never()).save(any());
        verify(outboxPublisherService, never()).publishBookingCompleted(any());
    }

    @Test
    @DisplayName("confirmCashPayment throws FORBIDDEN when merchantUserId does not match booking owner")
    void testConfirmCashPayment_ForbiddenMerchant() {
        // Arrange
        Long differentMerchantId = 777L;
        Booking booking = Booking.builder()
                .id(1L)
                .bookingCode(bookingCode)
                .merchantId(differentMerchantId)
                .parkingLotId(parkingLotId)
                .status(BookingStatusEnum.AWAITING_FINAL_PAYMENT)
                .build();
        when(bookingRepository.findByBookingCodeForUpdate(bookingCode)).thenReturn(Optional.of(booking));

        // Act & Assert
        AppException ex = assertThrows(AppException.class, () ->
                checkInOutService.confirmCashPayment(merchantUserId, bookingCode, parkingLotId, null));

        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        assertEquals("Bạn không có quyền xác nhận thu tiền cho bãi xe này!", ex.getMessage());
        verify(bookingRepository, never()).save(any());
        verify(outboxPublisherService, never()).publishBookingCompleted(any());
    }

    @Test
    @DisplayName("confirmCashPayment throws INTERNAL_SERVER_ERROR when slot release fails")
    void testConfirmCashPayment_ReleaseSlotFails() {
        // Arrange
        Booking booking = Booking.builder()
                .id(1L)
                .bookingCode(bookingCode)
                .merchantId(merchantUserId)
                .parkingLotId(parkingLotId)
                .vehicleType(VehicleTypeEnum.CONTAINER)
                .totalAmount(new BigDecimal("40000")).advanceDeposit(new BigDecimal("8000"))
                .status(BookingStatusEnum.AWAITING_FINAL_PAYMENT)
                .build();
        when(bookingRepository.findByBookingCodeForUpdate(bookingCode)).thenReturn(Optional.of(booking));
        doThrow(new RuntimeException("Feign client connection failed"))
                .when(parkingLotClient).releaseSlot(eq(parkingLotId), any(SlotHoldRequest.class));

        // Act & Assert
        AppException ex = assertThrows(AppException.class, () ->
                checkInOutService.confirmCashPayment(merchantUserId, bookingCode, parkingLotId, new BigDecimal("32000")));

        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR, ex.getErrorCode());
        assertEquals("Chưa giải phóng được chỗ đỗ. Vui lòng thử lại.", ex.getMessage());
        verify(outboxPublisherService, never()).publishBookingCompleted(any());
    }
}
