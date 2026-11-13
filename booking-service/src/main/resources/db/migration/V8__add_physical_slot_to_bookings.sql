-- ============================================================
-- V8__add_physical_slot_to_bookings.sql
-- Snapshot physical slot ID, physical slot code, and publication ID
-- ============================================================

ALTER TABLE bookings ADD COLUMN physical_slot_id VARCHAR(36) NULL;
ALTER TABLE bookings ADD COLUMN physical_slot_code VARCHAR(50) NULL;
ALTER TABLE bookings ADD COLUMN layout_publication_id VARCHAR(36) NULL;

CREATE INDEX idx_bookings_phys_slot ON bookings (physical_slot_id);
