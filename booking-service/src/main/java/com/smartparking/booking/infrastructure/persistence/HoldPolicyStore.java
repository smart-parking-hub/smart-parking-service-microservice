package com.smartparking.booking.infrastructure.persistence;

import com.smartparking.booking.domain.model.Booking;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class HoldPolicyStore {
    private final JdbcTemplate jdbc;
    public record ActiveBooking(Long id, Long driverId) {}
    public record Release(Long bookingId, Long driverId, Long vehicleId, Long parkingLotId,
                          LocalDateTime releasedAt, String assessment) {}
    public record RateWindow(LocalDateTime startedAt, int count) {}
    // DATETIME stores business wall-clock values. Avoid java.sql.Timestamp's JVM-zone conversion.
    private static final RowMapper<Release> RELEASE = (row, index) -> new Release(row.getLong("booking_id"),
            row.getLong("driver_id"), row.getLong("vehicle_id"), row.getLong("parking_lot_id"),
            row.getObject("released_at", LocalDateTime.class), row.getString("assessment"));

    /** Caller must own a transaction; the lock survives until its completion. */
    public void lock(String key) {
        jdbc.update("INSERT INTO booking_hold_policy_locks (lock_key) VALUES (?) ON DUPLICATE KEY UPDATE lock_key=lock_key", key);
        jdbc.queryForObject("SELECT lock_key FROM booking_hold_policy_locks WHERE lock_key=? FOR UPDATE", String.class, key);
    }
    public List<ActiveBooking> activeForVehicle(Long vehicleId) {
        return jdbc.query("SELECT id,driver_id FROM bookings WHERE vehicle_id=? AND status='PENDING_PAYMENT' ORDER BY id FOR UPDATE",
                (row,index) -> new ActiveBooking(row.getLong(1),row.getLong(2)), vehicleId);
    }
    public List<ActiveBooking> activeForDriver(Long driverId) {
        return jdbc.query("SELECT id,driver_id FROM bookings WHERE driver_id=? AND status='PENDING_PAYMENT' ORDER BY id FOR UPDATE",
                (row,index) -> new ActiveBooking(row.getLong(1),row.getLong(2)), driverId);
    }
    /** Read under the VEHICLE key lock; adjacent [start, end) reservations do not overlap. */
    public boolean hasScheduleConflict(Long vehicleId, LocalDateTime start, LocalDateTime end, LocalDateTime now) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM bookings WHERE vehicle_id=? AND (
                    (status IN ('PENDING_PAYMENT','PENDING_CHECKIN')
                        AND expected_check_in_time < ? AND expected_check_out_time > ?)
                    OR (status IN ('PARKING','OVERTIME','AWAITING_FINAL_PAYMENT')
                        AND COALESCE(check_in_time,expected_check_in_time) < ?
                        AND (expected_check_out_time IS NULL OR expected_check_out_time > ?
                            OR expected_check_out_time <= ?))
                ))
                """, Boolean.class, vehicleId, end, start, end, start, now));
    }
    public Optional<RateWindow> rateWindow(Long driverId) {
        return jdbc.query("SELECT window_started_at,request_count FROM booking_hold_rate_windows WHERE driver_id=? FOR UPDATE",
                (row,index) -> new RateWindow(row.getObject(1, LocalDateTime.class),row.getInt(2)),driverId).stream().findFirst();
    }
    public void setRateWindow(Long driverId, LocalDateTime start, int count) {
        jdbc.update("INSERT INTO booking_hold_rate_windows (driver_id,window_started_at,request_count) VALUES (?,?,?) "
                + "ON DUPLICATE KEY UPDATE window_started_at=VALUES(window_started_at),request_count=VALUES(request_count)",driverId,start,count);
    }
    public void recordRelease(Booking booking, String reason, LocalDateTime releasedAt) {
        jdbc.update("INSERT INTO booking_unpaid_hold_releases (booking_id,driver_id,vehicle_id,parking_lot_id,released_at,release_reason) "
                + "VALUES (?,?,?,?,?,?) ON DUPLICATE KEY UPDATE booking_id=booking_id", booking.getId(),booking.getDriverId(),
                booking.getVehicleId(),booking.getParkingLotId(),releasedAt,reason);
    }
    public boolean hasRelease(Long bookingId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM booking_unpaid_hold_releases WHERE booking_id=?)",Boolean.class,bookingId));
    }
    public List<Release> recentReleasesForUpdate(Long driverId, Long lotId, LocalDateTime since) {
        return jdbc.query("SELECT * FROM booking_unpaid_hold_releases WHERE driver_id=? AND parking_lot_id=? AND released_at>=? "
                + "ORDER BY booking_id FOR UPDATE", RELEASE, driverId,lotId,since);
    }
    public Optional<Release> releaseForUpdate(Long bookingId) {
        return jdbc.query("SELECT * FROM booking_unpaid_hold_releases WHERE booking_id=? FOR UPDATE", RELEASE,bookingId).stream().findFirst();
    }
    public void assess(Long bookingId, String assessment, LocalDateTime now) {
        jdbc.update("UPDATE booking_unpaid_hold_releases SET assessment=?,checked_at=? WHERE booking_id=?",assessment,now,bookingId);
    }
    public List<Long> dueForRecheck(LocalDateTime since, LocalDateTime checkedBefore) {
        return jdbc.queryForList("SELECT booking_id FROM booking_unpaid_hold_releases WHERE released_at>=? AND assessment<>'PROTECTED' "
                + "AND (checked_at IS NULL OR checked_at<?) ORDER BY checked_at,released_at LIMIT 100", Long.class,since,checkedBefore);
    }
}
