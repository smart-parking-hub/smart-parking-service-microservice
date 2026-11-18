package com.smartparking.common.util;

import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Shared contract for reservations; actual gate timestamps retain their precision. */
public final class BookingSchedulePolicy {
    public static final int ON_TIME_VERSION = 1;
    public static final int VERSION = 2;
    public static final int BLOCK_MINUTES = 30;
    public static final int EARLY_CHECK_IN_MINUTES = 15;
    public static final int BUFFER_MINUTES = 15;
    public static final int LEGACY_BUFFER_MINUTES = 30;
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private BookingSchedulePolicy() {}

    public static boolean hasScheduledWindow(Integer version) {
        return version != null && version >= ON_TIME_VERSION;
    }

    public static int checkInEarlyMinutes(Integer version, int legacyMinutes) {
        if (Integer.valueOf(VERSION).equals(version)) return EARLY_CHECK_IN_MINUTES;
        return hasScheduledWindow(version) ? 0 : legacyMinutes;
    }

    public static int bufferMinutes(Integer version) {
        return Integer.valueOf(VERSION).equals(version) ? BUFFER_MINUTES : LEGACY_BUFFER_MINUTES;
    }

    public static boolean aligned(LocalDateTime time) {
        return time != null && time.getMinute() % BLOCK_MINUTES == 0
                && time.getSecond() == 0 && time.getNano() == 0;
    }

    public static void validate(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !end.isAfter(start))
            throw new AppException(ErrorCode.BAD_REQUEST, "Giờ vào phải ở tương lai và giờ ra phải sau giờ vào");
        if (!aligned(start) || !aligned(end))
            throw new AppException(ErrorCode.BAD_REQUEST, "Chỉ nhận giờ tròn hoặc giờ rưỡi, mỗi block 30 phút");
    }

    public static boolean overlaps(LocalDateTime from, LocalDateTime until, LocalDateTime otherFrom, LocalDateTime otherUntil) {
        return from.isBefore(otherUntil) && otherFrom.isBefore(until);
    }
}
