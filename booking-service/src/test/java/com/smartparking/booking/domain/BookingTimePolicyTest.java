package com.smartparking.booking.domain;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
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
}
