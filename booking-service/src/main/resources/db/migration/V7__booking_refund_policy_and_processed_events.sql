-- Add cancellation refund policy snapshot and manual refund tracking fields to bookings
ALTER TABLE bookings ADD COLUMN cancelled_at DATETIME(6) NULL;
ALTER TABLE bookings ADD COLUMN cancellation_reason VARCHAR(500) NULL;
ALTER TABLE bookings ADD COLUMN refund_amount DECIMAL(12, 2) NULL DEFAULT 0.00;
ALTER TABLE bookings ADD COLUMN refund_percentage INT NULL DEFAULT 0;
ALTER TABLE bookings ADD COLUMN refund_policy VARCHAR(60) NULL;
ALTER TABLE bookings ADD COLUMN refund_status VARCHAR(40) NOT NULL DEFAULT 'NONE';
ALTER TABLE bookings ADD COLUMN refund_reference VARCHAR(120) NULL;

-- Idempotent consumer deduplication table for booking-service
CREATE TABLE IF NOT EXISTS processed_events (
    event_id VARCHAR(64) PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NULL,
    processed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
