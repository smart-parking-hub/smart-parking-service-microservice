ALTER TABLE bookings
    ADD COLUMN final_payment_method VARCHAR(20) NULL,
    ADD COLUMN final_payment_reference VARCHAR(80) NULL,
    ADD COLUMN final_payment_amount DECIMAL(10,2) NULL;
