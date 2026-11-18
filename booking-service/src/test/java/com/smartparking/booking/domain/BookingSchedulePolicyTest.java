package com.smartparking.booking.domain;
import com.smartparking.common.util.BookingSchedulePolicy;
import com.smartparking.common.exception.AppException;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
class BookingSchedulePolicyTest {
    @Test void onlyWholeHalfHourBlocksAreAcceptedIncludingOvernight() {
        var start=LocalDateTime.of(2026,11,20,23,30);
        for(int minutes:new int[]{30,60,90,120,1440})assertDoesNotThrow(()->BookingSchedulePolicy.validate(start,start.plusMinutes(minutes)));
        for(int minutes:new int[]{0,-30,15,45})assertThrows(AppException.class,()->BookingSchedulePolicy.validate(start,start.plusMinutes(minutes)));
        assertThrows(AppException.class,()->BookingSchedulePolicy.validate(start.plusSeconds(1),start.plusHours(1)));
        assertThrows(AppException.class,()->BookingSchedulePolicy.validate(start.plusNanos(1),start.plusHours(1)));
        assertThrows(AppException.class,()->BookingSchedulePolicy.validate(null,start));
    }
}
