CREATE INDEX idx_bookings_vehicle_pending ON bookings (vehicle_id, status);
CREATE INDEX idx_bookings_driver_pending ON bookings (driver_id, status);

CREATE TABLE booking_hold_policy_locks (
    lock_key VARCHAR(100) PRIMARY KEY
);

CREATE TABLE booking_hold_rate_windows (
    driver_id BIGINT PRIMARY KEY,
    window_started_at DATETIME(6) NOT NULL,
    request_count INT NOT NULL
);

-- Only releases committed after rollout are recorded. Do not backfill penalties.
CREATE TABLE booking_unpaid_hold_releases (
    booking_id BIGINT PRIMARY KEY,
    driver_id BIGINT NOT NULL,
    vehicle_id BIGINT NOT NULL,
    parking_lot_id BIGINT NOT NULL,
    released_at DATETIME(6) NOT NULL,
    release_reason VARCHAR(30) NOT NULL,
    assessment VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    checked_at DATETIME(6) NULL,
    INDEX idx_unpaid_release_driver_lot (driver_id, parking_lot_id, released_at),
    INDEX idx_unpaid_release_recheck (released_at, checked_at)
);
