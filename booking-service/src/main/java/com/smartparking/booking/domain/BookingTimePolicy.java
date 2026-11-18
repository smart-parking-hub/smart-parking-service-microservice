package com.smartparking.booking.domain;

import java.time.LocalDateTime;

public final class BookingTimePolicy {
    private BookingTimePolicy() {}
    public static LocalDateTime checkInOpensAt(LocalDateTime expected, int earlyMinutes) {
        return expected == null ? null : expected.minusMinutes(Math.max(0, Math.min(30, earlyMinutes)));
    }
}
