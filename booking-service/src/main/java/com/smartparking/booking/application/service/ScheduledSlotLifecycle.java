package com.smartparking.booking.application.service;

import com.smartparking.booking.domain.BookingTimePolicy;
import com.smartparking.booking.domain.model.Booking;
import com.smartparking.booking.infrastructure.client.ParkingLotClient;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Map;

/** Checkout delivery runs only after the authoritative booking transaction committed. */
public final class ScheduledSlotLifecycle {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ScheduledSlotLifecycle.class);
    private ScheduledSlotLifecycle() {}
    public static void occupy(Booking booking, ParkingLotClient client) {
        if (booking.getSchedulePolicyVersion() == null) return;
        var attempt = Map.of("token", java.util.UUID.randomUUID().toString());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        try { client.abortOccupation(booking.getParkingLotId(), booking.getId(), attempt); }
                        catch (RuntimeException failure) { LOG.error("Occupation compensation requires reconciliation: booking={}", booking.getId(), failure); }
                    }
                }
            });
        }
        client.confirmReservation(booking.getParkingLotId(), booking.getId());
        client.occupyReservation(booking.getParkingLotId(), booking.getId(), attempt);
    }
    public static void checkoutAfterCommit(Booking booking, ParkingLotClient client) {
        if (booking.getSchedulePolicyVersion() == null) return;
        Runnable deliver = () -> {
            try { client.completeReservation(booking.getParkingLotId(), booking.getId(), Map.of("checkoutTime", booking.getCheckOutTime())); }
            catch (RuntimeException failure) { LOG.warn("Checkout delivery deferred to reservation reconciliation: booking={}", booking.getId(), failure); }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { deliver.run(); }
            });
        } else deliver.run();
    }
    public static void releaseAfterCommit(Booking booking, ParkingLotClient client, QueueService queues) {
        Runnable deliver = () -> {
            try {
                client.releaseSlot(booking.getParkingLotId(), com.smartparking.booking.infrastructure.client.dto.SlotHoldRequest.builder()
                        .vehicleType(booking.getVehicleType()).bookingId(booking.getId()).build());
                queues.promoteNextInQueue(booking.getParkingLotId(), booking.getVehicleType());
            } catch (RuntimeException failure) { LOG.warn("Cancellation release deferred to reservation reconciliation: booking={}", booking.getId(), failure); }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { deliver.run(); }
            });
        } else deliver.run();
    }
    public static java.time.LocalDateTime checkInDeadline(Booking booking) {
        int graceMinutes = com.smartparking.common.util.BookingSchedulePolicy.hasScheduledWindow(booking.getSchedulePolicyVersion())
                ? com.smartparking.common.util.BookingSchedulePolicy.BLOCK_MINUTES : 60;
        var deadline = booking.getExpectedCheckInTime().plusMinutes(graceMinutes);
        return BookingTimePolicy.capCheckInDeadline(booking, deadline);
    }
}
