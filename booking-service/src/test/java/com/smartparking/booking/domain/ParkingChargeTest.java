package com.smartparking.booking.domain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import static org.junit.jupiter.api.Assertions.*;
class ParkingChargeTest {
    @Test void serverDepositIsTwentyPercentRoundedToDong() {
        assertEquals(new BigDecimal("4000"), ParkingCharge.deposit(new BigDecimal("20000")));
        assertEquals(new BigDecimal("20000"), ParkingCharge.deposit(new BigDecimal("100000")));
        assertEquals(new BigDecimal("20001"), ParkingCharge.deposit(new BigDecimal("100003")));
    }
    @Test void chargesEachStartedHour() {
        var start=LocalDateTime.of(2026,11,6,10,0);
        assertEquals(new BigDecimal("40000"), ParkingCharge.charge(start,start.plusMinutes(61),new BigDecimal("20000"),BigDecimal.ONE,null,null));
    }
    @ParameterizedTest
    @CsvSource({
        "0, 0", "1, 15000", "300000000000, 15000", "1800000000000, 15000",
        "1800000000001, 30000", "2100000000000, 30000", "3600000000000, 30000",
        "3600000000001, 45000"
    })
    void overtimeChargesEachStartedHalfHour(long nanos, String expected) {
        var start = LocalDateTime.of(2026, 11, 17, 10, 0);
        assertEquals(new BigDecimal(expected), ParkingCharge.chargeOvertime(start, start.plusNanos(nanos),
                new BigDecimal("30000"), BigDecimal.ONE, null, null));
    }
    @Test void overtimeEvaluatesPeakAtEachHalfHourStart() {
        var start = LocalDateTime.of(2026, 11, 17, 10, 5);
        assertEquals(new BigDecimal("30000"), ParkingCharge.chargeOvertime(start, start.plusHours(1),
                new BigDecimal("20000"), new BigDecimal("2"), LocalTime.of(10, 30), LocalTime.of(11, 0)));
    }
    @Test void overtimePeakWindowCanCrossMidnight() {
        var start = LocalDateTime.of(2026, 11, 17, 23, 45);
        assertEquals(new BigDecimal("45000"), ParkingCharge.chargeOvertime(start, start.plusMinutes(31),
                new BigDecimal("30000"), new BigDecimal("1.5"), LocalTime.of(22, 0), LocalTime.of(6, 0)));
    }
    @Test void overtimeRoundsMoneyOnlyAfterAddingAllBlocks() {
        var start = LocalDateTime.of(2026, 11, 17, 10, 0);
        assertEquals(new BigDecimal("10001"), ParkingCharge.chargeOvertime(start, start.plusMinutes(5),
                new BigDecimal("20001"), BigDecimal.ONE, null, null));
        assertEquals(new BigDecimal("20001"), ParkingCharge.chargeOvertime(start, start.plusMinutes(35),
                new BigDecimal("20001"), BigDecimal.ONE, null, null));
    }
    @Test void overtimeDoesNotChargeForReversedInterval() {
        var start = LocalDateTime.of(2026, 11, 17, 10, 0);
        assertEquals(BigDecimal.ZERO, ParkingCharge.chargeOvertime(start, start.minusMinutes(1),
                new BigDecimal("20000"), BigDecimal.ONE, null, null));
    }
    @Test void overtimeRejectsMissingRate() {
        var start = LocalDateTime.of(2026, 11, 17, 10, 0);
        assertThrows(IllegalArgumentException.class, () -> ParkingCharge.chargeOvertime(start,
                start.plusMinutes(5), null, BigDecimal.ONE, null, null));
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
