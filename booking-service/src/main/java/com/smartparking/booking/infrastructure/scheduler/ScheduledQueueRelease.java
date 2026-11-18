package com.smartparking.booking.infrastructure.scheduler;

import com.smartparking.booking.application.service.QueueService;
import com.smartparking.booking.domain.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import java.time.LocalDateTime;
import static com.smartparking.common.util.BookingSchedulePolicy.*;

@Component @RequiredArgsConstructor
@ConditionalOnProperty(name="app.schedule.reconciliation.enabled", havingValue="true", matchIfMissing=true)
public class ScheduledQueueRelease {
    private final BookingRepository bookings;
    private final QueueService queues;
    private long cursor;
    @Scheduled(fixedDelay=30000, initialDelay=30000)
    @Transactional
    public void releaseAfterBuffer() {
        var now = LocalDateTime.now(ZONE);
        var cutoff = now.minusMinutes(BUFFER_MINUTES);
        var batch=bookings.findSchedulesReadyForQueue(cutoff,cursor,PageRequest.of(0,100));
        if (batch.isEmpty()) { cursor=0; return; }
        for (Long id : batch) {
            cursor=id;
            var booking = bookings.findByIdForUpdate(id).orElseThrow();
            if (booking.getScheduleQueueNotifiedAt() != null || booking.getCheckOutTime() == null
                    || booking.getCheckOutTime().isAfter(cutoff) || booking.getExpectedCheckOutTime().isAfter(cutoff)) continue;
            if (!queues.tryPromoteNextInQueue(booking.getParkingLotId(), booking.getVehicleType())) continue;
            booking.setScheduleQueueNotifiedAt(now);
            bookings.save(booking);
        }
    }
}
