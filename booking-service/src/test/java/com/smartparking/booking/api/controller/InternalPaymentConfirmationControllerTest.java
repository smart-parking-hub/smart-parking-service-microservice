package com.smartparking.booking.api.controller;

import com.smartparking.booking.domain.model.Booking;
import com.smartparking.booking.domain.repository.BookingRepository;
import com.smartparking.common.enums.BookingStatusEnum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.TimeZone;

import static com.smartparking.booking.domain.BookingRefundPolicy.BUSINESS_ZONE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InternalPaymentConfirmationControllerTest {
    @Mock BookingRepository repository;
    @InjectMocks InternalPaymentConfirmationController controller;
    private TimeZone originalZone;

    @BeforeEach
    void useUtcHost() {
        originalZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        ReflectionTestUtils.setField(controller, "serviceToken", "test-internal-token");
    }

    @AfterEach
    void restoreHostZone() {
        TimeZone.setDefault(originalZone);
    }

    @ParameterizedTest
    @ValueSource(ints = {-5, -1, 0})
    void rejectsExpiredDepositOnUtcHost(int minutesUntilExpiry) {
        LocalDateTime expiry = LocalDateTime.now(BUSINESS_ZONE).plusMinutes(minutesUntilExpiry);
        Booking booking = pendingDeposit(expiry);

        var response = controller.confirm(10L, "test-internal-token", deposit());

        assertFalse(response.getData());
        assertEquals(BookingStatusEnum.PENDING_PAYMENT, booking.getStatus());
        assertEquals(expiry, booking.getExpiresAt());
        verify(repository, never()).save(any());
    }

    @Test
    void acceptsUnexpiredDepositOnUtcHost() {
        Booking booking = pendingDeposit(LocalDateTime.now(BUSINESS_ZONE).plusMinutes(10));

        var response = controller.confirm(10L, "test-internal-token", deposit());

        assertTrue(response.getData());
        assertEquals(BookingStatusEnum.PENDING_CHECKIN, booking.getStatus());
        assertEquals(booking.getExpectedCheckInTime().plusMinutes(60), booking.getExpiresAt());
        verify(repository).save(booking);
    }

    @Test
    void rejectsMissingDepositDeadline() {
        Booking booking = pendingDeposit(null);

        assertFalse(controller.confirm(10L, "test-internal-token", deposit()).getData());

        assertEquals(BookingStatusEnum.PENDING_PAYMENT, booking.getStatus());
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsDepositAtExactExpiry() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 15, 14, 15);
        Booking booking = pendingDeposit(now);
        try (var time = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            time.when(() -> LocalDateTime.now(BUSINESS_ZONE)).thenReturn(now);

            assertFalse(controller.confirm(10L, "test-internal-token", deposit()).getData());

            assertEquals(BookingStatusEnum.PENDING_PAYMENT, booking.getStatus());
            assertEquals(now, booking.getExpiresAt());
            verify(repository, never()).save(any());
        }
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatusEnum.class, names = {"PENDING_CHECKIN", "PARKING", "COMPLETED"})
    void repeatedConfirmationKeepsStatusAndDeadline(BookingStatusEnum status) {
        LocalDateTime deadline = LocalDateTime.now(BUSINESS_ZONE).minusMinutes(5);
        Booking booking = pendingDeposit(deadline);
        booking.setStatus(status);

        assertTrue(controller.confirm(10L, "test-internal-token", deposit()).getData());

        assertEquals(status, booking.getStatus());
        assertEquals(deadline, booking.getExpiresAt());
        verify(repository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatusEnum.class, names = {"CANCELLED", "EXPIRED"})
    void depositCannotReviveClosedBooking(BookingStatusEnum status) {
        LocalDateTime deadline = LocalDateTime.now(BUSINESS_ZONE).plusMinutes(10);
        Booking booking = pendingDeposit(deadline);
        booking.setStatus(status);

        assertFalse(controller.confirm(10L, "test-internal-token", deposit()).getData());

        assertEquals(status, booking.getStatus());
        assertEquals(deadline, booking.getExpiresAt());
        verify(repository, never()).save(any());
    }

    private Booking pendingDeposit(LocalDateTime expiry) {
        Booking booking = Booking.builder().id(10L).driverId(20L).merchantId(30L)
                .advanceDeposit(new BigDecimal("50000")).status(BookingStatusEnum.PENDING_PAYMENT)
                .expiresAt(expiry).expectedCheckInTime(LocalDateTime.now(BUSINESS_ZONE).plusHours(1)).build();
        when(repository.findByIdForUpdate(10L)).thenReturn(Optional.of(booking));
        return booking;
    }

    private InternalPaymentConfirmationController.Confirmation deposit() {
        return new InternalPaymentConfirmationController.Confirmation(20L, 30L, new BigDecimal("50000"), "DEPOSIT");
    }
}
