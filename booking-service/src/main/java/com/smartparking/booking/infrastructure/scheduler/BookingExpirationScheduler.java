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
    private final com.smartparking.booking.infrastructure.persistence.HoldPolicyStore holdPolicyStore;
    private final ParkingLotClient parkingLotClient;
    private final QueueService queueService;
    private final com.smartparking.booking.application.service.OutboxPublisherService outboxPublisherService;

    /**
     * Runs every 60 seconds to release slots for unpaid bookings that have exceeded the 15-minute hold limit.
     */
    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void expireUnpaidBookings() {
        // Thêm 3 phút Grace Period tránh race condition khi webhook thanh toán PayOS đến trễ
        LocalDateTime threshold = LocalDateTime.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).minusMinutes(3);
        List<Booking> expiredBookings = bookingRepository.findByStatusAndExpiresAtBefore(
                BookingStatusEnum.PENDING_PAYMENT, threshold);

        if (expiredBookings.isEmpty()) {
            return;
        }

        log.info("Found {} expired unpaid bookings to process", expiredBookings.size());

        for (Booking candidate : expiredBookings) {
            try {
                Booking booking = bookingRepository.findByIdForUpdate(candidate.getId()).orElse(null);
                if (booking == null || booking.getStatus() != BookingStatusEnum.PENDING_PAYMENT || !booking.getExpiresAt().isBefore(threshold)) continue;
                booking.setStatus(BookingStatusEnum.EXPIRED);
                bookingRepository.save(booking);
                holdPolicyStore.recordRelease(booking, "EXPIRED", LocalDateTime.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")));

                try {
                    // Release held slot back to parking lot
                    SlotHoldRequest releaseReq = SlotHoldRequest.builder()
                            .vehicleType(booking.getVehicleType())
                            .bookingId(booking.getId())
                            .build();
                    parkingLotClient.releaseSlot(booking.getParkingLotId(), releaseReq);

                    // Promote next waiting driver in queue if any
                    queueService.promoteNextInQueue(booking.getParkingLotId(), booking.getVehicleType());
                } catch (Exception ex) {
                    log.warn("Failed to release slot or promote queue for expired booking {} (kept EXPIRED): {}",
                            booking.getBookingCode(), ex.getMessage());
                }

                log.info("Booking {} marked as EXPIRED for lot {}",
                        booking.getBookingCode(), booking.getParkingLotId());

            } catch (Exception e) {
                log.error("Failed to expire booking {}: {}", candidate.getBookingCode(), e.getMessage());
            }
        }
    }

    /**
     * Runs every 60 seconds to cancel bookings and release slots when the driver never checks in (No-Show).
     */
    @Scheduled(cron = "30 * * * * *")
    @Transactional
    public void expireNoShowBookings() {
        LocalDateTime now = LocalDateTime.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        List<Booking> noShowBookings = bookingRepository.findByStatusAndExpiresAtBefore(
                BookingStatusEnum.PENDING_CHECKIN, now);

        if (noShowBookings.isEmpty()) {
            return;
        }

        log.info("Found {} no-show bookings to process", noShowBookings.size());

        for (Booking candidate : noShowBookings) {
            try {
                Booking booking = bookingRepository.findByIdForUpdate(candidate.getId()).orElse(null);
                if (booking == null || booking.getStatus() != BookingStatusEnum.PENDING_CHECKIN || !booking.getExpiresAt().isBefore(now)) continue;
                var noShowDecision = com.smartparking.booking.domain.BookingRefundPolicy.evaluateNoShow(now);
                String noShowReason = "Tài xế không đến bãi đỗ (No-show)";
                booking.setStatus(BookingStatusEnum.CANCELLED);
                booking.setCancelledAt(noShowDecision.cancelledAt());
                booking.setCancellationReason(noShowReason);
                booking.setRefundAmount(noShowDecision.refundAmount());
                booking.setRefundPercentage(noShowDecision.refundPercentage());
                booking.setRefundPolicy(noShowDecision.refundPolicy());
                booking.setRefundStatus(noShowDecision.refundStatus());
                bookingRepository.save(booking);

                // Publish cancelled event (with reason no-show)


                try {
                    // Release held slot back to parking lot
                    SlotHoldRequest releaseReq = SlotHoldRequest.builder()
                            .vehicleType(booking.getVehicleType())
                            .bookingId(booking.getId())
                            .build();
                    parkingLotClient.releaseSlot(booking.getParkingLotId(), releaseReq);

                    queueService.promoteNextInQueue(booking.getParkingLotId(), booking.getVehicleType());
                } catch (Exception ex) {
                    log.warn("Failed to release slot or promote queue for no-show booking {} (kept CANCELLED): {}",
                            booking.getBookingCode(), ex.getMessage());
                }

                outboxPublisherService.publishBookingCancelled(booking, noShowReason);
                log.info("No-show booking {} cancelled for lot {}",
                        booking.getBookingCode(), booking.getParkingLotId());

            } catch (Exception e) {
                log.error("Failed to process no-show booking {}: {}", candidate.getBookingCode(), e.getMessage());
            }
        }
    }
}
