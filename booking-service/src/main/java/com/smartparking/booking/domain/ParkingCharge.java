package com.smartparking.booking.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** Charges commenced hours for regular parking and commenced half-hours for overtime. */
public final class ParkingCharge {
    private ParkingCharge() {}
    public static BigDecimal charge(LocalDateTime from, LocalDateTime until, BigDecimal rate,
            BigDecimal surge, LocalTime surgeStart, LocalTime surgeEnd) {
        return chargeBlocks(from, until, rate, surge, surgeStart, surgeEnd, 60);
    }
    public static BigDecimal chargeOvertime(LocalDateTime from, LocalDateTime until, BigDecimal hourlyRate,
            BigDecimal surge, LocalTime surgeStart, LocalTime surgeEnd) {
        return chargeBlocks(from, until, hourlyRate, surge, surgeStart, surgeEnd, 30);
    }
    private static BigDecimal chargeBlocks(LocalDateTime from, LocalDateTime until, BigDecimal rate,
            BigDecimal surge, LocalTime surgeStart, LocalTime surgeEnd, int blockMinutes) {
        if (from == null || until == null || rate == null || rate.signum() <= 0)
            throw new IllegalArgumentException("Missing valid time or tariff");
        if (!until.isAfter(from)) return BigDecimal.ZERO;
        BigDecimal blockRate = rate.multiply(BigDecimal.valueOf(blockMinutes)).divide(BigDecimal.valueOf(60));
        BigDecimal total = BigDecimal.ZERO;
        for (LocalDateTime block = from; block.isBefore(until); block = block.plusMinutes(blockMinutes)) {
            boolean peak = false;
            if (surgeStart != null && surgeEnd != null && !surgeStart.equals(surgeEnd)) {
                LocalTime time = block.toLocalTime();
                peak = surgeStart.isBefore(surgeEnd)
                    ? !time.isBefore(surgeStart) && time.isBefore(surgeEnd)
                    : !time.isBefore(surgeStart) || time.isBefore(surgeEnd);
            }
            total = total.add(blockRate.multiply(peak && surge != null ? surge : BigDecimal.ONE));
        }
        return total.setScale(0, RoundingMode.HALF_UP);
    }
    public static BigDecimal deposit(BigDecimal estimate) {
        return estimate.multiply(new BigDecimal("0.20")).setScale(0, RoundingMode.HALF_UP);
    }
}
