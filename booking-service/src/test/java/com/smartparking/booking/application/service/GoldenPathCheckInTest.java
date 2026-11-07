package com.smartparking.booking.application.service;

import com.smartparking.booking.application.service.impl.CheckInOutServiceImpl;
import com.smartparking.booking.domain.model.Booking;
import com.smartparking.booking.domain.repository.BookingRepository;
import com.smartparking.booking.infrastructure.client.ParkingLotClient;
import com.smartparking.common.enums.BookingStatusEnum;
import com.smartparking.common.exception.AppException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoldenPathCheckInTest {
    @Mock BookingRepository repository;
    @Mock ParkingLotClient lots;
    @Mock OutboxPublisherService events;
    @Mock QueueService queue;
    @InjectMocks CheckInOutServiceImpl service;
    Booking booking() {
        return Booking.builder().id(1L).bookingCode("BK-TEST").merchantId(7L).parkingLotId(8L)
            .status(BookingStatusEnum.PENDING_CHECKIN).expectedCheckInTime(LocalDateTime.now().plusMinutes(40))
            .expectedCheckOutTime(LocalDateTime.now().plusHours(2)).expiresAt(LocalDateTime.now().plusHours(2)).build();
    }
    @Test void cannotConfigureEarlyArrivalBeyondThirtyMinutes() {
        ReflectionTestUtils.setField(service, "checkInEarlyMinutes", 60);
        Booking booking = booking();
        when(repository.findByBookingCodeForUpdate("BK-TEST")).thenReturn(Optional.of(booking));
        assertThrows(AppException.class, () -> service.checkIn(7L, "BK-TEST", 8L));
        verify(events, never()).publishBookingCheckedIn(any());
    }
    @Test void missingMerchantNeverBypassesOwnership() {
        Booking booking = booking(); booking.setMerchantId(null);
        booking.setExpectedCheckInTime(LocalDateTime.now().minusMinutes(1));
        when(repository.findByBookingCodeForUpdate("BK-TEST")).thenReturn(Optional.of(booking));
        assertThrows(AppException.class, () -> service.checkIn(7L, "BK-TEST", 8L));
    }
    @Test void overtimeCanProceedToSettlement() {
        Booking booking = booking(); booking.setStatus(BookingStatusEnum.OVERTIME);
        booking.setCheckInTime(LocalDateTime.now().minusHours(2));
        booking.setExpectedCheckOutTime(LocalDateTime.now().minusHours(1));
        booking.setHourlyRate(new BigDecimal("20000")); booking.setAdvanceDeposit(BigDecimal.ZERO);
        when(repository.findByBookingCodeForUpdate("BK-TEST")).thenReturn(Optional.of(booking));
        assertEquals(BookingStatusEnum.AWAITING_FINAL_PAYMENT.name(), service.checkOut(7L,"BK-TEST",8L).getBookingStatus());
    }
    @Test void overtimeMultiplierOnlyAppliesAfterExpectedDeparture() {
        Booking b = booking(); b.setStatus(BookingStatusEnum.OVERTIME);
        b.setCheckInTime(LocalDateTime.now().minusMinutes(90));
        b.setExpectedCheckOutTime(LocalDateTime.now().minusMinutes(20));
        b.setHourlyRate(new BigDecimal("20000")); b.setAdvanceDeposit(new BigDecimal("10000"));
        b.setOvertimePenaltyMultiplier(new BigDecimal("2"));
        when(repository.findByBookingCodeForUpdate("BK-TEST")).thenReturn(Optional.of(b));
        var result = service.checkOut(7L,"BK-TEST",8L);
        // 70 minutes regular = 2 x 20k; 20 minutes overtime = 1 x 20k x 2.
        assertEquals(0, new BigDecimal("80000").compareTo(result.getTotalAmount()));
        assertEquals(0, new BigDecimal("70000").compareTo(result.getAmountDue()));
    }
}
