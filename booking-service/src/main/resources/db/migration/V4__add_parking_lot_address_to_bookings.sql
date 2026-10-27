-- Preserve existing bookings while adding the parking lot address snapshot.
ALTER TABLE bookings ADD COLUMN parking_lot_address VARCHAR(255) NULL AFTER parking_lot_name;
