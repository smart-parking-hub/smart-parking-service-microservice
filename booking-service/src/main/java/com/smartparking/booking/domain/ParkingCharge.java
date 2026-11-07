package com.smartparking.booking.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** Charges each commenced hour; overtime applies only after the promised departure. */
public final class ParkingCharge {
    private ParkingCharge() {}
    public static BigDecimal charge(LocalDateTime from, LocalDateTime until, BigDecimal rate,
            BigDecimal surge, LocalTime surgeStart, LocalTime surgeEnd) {
        if (from == null || until == null || rate == null || rate.signum() <= 0)
            throw new IllegalArgumentException("Missing valid time or tariff");
        if (!until.isAfter(from)) return BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (LocalDateTime hour = from; hour.isBefore(until); hour = hour.plusHours(1)) {
            boolean peak = false;
            if (surgeStart != null && surgeEnd != null && !surgeStart.equals(surgeEnd)) {
                LocalTime time = hour.toLocalTime();
                peak = surgeStart.isBefore(surgeEnd)
                    ? !time.isBefore(surgeStart) && time.isBefore(surgeEnd)
                    : !time.isBefore(surgeStart) || time.isBefore(surgeEnd);
            }
            total = total.add(rate.multiply(peak && surge != null ? surge : BigDecimal.ONE));
        }
        return total.setScale(0, RoundingMode.HALF_UP);
    }
    public static BigDecimal deposit(BigDecimal estimate) {
        return estimate.multiply(new BigDecimal("0.20")).setScale(0, RoundingMode.HALF_UP);
    }
}
