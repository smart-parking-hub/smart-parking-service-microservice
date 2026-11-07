CREATE TABLE slot_holds (
 booking_id BIGINT PRIMARY KEY,
 slot_id BIGINT NOT NULL,
 released BOOLEAN NOT NULL DEFAULT FALSE,
 CONSTRAINT fk_slot_hold_slot FOREIGN KEY (slot_id) REFERENCES slots(id)
);
