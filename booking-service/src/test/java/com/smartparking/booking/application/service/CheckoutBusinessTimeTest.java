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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.TimeZone;
import static com.smartparking.booking.domain.BookingRefundPolicy.BUSINESS_ZONE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckoutBusinessTimeTest {
    @Mock BookingRepository bookings;
    @Mock ParkingLotClient lots;
    @Mock OutboxPublisherService events;
    @Mock QueueService queue;
    @InjectMocks CheckInOutServiceImpl service;

    @Test void checkoutUsesVietnamTimeEvenWhenHostUsesUtc() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
            Booking booking = Booking.builder().bookingCode("BK-TIME").merchantId(7L).parkingLotId(8L)
                    .status(BookingStatusEnum.OVERTIME).checkInTime(now.minusMinutes(90))
                    .expectedCheckOutTime(now.minusMinutes(20)).hourlyRate(new BigDecimal("20000"))
                    .advanceDeposit(new BigDecimal("10000")).overtimePenaltyMultiplier(new BigDecimal("2")).build();
            when(bookings.findByBookingCodeForUpdate("BK-TIME")).thenReturn(Optional.of(booking));
            var result = service.checkOut(7L, "BK-TIME", 8L);
            assertEquals(90, result.getParkingMinutes());
            assertEquals(0, new BigDecimal("80000").compareTo(result.getTotalAmount()));
            assertEquals(0, new BigDecimal("70000").compareTo(result.getAmountDue()));
            assertFalse(booking.getCheckOutTime().isBefore(now));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test void successfulCheckInCannotBeRepeatedOrChangeEntryTime() {
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        Booking booking = Booking.builder().bookingCode("BK-ONCE").merchantId(7L).parkingLotId(8L)
                .status(BookingStatusEnum.PENDING_CHECKIN).expectedCheckInTime(now.minusMinutes(1))
                .expiresAt(now.plusHours(1)).build();
        when(bookings.findByBookingCodeForUpdate("BK-ONCE")).thenReturn(Optional.of(booking));
        service.checkIn(7L, "BK-ONCE", 8L);
        LocalDateTime entry = booking.getCheckInTime();
        assertThrows(AppException.class, () -> service.checkIn(7L, "BK-ONCE", 8L));
        assertEquals(entry, booking.getCheckInTime());
        verify(events, times(1)).publishBookingCheckedIn(booking);
        verify(bookings, times(1)).save(booking);
    }
}
