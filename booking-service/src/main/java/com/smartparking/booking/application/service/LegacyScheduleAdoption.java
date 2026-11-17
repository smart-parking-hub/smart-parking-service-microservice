package com.smartparking.booking.application.service;

import com.smartparking.booking.domain.repository.BookingRepository;
import com.smartparking.booking.infrastructure.client.ParkingLotClient;
import com.smartparking.common.exception.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import java.util.List;

@Service @RequiredArgsConstructor @Slf4j
public class LegacyScheduleAdoption {
    private final BookingRepository bookings;
    private final ParkingLotClient lots;
    @Value("${app.booking.check-in-early-minutes:30}") private int earlyMinutes;

    @Transactional
    public boolean adopt(Long id) {
        var booking = bookings.findByIdForUpdate(id).orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND, "Không tìm thấy booking"));
        if (Integer.valueOf(1).equals(booking.getSchedulePolicyVersion())) return false;
        if (booking.getSchedulePolicyVersion() == null) {
            if (!List.of("PENDING_PAYMENT", "PENDING_CHECKIN", "PARKING", "OVERTIME", "AWAITING_FINAL_PAYMENT").contains(booking.getStatus().name()))
                throw new AppException(ErrorCode.CONFLICT, "Chỉ chuyển đổi booking còn hoạt động");
            lots.prepareLegacyImport(booking.getParkingLotId(), new ParkingLotClient.LegacyReservationSnapshot(booking.getId(),
                    booking.getVehicleType(), booking.getPhysicalSlotId(), booking.getExpectedCheckInTime(), booking.getExpectedCheckOutTime(),
                    booking.getExpiresAt(), booking.getCheckInTime(), booking.getCheckOutTime(), booking.getStatus().name(), earlyMinutes));
            booking.setSchedulePolicyVersion(0); bookings.saveAndFlush(booking);
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try { lots.activateLegacyImport(booking.getParkingLotId(), booking.getId()); }
                catch (RuntimeException failure) { log.warn("Legacy schedule activation deferred to reconciliation: booking={}", id, failure); }
            }
        });
        return true;
    }
}
