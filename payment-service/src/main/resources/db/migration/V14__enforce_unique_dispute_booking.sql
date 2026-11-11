ALTER TABLE disputes
    ADD CONSTRAINT uk_disputes_booking_id UNIQUE (booking_id);

