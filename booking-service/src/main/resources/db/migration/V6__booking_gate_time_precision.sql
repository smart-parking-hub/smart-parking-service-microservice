-- Preserve actual gate timestamps without MySQL rounding sub-second arrivals into the future.
ALTER TABLE bookings
 MODIFY COLUMN check_in_time DATETIME(6) NULL,
 MODIFY COLUMN check_out_time DATETIME(6) NULL;
