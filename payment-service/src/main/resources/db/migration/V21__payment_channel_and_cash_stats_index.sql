ALTER TABLE payments
    ADD COLUMN payment_channel VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN';

CREATE INDEX idx_payments_merchant_lot_channel
    ON payments (merchant_id, status, parking_lot_id, payment_channel, created_at);

CREATE INDEX idx_cash_checkout_merchant_stats
    ON cash_checkout_receipts (merchant_id, status, parking_lot_id, updated_at);
