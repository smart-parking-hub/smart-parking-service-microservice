ALTER TABLE disputes ADD COLUMN parking_lot_id BIGINT NULL;
ALTER TABLE disputes ADD COLUMN evidence_urls JSON NULL;
CREATE INDEX idx_disputes_driver_id ON disputes (driver_id, status);

