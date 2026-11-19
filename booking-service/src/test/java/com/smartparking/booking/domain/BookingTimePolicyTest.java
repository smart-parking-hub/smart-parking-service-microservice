package com.smartparking.booking.domain;
import com.smartparking.booking.application.dto.BookingDto;
import com.smartparking.booking.domain.model.Booking;
import com.smartparking.common.enums.BookingStatusEnum;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;
class BookingTimePolicyTest {
    final LocalDateTime arrival = LocalDateTime.of(2026, 11, 6, 10, 0);
    @Test void capsTheEarlyWindowAtThirtyMinutes() {
        assertEquals(arrival.minusMinutes(30), BookingTimePolicy.checkInOpensAt(arrival, 60));
    }

    @Test void respectsAShorterEarlyWindow() {
        assertEquals(arrival.minusMinutes(15), BookingTimePolicy.checkInOpensAt(arrival, 15));
    }

    @Test void negativeEarlyWindowCannotDelayTheStart() {
        assertEquals(arrival, BookingTimePolicy.checkInOpensAt(arrival, -1));
    }

    @Test void missingArrivalHasNoOpeningTime() {
        assertNull(BookingTimePolicy.checkInOpensAt(null, 30));
    }

    @ParameterizedTest
    @CsvSource({"1,15,15", "1,60,60", "2,15,15", "2,60,60", "0,180,180", ",180,180"})
    void preservesIssuedDeadlinesIncludingLegacyTickets(Integer version, int savedMinutes, int expectedMinutes) {
        var ticket = Booking.builder().schedulePolicyVersion(version).status(BookingStatusEnum.PENDING_CHECKIN)
                .expectedCheckInTime(arrival).expectedCheckOutTime(arrival.plusHours(2))
                .expiresAt(arrival.plusMinutes(savedMinutes)).build();
        assertEquals(arrival.plusMinutes(expectedMinutes), BookingDto.fromEntity(ticket).getExpiresAt());
        assertFalse(BookingTimePolicy.isCheckInExpired(ticket, arrival.plusMinutes(expectedMinutes)));
        assertTrue(BookingTimePolicy.isCheckInExpired(ticket, arrival.plusMinutes(expectedMinutes).plusSeconds(1)));
    }
}
