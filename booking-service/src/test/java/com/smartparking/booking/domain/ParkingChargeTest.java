package com.smartparking.booking.domain;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import static org.junit.jupiter.api.Assertions.*;
class ParkingChargeTest {
    @Test void serverDepositIsTwentyPercentRoundedToDong() {
        assertEquals(new BigDecimal("20000"), ParkingCharge.deposit(new BigDecimal("100000")));
        assertEquals(new BigDecimal("20001"), ParkingCharge.deposit(new BigDecimal("100003")));
    }
    @Test void chargesEachStartedHour() {
        var start=LocalDateTime.of(2026,11,6,10,0);
        assertEquals(new BigDecimal("40000"), ParkingCharge.charge(start,start.plusMinutes(61),new BigDecimal("20000"),BigDecimal.ONE,null,null));
    }
    @Test void peakWindowCanCrossMidnight() {
        var start=LocalDateTime.of(2026,11,6,23,0);
        assertEquals(new BigDecimal("60000"), ParkingCharge.charge(start,start.plusHours(2),new BigDecimal("20000"),new BigDecimal("1.5"),LocalTime.of(22,0),LocalTime.of(6,0)));
    }
    @Test void rateIsNotGuessedWhenMissing() {
        var start=LocalDateTime.of(2026,11,6,10,0);
        assertThrows(IllegalArgumentException.class,()->ParkingCharge.charge(start,start.plusHours(1),null,BigDecimal.ONE,null,null));
    }
}
