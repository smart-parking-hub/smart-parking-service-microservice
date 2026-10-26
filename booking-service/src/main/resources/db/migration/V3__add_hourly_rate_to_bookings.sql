-- Add hourly_rate column to bookings to record dynamic pricing at reservation time
ALTER TABLE bookings ADD COLUMN hourly_rate DECIMAL(10, 2) NULL AFTER total_amount;
