CREATE TABLE slot_reservations (
    booking_id BIGINT NOT NULL PRIMARY KEY,
    parking_lot_id BIGINT NOT NULL,
    slot_group_id BIGINT NOT NULL,
    physical_slot_id VARCHAR(36) NULL,
    publication_id VARCHAR(36) NULL,
    start_time DATETIME(6) NOT NULL,
    end_time DATETIME(6) NOT NULL,
    protected_start DATETIME(6) NOT NULL,
    protected_until DATETIME(6) NOT NULL,
    hold_expires_at DATETIME(6) NULL,
    actual_checkout DATETIME(6) NULL,
    occupation_token VARCHAR(36) NULL,
    import_state VARCHAR(20) NULL,
    state VARCHAR(20) NOT NULL,
    policy_version INT NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_reservation_group FOREIGN KEY (slot_group_id) REFERENCES slots(id),
    CONSTRAINT fk_reservation_lot FOREIGN KEY (parking_lot_id) REFERENCES parking_lots(id)
);
CREATE INDEX idx_reservation_group_time ON slot_reservations(slot_group_id, protected_start, protected_until);
CREATE INDEX idx_reservation_physical_time ON slot_reservations(physical_slot_id, protected_start, protected_until);
CREATE INDEX idx_reservation_state ON slot_reservations(state, updated_at);
