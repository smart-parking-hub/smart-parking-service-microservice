package com.smartparking.booking.domain;

import com.smartparking.booking.domain.model.Booking;
import com.smartparking.common.util.BookingSchedulePolicy;

import java.time.LocalDateTime;

public final class BookingTimePolicy {
    private BookingTimePolicy() {}
    public static LocalDateTime checkInOpensAt(LocalDateTime expected, int earlyMinutes) {
        return expected == null ? null : expected.minusMinutes(Math.max(0, Math.min(30, earlyMinutes)));
    }

    public static LocalDateTime capCheckInDeadline(Booking booking, LocalDateTime deadline) {
        LocalDateTime effective = deadline;
        LocalDateTime expectedStart = booking.getExpectedCheckInTime();
        if (BookingSchedulePolicy.hasScheduledWindow(booking.getSchedulePolicyVersion()) && expectedStart != null) {
            LocalDateTime maxGrace = expectedStart.plusMinutes(BookingSchedulePolicy.BLOCK_MINUTES);
            if (effective == null || maxGrace.isBefore(effective)) {
                effective = maxGrace;
            }
        }
        LocalDateTime scheduledEnd = booking.getExpectedCheckOutTime();
        if (BookingSchedulePolicy.hasScheduledWindow(booking.getSchedulePolicyVersion()) && scheduledEnd != null
                && (effective == null || scheduledEnd.isBefore(effective))) {
            return scheduledEnd;
        }
        return effective;
    }

    public static boolean isCheckInExpired(Booking booking, LocalDateTime now) {
        LocalDateTime expiresAt = capCheckInDeadline(booking, booking.getExpiresAt());
        if (expiresAt == null) return false;
        return expiresAt.isBefore(now) || (BookingSchedulePolicy.hasScheduledWindow(booking.getSchedulePolicyVersion())
                && expiresAt.equals(booking.getExpectedCheckOutTime()) && expiresAt.equals(now));
    }
}
