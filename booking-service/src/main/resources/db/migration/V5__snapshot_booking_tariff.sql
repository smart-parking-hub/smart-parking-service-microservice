ALTER TABLE bookings
 ADD COLUMN surge_multiplier DECIMAL(3,2) NULL,
 ADD COLUMN surge_start_time TIME NULL,
 ADD COLUMN surge_end_time TIME NULL,
 ADD COLUMN overtime_penalty_multiplier DECIMAL(3,2) NULL;

ALTER TABLE bookings ADD COLUMN request_key VARCHAR(100) NULL UNIQUE;
