-- Null preserves legacy check-in rights and exact historical reservation timestamps.
ALTER TABLE bookings ADD COLUMN schedule_policy_version INT NULL;
ALTER TABLE bookings ADD COLUMN schedule_queue_notified_at DATETIME(6) NULL;
