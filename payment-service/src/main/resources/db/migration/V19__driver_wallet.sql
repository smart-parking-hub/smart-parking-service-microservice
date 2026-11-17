-- Wallets are liabilities to drivers; existing merchant balances and manual refunds are preserved.
CREATE TABLE driver_wallets (
    driver_id BIGINT PRIMARY KEY,
    available_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    held_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    CONSTRAINT ck_driver_wallet_available CHECK (available_amount >= 0),
    CONSTRAINT ck_driver_wallet_held CHECK (held_amount >= 0)
);
ALTER TABLE ledger_journals ADD COLUMN driver_id BIGINT NULL;
ALTER TABLE ledger_postings ADD COLUMN driver_id BIGINT NULL;
CREATE INDEX idx_posting_account_driver ON ledger_postings(account_code, driver_id);
CREATE TABLE driver_wallet_entries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    driver_id BIGINT NOT NULL,
    journal_id BIGINT NOT NULL UNIQUE,
    payment_id BIGINT NULL,
    booking_id BIGINT NULL,
    entry_type VARCHAR(30) NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    held_delta DECIMAL(15,2) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_driver_entry_journal FOREIGN KEY (journal_id) REFERENCES ledger_journals(id),
    INDEX idx_driver_entry_page(driver_id, id)
);
ALTER TABLE refund_requests ADD COLUMN refund_channel VARCHAR(30) NOT NULL DEFAULT 'BANK_MANUAL';
ALTER TABLE payments ADD COLUMN wallet_refund BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_wallet_payment_recovery ON payments(gateway, status, id);
CREATE INDEX idx_wallet_refund_recovery ON refund_requests(refund_channel, status, id);
CREATE INDEX idx_merchant_deposit_page ON payments(merchant_id, payment_type, id);
