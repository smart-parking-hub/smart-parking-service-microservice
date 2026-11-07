package com.smartparking.booking.domain;

import java.time.LocalDateTime;

public final class BookingTimePolicy {
    private BookingTimePolicy() {}
    public static int earlyMinutes(int configured) { return Math.max(0, Math.min(30, configured)); }
    public static boolean tooEarly(LocalDateTime now, LocalDateTime expected, int configured) {
        return expected == null || now.isBefore(expected.minusMinutes(earlyMinutes(configured)));
    }
}
