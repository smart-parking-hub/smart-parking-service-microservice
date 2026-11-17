package com.smartparking.parkinglot.infrastructure.scheduler;

import com.smartparking.parkinglot.application.service.ScheduledSlotService;
import com.smartparking.parkinglot.domain.repository.SlotReservationRepository;
import com.smartparking.parkinglot.infrastructure.client.BookingHoldVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.List;
import static com.smartparking.common.util.BookingSchedulePolicy.ZONE;

/** Recover missed post-commit checkout delivery and cancellation without expiring paid holds by TTL. */
@Component @RequiredArgsConstructor @Slf4j
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "app.schedule.reconciliation.enabled", havingValue = "true", matchIfMissing = true)
public class ScheduledReservationReconciliation {
    private final SlotReservationRepository reservations;
    private final ScheduledSlotService schedules;
    private final BookingHoldVerifier verifier;
    private final com.smartparking.parkinglot.application.service.LegacyScheduleImport legacyImport;
    private long cursor;

    @Scheduled(fixedDelay = 30000, initialDelay = 30000)
    public void reconcile() {
        var batch = reservations.findTop100ByStateInAndBookingIdGreaterThanOrderByBookingIdAsc(
                List.of("HELD", "CONFIRMED", "OCCUPIED", "IMPORTING"), cursor);
        if (batch.isEmpty()) { cursor = 0; return; }
        for (var reservation : batch) {
            cursor = reservation.getBookingId();
            // Do not race a booking transaction which has just made its remote allocation.
            if (reservation.getUpdatedAt().isAfter(LocalDateTime.now(ZONE).minusMinutes(1))) continue;
            try {
                var snapshot = verifier.snapshot(reservation.getBookingId());
                if (snapshot == null) continue; // Unknown is not available.
                if ("IMPORTING".equals(reservation.getState())) {
                    if (Integer.valueOf(0).equals(snapshot.schedulePolicyVersion())) legacyImport.activate(reservation.getParkingLotId(), reservation.getBookingId());
                    continue;
                }
                if ("OCCUPIED".equals(reservation.getState()) && "PENDING_CHECKIN".equals(snapshot.status()) && snapshot.checkInTime() == null) {
                    schedules.abortOccupation(reservation.getParkingLotId(), reservation.getBookingId(), reservation.getOccupationToken());
                    continue;
                }
                if (snapshot.checkoutTime() != null && "OCCUPIED".equals(reservation.getState())) {
                    schedules.complete(reservation.getParkingLotId(), reservation.getBookingId(), snapshot.checkoutTime());
                } else if ("PENDING_CHECKIN".equals(snapshot.status()) && "HELD".equals(reservation.getState())) {
                    schedules.confirm(reservation.getParkingLotId(), reservation.getBookingId());
                } else if (List.of("CANCELLED", "EXPIRED").contains(snapshot.status())
                        || ("ABSENT".equals(snapshot.status()) && reservation.getUpdatedAt().isBefore(LocalDateTime.now(ZONE).minusMinutes(20)))) {
                    schedules.release(reservation.getParkingLotId(), reservation.getBookingId());
                }
            } catch (RuntimeException failure) {
                log.warn("Scheduled reservation reconciliation deferred: booking={}", reservation.getBookingId(), failure);
            }
        }
    }
}
