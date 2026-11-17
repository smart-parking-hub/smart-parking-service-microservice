-- Merchant-held cash is not a gateway receipt and must not enter payments/escrow.
CREATE TABLE cash_checkout_receipts (
    booking_id BIGINT PRIMARY KEY,
    merchant_id BIGINT NOT NULL,
    parking_lot_id BIGINT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    INDEX idx_cash_checkout_recovery(status, booking_id)
);
