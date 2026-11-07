package com.smartparking.booking.domain;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BookingTimePolicyTest {
    final LocalDateTime arrival = LocalDateTime.of(2026, 11, 6, 10, 0);
    @Test void exactlyThirtyMinutesEarlyIsAllowed() { assertFalse(BookingTimePolicy.tooEarly(arrival.minusMinutes(30), arrival, 30)); }
    @Test void thirtyMinutesAndOneSecondEarlyIsRejected() { assertTrue(BookingTimePolicy.tooEarly(arrival.minusMinutes(30).minusSeconds(1), arrival, 60)); }
    @Test void stricterLotConfigurationIsRespected() { assertTrue(BookingTimePolicy.tooEarly(arrival.minusMinutes(20), arrival, 15)); }
    @Test void missingArrivalIsNeverAccepted() { assertTrue(BookingTimePolicy.tooEarly(arrival, null, 30)); }
}
