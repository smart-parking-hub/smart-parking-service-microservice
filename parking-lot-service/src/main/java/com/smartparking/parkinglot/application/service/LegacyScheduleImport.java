package com.smartparking.parkinglot.application.service;

import com.smartparking.common.enums.VehicleTypeEnum;
import com.smartparking.common.exception.*;
import com.smartparking.parkinglot.domain.model.SlotReservation;
import com.smartparking.parkinglot.domain.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Objects;
import static com.smartparking.common.util.BookingSchedulePolicy.*;

/** Stage first; release the old all-day hold only after Booking has committed policy version 0. */
@Service @RequiredArgsConstructor
public class LegacyScheduleImport {
    private final SlotRepository groups;
    private final SlotHoldRepository holds;
    private final SlotReservationRepository reservations;
    private final ParkingLayoutRepository layouts;
    private final CurrentSlotAllocationRepository allocations;
    public record Snapshot(Long bookingId, VehicleTypeEnum vehicleType, String physicalSlotId,
            LocalDateTime startTime, LocalDateTime endTime, LocalDateTime expiresAt,
            LocalDateTime checkInTime, LocalDateTime checkoutTime, String status, int earlyMinutes) {}

    @Transactional
    public void prepare(Long lotId, Snapshot source) {
        layouts.findByParkingLotIdForUpdate(lotId);
        var group = groups.findByParkingLotIdAndVehicleTypeForUpdate(lotId, source.vehicleType()).orElseThrow();
        var existing = reservations.findById(source.bookingId());
        if (existing.isPresent() && !"IMPORTING".equals(existing.get().getState())) return;
        var hold = holds.findById(source.bookingId()).orElseThrow(() -> new AppException(ErrorCode.CONFLICT, "Booking cũ thiếu sổ giữ chỗ, cần đối soát"));
        if (hold.isReleased() || !hold.getSlotId().equals(group.getId()) || !Objects.equals(hold.getPhysicalSlotId(), source.physicalSlotId())
                || source.startTime() == null || source.endTime() == null || !source.endTime().isAfter(source.startTime()))
            throw new AppException(ErrorCode.CONFLICT, "Lịch và sổ giữ chỗ cũ không khớp");
        String target = switch (source.status()) {
            case "PENDING_PAYMENT" -> "HELD";
            case "PENDING_CHECKIN" -> "CONFIRMED";
            case "PARKING", "OVERTIME" -> "OCCUPIED";
            case "AWAITING_FINAL_PAYMENT" -> "COOLDOWN";
            default -> throw new AppException(ErrorCode.CONFLICT, "Chỉ chuyển booking còn hoạt động");
        };
        if ("COOLDOWN".equals(target) && source.checkoutTime() == null)
            throw new AppException(ErrorCode.CONFLICT, "Thiếu giờ xe ra");
        var from = source.startTime().minusMinutes(Math.max(0, Math.min(30, source.earlyMinutes())));
        if (source.checkInTime() != null && source.checkInTime().isBefore(from)) from = source.checkInTime();
        var until = source.endTime();
        if (source.checkoutTime() != null && source.checkoutTime().isAfter(until)) until = source.checkoutTime();
        if ("HELD".equals(target) || "CONFIRMED".equals(target)) {
            var legacyEntryDeadline = source.startTime().plusMinutes(60);
            if (legacyEntryDeadline.isAfter(until)) until = legacyEntryDeadline;
            if (source.expiresAt() != null && source.expiresAt().isAfter(until)) until = source.expiresAt();
        }
        var booking = existing.orElseGet(SlotReservation::new);
        booking.setBookingId(source.bookingId()); booking.setParkingLotId(lotId); booking.setSlotGroupId(group.getId());
        booking.setPhysicalSlotId(hold.getPhysicalSlotId()); booking.setPublicationId(hold.getPublicationId());
        booking.setStartTime(source.startTime()); booking.setEndTime(source.endTime());
        booking.setProtectedStart(from); booking.setProtectedUntil(until.plusMinutes(BUFFER_MINUTES));
        booking.setHoldExpiresAt(source.expiresAt()); booking.setActualCheckout(source.checkoutTime());
        booking.setState("IMPORTING"); booking.setImportState(target); booking.setPolicyVersion(0);
        booking.setUpdatedAt(LocalDateTime.now(ZONE)); reservations.saveAndFlush(booking);
    }

    @Transactional
    public void activate(Long lotId, Long bookingId) {
        layouts.findByParkingLotIdForUpdate(lotId);
        var groupId = reservations.findGroupId(bookingId).orElseThrow();
        var type = groups.findVehicleType(groupId).orElseThrow();
        var group = groups.findByParkingLotIdAndVehicleTypeForUpdate(lotId, type).orElseThrow();
        var booking = reservations.findById(bookingId).orElseThrow();
        if (!booking.getParkingLotId().equals(lotId) || !group.getId().equals(groupId)) throw new AppException(ErrorCode.FORBIDDEN, "Lịch thuộc bãi khác");
        if (!"IMPORTING".equals(booking.getState())) return;
        var hold = holds.findById(bookingId).orElseThrow();
        if (!hold.isReleased()) {
            hold.setReleased(true); holds.save(hold);
            if (!"OCCUPIED".equals(booking.getImportState())) {
                group.setAvailableCapacity(Math.min(group.getTotalCapacity(), group.getAvailableCapacity()+1));
                groups.save(group); allocations.deleteByBookingId(bookingId);
            }
        }
        booking.setState(booking.getImportState()); booking.setImportState(null);
        booking.setUpdatedAt(LocalDateTime.now(ZONE)); reservations.save(booking);
    }
}
