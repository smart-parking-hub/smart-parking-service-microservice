CREATE TABLE wallet_payment_receipts (
    payment_id BIGINT PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    accepted BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_wallet_receipt_booking(booking_id)
);
ALTER TABLE bookings ADD COLUMN refund_channel VARCHAR(30) NULL;
