package com.smartparking.parkinglot.application.service;

import com.smartparking.common.enums.ParkingLotStatusEnum;
import com.smartparking.common.enums.VehicleTypeEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.common.util.BookingSchedulePolicy;
import com.smartparking.parkinglot.application.dto.*;
import com.smartparking.parkinglot.domain.model.*;
import com.smartparking.parkinglot.domain.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import static com.smartparking.common.util.BookingSchedulePolicy.*;

@Service
@RequiredArgsConstructor
public class ScheduledSlotService {
    private final SlotRepository slots;
    private final SlotReservationRepository reservations;
    private final SlotHoldRepository legacyHolds;
    private final ParkingLayoutRepository layouts;
    private final PhysicalParkingSlotRepository physicalSlots;
    private final CurrentSlotAllocationRepository allocations;
    private final PricingRuleRepository prices;
    private final com.smartparking.parkinglot.infrastructure.config.ParkingLayoutProperties layoutProperties;
    @Value("${app.schedule.early.enabled:false}")
    private boolean earlyArrivalEnabled;

    private int newReservationPolicy() {
        return earlyArrivalEnabled ? VERSION : ON_TIME_VERSION;
    }

    private LocalDateTime now() { return LocalDateTime.now(ZONE); }
    private AppException conflict(String message) { return new AppException(ErrorCode.SLOT_NOT_AVAILABLE, message); }
    private Slot group(Long lotId, VehicleTypeEnum type, boolean lock) {
        // Publication uses layout -> group order as well, preventing a layout/hold race.
        if (lock) layouts.findByParkingLotIdForUpdate(lotId);
        Slot group = (lock ? slots.findByParkingLotIdAndVehicleTypeForUpdate(lotId, type)
                : slots.findByParkingLotIdAndVehicleType(lotId, type))
                .orElseThrow(() -> conflict("Bãi chưa có cấu hình cho loại xe này"));
        return group;
    }
    private void requireAccepting(Slot group) {
        if (group.getParkingLot().getStatus() != ParkingLotStatusEnum.APPROVED)
            throw new AppException(ErrorCode.BAD_REQUEST, "Bãi chưa được duyệt hoặc đã ngừng nhận xe");
    }

    @Transactional
    public SlotHoldResponse hold(Long lotId, ScheduledHoldRequest request) {
        BookingSchedulePolicy.validate(request.startTime(), request.endTime());
        if (request.physicalSlotId() != null && !layoutProperties.isPhysicalBookingEnabled())
            throw new AppException(ErrorCode.BAD_REQUEST, "Tính năng chọn ô vật lý đang tắt; có thể chọn tự phân bổ");
        Slot group = group(lotId, request.vehicleType(), true);
        var previous = reservations.findById(request.bookingId());
        if (previous.isPresent()) {
            var old = previous.get();
            if (!old.getParkingLotId().equals(lotId) || !old.getSlotGroupId().equals(group.getId())
                    || !old.getStartTime().equals(request.startTime()) || !old.getEndTime().equals(request.endTime())
                    || (request.physicalSlotId() != null && !Objects.equals(request.physicalSlotId(), old.getPhysicalSlotId()))
                    || "RELEASED".equals(old.getState()))
                throw new AppException(ErrorCode.CONFLICT, "Mã giữ chỗ đã dùng với lịch khác hoặc đã hết hiệu lực");
            return quote(group, old, 0);
        }
        requireAccepting(group);
        if (!request.startTime().isAfter(now()) || !request.holdExpiresAt().isAfter(now()))
            throw new AppException(ErrorCode.BAD_REQUEST, "Giờ vào và hạn giữ chỗ phải ở tương lai");
        Integer requestedVersion = request.schedulePolicyVersion();
        if (requestedVersion != null && requestedVersion != ON_TIME_VERSION && requestedVersion != VERSION)
            throw new AppException(ErrorCode.BAD_REQUEST, "Phiên bản đặt lịch không được hỗ trợ");
        int version = newReservationPolicy();
        if (version == VERSION && !Integer.valueOf(VERSION).equals(requestedVersion))
            throw new AppException(ErrorCode.CONFLICT, "Dịch vụ đặt chỗ chưa hỗ trợ chính sách vào sớm đang áp dụng");
        LocalDateTime from = request.startTime().minusMinutes(checkInEarlyMinutes(version, 0));
        LocalDateTime until = request.endTime().plusMinutes(bufferMinutes(version));
        var context = context(group, from, until);
        var available = availability(context, from, until, request.physicalSlotId(), version);
        if (available.remainingCapacity() <= 0)
            throw conflict("Không còn ô phù hợp cho khung giờ, thời gian vào sớm và khoảng đệm");
        String selected = null;
        if (context.physical()) {
            selected = request.physicalSlotId() != null ? request.physicalSlotId() : available.slots().getFirst().id();
        } else if (request.physicalSlotId() != null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Bãi chưa bật chọn ô đỗ vật lý");
        }
        var reservation = SlotReservation.builder().bookingId(request.bookingId()).parkingLotId(lotId)
                .slotGroupId(group.getId()).physicalSlotId(selected).publicationId(context.publicationId())
                .startTime(request.startTime()).endTime(request.endTime()).protectedStart(from)
                .protectedUntil(until).holdExpiresAt(request.holdExpiresAt())
                .state("HELD").policyVersion(version).updatedAt(now()).build();
        reservations.saveAndFlush(reservation);
        return quote(group, reservation, available.remainingCapacity() - 1);
    }

    private SlotHoldResponse quote(Slot group, SlotReservation reservation, int remaining) {
        var price = prices.findByParkingLotIdAndVehicleType(group.getParkingLot().getId(), group.getVehicleType())
                .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST, "Bãi chưa có bảng giá"));
        if (price.getBasePricePerHour() == null || price.getBasePricePerHour().signum() <= 0)
            throw new AppException(ErrorCode.BAD_REQUEST, "Bảng giá không hợp lệ");
        var lot = group.getParkingLot();
        String code = reservation.getPhysicalSlotId() == null ? null : physicalSlots.findById(reservation.getPhysicalSlotId())
                .map(PhysicalParkingSlot::getCode).orElse(null);
        return SlotHoldResponse.builder().success(true).slotId(group.getId()).parkingLotId(lot.getId())
                .vehicleType(group.getVehicleType()).remainingCapacity(remaining).baseHourlyPrice(price.getBasePricePerHour())
                .merchantId(lot.getMerchantId()).parkingLotName(lot.getName()).parkingLotAddress(lot.getAddress())
                .surgeMultiplier(price.getSurgeMultiplier()).surgeStartTime(price.getSurgeStartTime())
                .surgeEndTime(price.getSurgeEndTime()).overtimePenaltyMultiplier(price.getOvertimePenaltyMultiplier())
                .physicalSlotId(reservation.getPhysicalSlotId()).physicalSlotCode(code)
                .layoutPublicationId(reservation.getPublicationId()).schedulePolicyVersion(reservation.getPolicyVersion())
                .message("Đã giữ lịch, khoảng đệm " + bufferMinutes(reservation.getPolicyVersion()) + " phút").build();
    }

    private record Context(Slot group, boolean physical, String publicationId, int capacity,
            Set<String> legacyPhysical, List<PhysicalParkingSlot> physicalSlots, List<SlotReservation> bookings) {}

    private Context context(Slot group, LocalDateTime from, LocalDateTime until) {
        var layout = layouts.findByParkingLotId(group.getParkingLot().getId());
        boolean physical = layout.isPresent() && layout.get().getActivePublicationId() != null
                && layout.get().getAllocationMode() == AllocationMode.PHYSICAL;
        var holds = legacyHolds.findBySlotIdAndReleasedFalse(group.getId());
        // Unreconciled historical holds remain unavailable, including missing journal rows.
        int legacyCount = Math.max(holds.size(), group.getTotalCapacity() - group.getAvailableCapacity()
                - Math.toIntExact(reservations.countBySlotGroupIdAndState(group.getId(), "OCCUPIED")));
        Set<String> legacyPhysical = new HashSet<>();
        holds.stream().map(SlotHold::getPhysicalSlotId).filter(Objects::nonNull).forEach(legacyPhysical::add);
        var bookings = reservations.findBlocking(group.getId(), from, until);
        var known = new HashSet<Long>();
        bookings.forEach(b -> known.add(b.getBookingId()));
        if (physical) allocations.findByParkingLotId(group.getParkingLot().getId()).stream()
                .filter(a -> a.getSlotGroupId().equals(group.getId()) && !known.contains(a.getBookingId()))
                .map(CurrentSlotAllocation::getPhysicalSlotId).forEach(legacyPhysical::add);
        return new Context(group, physical, layout.map(ParkingLayout::getActivePublicationId).orElse(null),
                Math.max(0, group.getTotalCapacity() - legacyCount), legacyPhysical,
                physical ? physicalSlots.findByParkingLotIdAndVehicleType(group.getParkingLot().getId(), group.getVehicleType()) : List.of(), bookings);
    }

    private LocalDateTime effectiveUntil(SlotReservation booking) {
        // The expected exit is never evidence that an overdue vehicle physically left.
        return "OCCUPIED".equals(booking.getState()) && !booking.getEndTime().isAfter(now())
                ? LocalDateTime.of(9999, 12, 31, 23, 59) : booking.getProtectedUntil();
    }

    private int peak(List<SlotReservation> bookings, LocalDateTime from, LocalDateTime until) {
        TreeMap<LocalDateTime, Integer> changes = new TreeMap<>();
        for (var booking : bookings) {
            LocalDateTime end = effectiveUntil(booking);
            if (!overlaps(from, until, booking.getProtectedStart(), end)) continue;
            changes.merge(booking.getProtectedStart().isBefore(from) ? from : booking.getProtectedStart(), 1, Integer::sum);
            changes.merge(end.isAfter(until) ? until : end, -1, Integer::sum);
        }
        int count = 0, maximum = 0;
        for (int delta : changes.values()) { count += delta; maximum = Math.max(maximum, count); }
        return maximum;
    }

    private ScheduleAvailability availability(Context context, LocalDateTime from, LocalDateTime until,
                                              String physicalId, int version) {
        int remaining = Math.max(0, context.capacity() - peak(context.bookings(), from, until));
        List<ScheduleAvailability.SlotOption> options = context.physicalSlots().stream()
                .filter(s -> "AVAILABLE".equalsIgnoreCase(s.getStatus()) && !context.legacyPhysical().contains(s.getId()))
                .filter(s -> physicalId == null || physicalId.equals(s.getId()))
                .filter(s -> context.bookings().stream().noneMatch(b -> s.getId().equals(b.getPhysicalSlotId())
                        && overlaps(from, until, b.getProtectedStart(), effectiveUntil(b))))
                .sorted(Comparator.comparing(PhysicalParkingSlot::getCode))
                .map(s -> new ScheduleAvailability.SlotOption(s.getId(), s.getCode())).toList();
        if (context.physical()) remaining = Math.min(remaining, options.size());
        else if (physicalId != null) remaining = 0;
        int early = checkInEarlyMinutes(version, 0);
        int turnaround = bufferMinutes(version);
        return new ScheduleAvailability(ZONE.getId(), BLOCK_MINUTES, early + turnaround, early, turnaround,
                context.physical() ? "PHYSICAL" : "GROUP", now(), remaining, options, List.of());
    }

    @Transactional(readOnly = true)
    public ScheduleAvailability available(Long lotId, VehicleTypeEnum type, LocalDateTime start, LocalDateTime end, String physicalId) {
        BookingSchedulePolicy.validate(start, end);
        var group = group(lotId, type, false); requireAccepting(group);
        int version = newReservationPolicy();
        var from = start.minusMinutes(checkInEarlyMinutes(version, 0));
        var until = end.plusMinutes(bufferMinutes(version));
        var result = availability(context(group, from, until), from, until, physicalId, version);
        return layoutProperties.isPhysicalBookingEnabled() ? result : new ScheduleAvailability(result.timezone(), result.blockMinutes(),
                result.bufferMinutes(), result.checkInEarlyMinutes(), result.turnaroundMinutes(), result.allocationMode(),
                result.updatedAt(),
                result.remainingCapacity(), List.of(), result.blocks());
    }

    @Transactional(readOnly = true)
    public ScheduleAvailability timeline(Long lotId, VehicleTypeEnum type, LocalDate date, String physicalId) {
        LocalDateTime from = date.atStartOfDay(), until = from.plusDays(1);
        var group = group(lotId, type, false); requireAccepting(group);
        int version = newReservationPolicy();
        int early = checkInEarlyMinutes(version, 0);
        int turnaround = bufferMinutes(version);
        var context = context(group, from.minusMinutes(early), until.plusMinutes(turnaround));
        List<ScheduleAvailability.TimeBlock> blocks = new ArrayList<>();
        for (var start = from; start.isBefore(until); start = start.plusMinutes(BLOCK_MINUTES)) {
            var end = start.plusMinutes(BLOCK_MINUTES);
            var protectedFrom = start.minusMinutes(early);
            var protectedUntil = end.plusMinutes(turnaround);
            var result = availability(context, protectedFrom, protectedUntil, physicalId, version);
            final var blockStart = start;
            boolean buffer = context.bookings().stream().anyMatch(b -> (physicalId == null || physicalId.equals(b.getPhysicalSlotId()))
                    && !"OCCUPIED".equals(b.getState())
                    && ((!b.getEndTime().isAfter(blockStart) && b.getProtectedUntil().isAfter(protectedFrom))
                        || (!b.getStartTime().isBefore(end) && b.getProtectedStart().isBefore(protectedUntil))));
            String status = !start.isAfter(now()) ? "PAST" : result.remainingCapacity() > 0 ? "AVAILABLE" : buffer ? "BUFFER" : "FULL";
            blocks.add(new ScheduleAvailability.TimeBlock(start, end, result.remainingCapacity(), status));
        }
        return new ScheduleAvailability(ZONE.getId(), BLOCK_MINUTES, early + turnaround,
                early, turnaround, context.physical() ? "PHYSICAL" : "GROUP", now(), 0, List.of(), blocks);
    }

    public boolean contains(Long bookingId) { return reservations.existsById(bookingId); }

    private SlotReservation locked(Long lotId, Long bookingId) {
        var groupId = reservations.findGroupId(bookingId).orElseThrow(() -> conflict("Không có lịch giữ chỗ"));
        var type = slots.findVehicleType(groupId).orElseThrow(() -> conflict("Không có nhóm chỗ đỗ"));
        group(lotId, type, true);
        var booking = reservations.findByIdForUpdate(bookingId).orElseThrow();
        if (!booking.getParkingLotId().equals(lotId)) throw new AppException(ErrorCode.FORBIDDEN, "Lịch thuộc bãi khác");
        return booking;
    }

    @Transactional
    public void confirm(Long lotId, Long bookingId) {
        var booking = locked(lotId, bookingId);
        if ("RELEASED".equals(booking.getState())) throw conflict("Lịch đã giải phóng, cần đối soát thanh toán");
        if ("HELD".equals(booking.getState())) { booking.setState("CONFIRMED"); booking.setUpdatedAt(now()); reservations.save(booking); }
    }

    @Transactional
    public void occupy(Long lotId, Long bookingId, String token) {
        var booking = locked(lotId, bookingId);
        Slot group = slots.findById(booking.getSlotGroupId()).orElseThrow();
        requireAccepting(group);
        if ("OCCUPIED".equals(booking.getState())) {
            booking.setOccupationToken(token); booking.setUpdatedAt(now()); reservations.save(booking); return;
        }
        var entryTime = now();
        if (!"CONFIRMED".equals(booking.getState()) || entryTime.isBefore(booking.getProtectedStart())
                || (hasScheduledWindow(booking.getPolicyVersion()) && !entryTime.isBefore(booking.getEndTime())))
            throw conflict("Chưa đến giờ vào hoặc lịch không còn nhận check-in");
        var others = reservations.findBlockingAtForUpdate(booking.getSlotGroupId(), entryTime).stream()
                .filter(b -> !b.getBookingId().equals(bookingId))
                .filter(b -> effectiveUntil(b).isAfter(entryTime)).toList();
        // Actual occupancy and legacy holds already reduce the locked counter. Cooldowns do not.
        long activeCooldowns = others.stream().filter(b -> "COOLDOWN".equals(b.getState())).count();
        if (group.getAvailableCapacity() <= activeCooldowns)
            throw conflict("Ô đỗ chưa giải phóng hoặc chưa hết khoảng đệm, liên hệ nhân viên bãi");
        if (booking.getPhysicalSlotId() != null) {
            if (others.stream().anyMatch(b -> booking.getPhysicalSlotId().equals(b.getPhysicalSlotId())))
                throw conflict("Ô đỗ chưa hết khoảng đệm");
            var actual = allocations.findByPhysicalSlotIdForUpdate(booking.getPhysicalSlotId());
            if (actual.isPresent() && !actual.get().getBookingId().equals(bookingId))
                throw conflict("Xe trước chưa ra khỏi ô, liên hệ nhân viên bãi");
            if (actual.isEmpty()) allocations.saveAndFlush(CurrentSlotAllocation.builder().parkingLotId(lotId)
                    .physicalSlotId(booking.getPhysicalSlotId()).bookingId(bookingId).slotGroupId(group.getId()).build());
        }
        group.setAvailableCapacity(group.getAvailableCapacity() - 1); slots.save(group);
        booking.setOccupationToken(token);
        booking.setState("OCCUPIED"); booking.setUpdatedAt(now()); reservations.save(booking);
    }

    @Transactional
    public void complete(Long lotId, Long bookingId, LocalDateTime checkout) {
        var booking = locked(lotId, bookingId);
        if ("COOLDOWN".equals(booking.getState())) {
            if (!Objects.equals(booking.getActualCheckout(), checkout)) throw new AppException(ErrorCode.CONFLICT, "Giờ ra đã được ghi nhận");
            return;
        }
        if (!"OCCUPIED".equals(booking.getState()) || checkout == null || checkout.isBefore(booking.getProtectedStart()) || checkout.isAfter(now().plusSeconds(5)))
            throw conflict("Không thể ghi nhận giờ ra cho lịch này");
        booking.setActualCheckout(checkout);
        booking.setProtectedUntil((checkout.isAfter(booking.getEndTime()) ? checkout : booking.getEndTime())
                .plusMinutes(bufferMinutes(booking.getPolicyVersion())));
        booking.setState("COOLDOWN"); booking.setUpdatedAt(now());
        var group = slots.findById(booking.getSlotGroupId()).orElseThrow();
        group.setAvailableCapacity(Math.min(group.getTotalCapacity(), group.getAvailableCapacity() + 1)); slots.save(group);
        allocations.deleteByBookingId(bookingId); reservations.save(booking);
    }

    @Transactional
    public void release(Long lotId, Long bookingId) {
        var booking = locked(lotId, bookingId);
        if ("OCCUPIED".equals(booking.getState())) throw conflict("Xe đang đỗ, cần ghi nhận checkout trước");
        if ("COOLDOWN".equals(booking.getState()) || "RELEASED".equals(booking.getState())) return;
        if ("IMPORTING".equals(booking.getState())) {
            if ("OCCUPIED".equals(booking.getImportState()) || "COOLDOWN".equals(booking.getImportState()))
                throw conflict("Cần hoàn tất đối soát giờ xe ra và khoảng đệm trước khi giải phóng");
            var oldHold = legacyHolds.findById(bookingId).orElseThrow();
            if (!oldHold.isReleased()) {
                oldHold.setReleased(true); legacyHolds.save(oldHold);
                var group = slots.findById(booking.getSlotGroupId()).orElseThrow();
                group.setAvailableCapacity(Math.min(group.getTotalCapacity(), group.getAvailableCapacity()+1)); slots.save(group);
                allocations.deleteByBookingId(bookingId);
            }
        }
        booking.setState("RELEASED"); booking.setUpdatedAt(now()); reservations.save(booking);
    }

    @Transactional
    public void abortOccupation(Long lotId, Long bookingId, String token) {
        var booking = locked(lotId, bookingId);
        if (!"OCCUPIED".equals(booking.getState()) || !Objects.equals(token, booking.getOccupationToken())) return;
        allocations.deleteByBookingId(bookingId);
        var group = slots.findById(booking.getSlotGroupId()).orElseThrow();
        group.setAvailableCapacity(Math.min(group.getTotalCapacity(), group.getAvailableCapacity() + 1)); slots.save(group);
        booking.setState("CONFIRMED"); booking.setUpdatedAt(now()); reservations.save(booking);
    }

    @Transactional
    public void assertLayoutSafe(Long lotId) {
        if (!reservations.findLotActiveForUpdate(lotId, now()).isEmpty())
            throw new AppException(ErrorCode.CONFLICT, "Bãi còn lịch đặt hoặc khoảng đệm, chưa thể đổi sơ đồ");
    }

    @Transactional
    public void assertConfigurationSafe(Long groupId, int newCapacity) {
        var future = reservations.findActiveForUpdate(groupId).stream()
                .filter(b -> "OCCUPIED".equals(b.getState()) || b.getProtectedUntil().isAfter(now())).toList();
        if (!future.isEmpty() && newCapacity < slots.findById(groupId).orElseThrow().getTotalCapacity())
            throw new AppException(ErrorCode.CONFLICT, "Không thể giảm sức chứa khi còn lịch đặt hoặc khoảng đệm");
    }

    @Transactional
    public void assertLegacyHoldAllowed(Long groupId) {
        if (reservations.findActiveForUpdate(groupId).stream()
                .anyMatch(b -> "OCCUPIED".equals(b.getState()) || b.getProtectedUntil().isAfter(now())))
            throw new AppException(ErrorCode.CONFLICT, "Bãi có lịch theo giờ; caller phải gửi giờ vào/ra qua API lịch đặt");
    }
}
