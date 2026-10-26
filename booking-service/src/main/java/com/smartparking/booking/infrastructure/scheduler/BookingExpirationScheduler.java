package com.smartparking.booking.infrastructure.scheduler;

import com.smartparking.booking.application.service.QueueService;
import com.smartparking.booking.domain.model.Booking;
import com.smartparking.booking.domain.repository.BookingRepository;
import com.smartparking.booking.infrastructure.client.ParkingLotClient;
import com.smartparking.booking.infrastructure.client.dto.SlotHoldRequest;
import com.smartparking.common.enums.BookingStatusEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingExpirationScheduler {

    private final BookingRepository bookingRepository;
    private final ParkingLotClient parkingLotClient;
    private final QueueService queueService;
    private final com.smartparking.booking.application.service.OutboxPublisherService outboxPublisherService;

    /**
     * Runs every 60 seconds to release slots for unpaid bookings that have exceeded the 15-minute hold limit.
     */
    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void expireUnpaidBookings() {
        LocalDateTime now = LocalDateTime.now();
        List<Booking> expiredBookings = bookingRepository.findByStatusAndExpiresAtBefore(
                BookingStatusEnum.PENDING_PAYMENT, now);

        if (expiredBookings.isEmpty()) {
            return;
        }

        log.info("Found {} expired unpaid bookings to process", expiredBookings.size());

        for (Booking booking : expiredBookings) {
            try {
                booking.setStatus(BookingStatusEnum.EXPIRED);
                bookingRepository.save(booking);

                // Release held slot back to parking lot
                SlotHoldRequest releaseReq = SlotHoldRequest.builder()
                        .vehicleType(booking.getVehicleType())
                        .bookingId(booking.getId())
                        .build();
                parkingLotClient.releaseSlot(booking.getParkingLotId(), releaseReq);

                log.info("Booking {} expired and slot released for lot {}",
                        booking.getBookingCode(), booking.getParkingLotId());

                // Promote next waiting driver in queue if any
                queueService.promoteNextInQueue(booking.getParkingLotId(), booking.getVehicleType());

            } catch (Exception e) {
                log.error("Failed to expire booking {}: {}", booking.getBookingCode(), e.getMessage());
            }
        }
    }

    /**
     * Runs every 60 seconds to cancel bookings and release slots when the driver never checks in (No-Show).
     */
    @Scheduled(cron = "30 * * * * *")
    @Transactional
    public void expireNoShowBookings() {
        LocalDateTime now = LocalDateTime.now();
        List<Booking> noShowBookings = bookingRepository.findByStatusAndExpiresAtBefore(
                BookingStatusEnum.PENDING_CHECKIN, now);

        if (noShowBookings.isEmpty()) {
            return;
        }

        log.info("Found {} no-show bookings to process", noShowBookings.size());

        for (Booking booking : noShowBookings) {
            try {
                booking.setStatus(BookingStatusEnum.CANCELLED);
                bookingRepository.save(booking);

                // Release held slot back to parking lot
                SlotHoldRequest releaseReq = SlotHoldRequest.builder()
                        .vehicleType(booking.getVehicleType())
                        .bookingId(booking.getId())
                        .build();
                parkingLotClient.releaseSlot(booking.getParkingLotId(), releaseReq);

                // Publish cancelled event (with reason no-show)
                outboxPublisherService.publishBookingCancelled(booking, "Tài xế không đến bãi đỗ (No-show)");

                log.info("No-show booking {} cancelled and slot released for lot {}",
                        booking.getBookingCode(), booking.getParkingLotId());

                queueService.promoteNextInQueue(booking.getParkingLotId(), booking.getVehicleType());

            } catch (Exception e) {
                log.error("Failed to process no-show booking {}: {}", booking.getBookingCode(), e.getMessage());
            }
        }
    }
}
