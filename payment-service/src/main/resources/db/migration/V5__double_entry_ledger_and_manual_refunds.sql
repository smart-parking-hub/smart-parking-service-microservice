-- =====================================================================
-- V5: Double-Entry Bookkeeping Journals, Daily Settlement & Manual Refunds
-- Target: MySQL 8.4 (compatible with Flyway MySQL)
-- Preserves V1-V4 tables and historical 8% fee rows in wallet_ledger_entries
-- =====================================================================

CREATE TABLE IF NOT EXISTS ledger_journals (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    journal_key VARCHAR(150) NOT NULL UNIQUE,
    journal_type VARCHAR(60) NOT NULL,
    merchant_id BIGINT NULL,
    booking_id BIGINT NULL,
    payment_id BIGINT NULL,
    settlement_date DATE NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'VND',
    total_debit DECIMAL(15, 2) NOT NULL,
    total_credit DECIMAL(15, 2) NOT NULL,
    fee_percent DECIMAL(5, 2) NOT NULL DEFAULT 0.00,
    reversal_of_journal_id BIGINT NULL,
    description VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_journal_merchant (merchant_id),
    INDEX idx_journal_booking (booking_id),
    INDEX idx_journal_payment (payment_id),
    INDEX idx_journal_settlement_date (settlement_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ledger_postings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    journal_id BIGINT NOT NULL,
    account_code VARCHAR(50) NOT NULL,
    merchant_id BIGINT NULL,
    direction VARCHAR(10) NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'VND',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_posting_journal (journal_id),
    INDEX idx_posting_account_merchant (account_code, merchant_id),
    CONSTRAINT fk_ledger_posting_journal FOREIGN KEY (journal_id) REFERENCES ledger_journals(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS settlement_batches (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_key VARCHAR(120) NOT NULL UNIQUE,
    business_date DATE NOT NULL,
    merchant_id BIGINT NOT NULL,
    gross_amount DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    fee_percent DECIMAL(5, 2) NOT NULL DEFAULT 10.00,
    fee_amount DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    net_amount DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    payment_count INT NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'COMPLETED',
    journal_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_settlement_date_merchant (business_date, merchant_id),
    INDEX idx_settlement_date (business_date),
    INDEX idx_settlement_merchant (merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS refund_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    idempotency_key VARCHAR(120) NOT NULL UNIQUE,
    booking_id BIGINT NOT NULL UNIQUE,
    payment_id BIGINT NULL,
    driver_id BIGINT NOT NULL,
    merchant_id BIGINT NULL,
    parking_lot_id BIGINT NULL,
    deposit_amount DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    refund_amount DECIMAL(15, 2) NOT NULL,
    refund_percentage INT NOT NULL DEFAULT 0,
    currency VARCHAR(10) NOT NULL DEFAULT 'VND',
    policy_code VARCHAR(60) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'REQUESTED',
    cancellation_reason VARCHAR(500) NULL,
    transaction_reference VARCHAR(120) NULL,
    evidence_url VARCHAR(1024) NULL,
    merchant_note VARCHAR(500) NULL,
    evidence_submitted_at DATETIME(6) NULL,
    verified_by BIGINT NULL,
    verified_at DATETIME(6) NULL,
    verification_note VARCHAR(500) NULL,
    due_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    INDEX idx_refund_merchant (merchant_id),
    INDEX idx_refund_driver (driver_id),
    INDEX idx_refund_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS processed_events (
    event_id VARCHAR(64) PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NULL,
    processed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 1. Convert historical V2 wallet_ledger_entries into balanced double-entry journals (preserving 8% historical fee)
INSERT INTO ledger_journals (journal_key, journal_type, merchant_id, booking_id, payment_id, currency, total_debit, total_credit, fee_percent, description, created_at)
SELECT
    CONCAT('HIST:WLE:', wle.id),
    'HISTORICAL_SETTLEMENT',
    wle.merchant_id,
    wle.booking_id,
    wle.payment_id,
    'VND',
    wle.gross_amount,
    (wle.net_amount + wle.fee_amount),
    wle.fee_percent,
    wle.note,
    wle.created_at
FROM wallet_ledger_entries wle
WHERE wle.entry_type = 'SETTLEMENT_CREDIT'
  AND wle.gross_amount > 0
  AND NOT EXISTS (
      SELECT 1 FROM ledger_journals lj WHERE lj.journal_key = CONCAT('HIST:WLE:', wle.id)
  );

INSERT INTO ledger_postings (journal_id, account_code, merchant_id, direction, amount, currency, created_at)
SELECT
    lj.id,
    'GATEWAY_CLEARING',
    lj.merchant_id,
    'DEBIT',
    wle.gross_amount,
    'VND',
    lj.created_at
FROM ledger_journals lj
JOIN wallet_ledger_entries wle ON lj.journal_key = CONCAT('HIST:WLE:', wle.id)
WHERE NOT EXISTS (
    SELECT 1 FROM ledger_postings lp WHERE lp.journal_id = lj.id AND lp.account_code = 'GATEWAY_CLEARING'
);

INSERT INTO ledger_postings (journal_id, account_code, merchant_id, direction, amount, currency, created_at)
SELECT
    lj.id,
    'MERCHANT_PAYABLE',
    lj.merchant_id,
    'CREDIT',
    wle.net_amount,
    'VND',
    lj.created_at
FROM ledger_journals lj
JOIN wallet_ledger_entries wle ON lj.journal_key = CONCAT('HIST:WLE:', wle.id)
WHERE wle.net_amount > 0
  AND NOT EXISTS (
      SELECT 1 FROM ledger_postings lp WHERE lp.journal_id = lj.id AND lp.account_code = 'MERCHANT_PAYABLE'
  );

INSERT INTO ledger_postings (journal_id, account_code, merchant_id, direction, amount, currency, created_at)
SELECT
    lj.id,
    'PLATFORM_FEE_REVENUE',
    lj.merchant_id,
    'CREDIT',
    wle.fee_amount,
    'VND',
    lj.created_at
FROM ledger_journals lj
JOIN wallet_ledger_entries wle ON lj.journal_key = CONCAT('HIST:WLE:', wle.id)
WHERE wle.fee_amount > 0
  AND NOT EXISTS (
      SELECT 1 FROM ledger_postings lp WHERE lp.journal_id = lj.id AND lp.account_code = 'PLATFORM_FEE_REVENUE'
  );

-- 2. Reconcile opening balance delta between legacy wallets.balance and historical ledger entries
INSERT INTO ledger_journals (journal_key, journal_type, merchant_id, currency, total_debit, total_credit, fee_percent, description, created_at)
SELECT
    CONCAT('OPENING:MERCHANT:', w.merchant_id),
    'OPENING_BALANCE_RECONCILIATION',
    w.merchant_id,
    COALESCE(w.currency, 'VND'),
    ABS(w.balance - COALESCE(hist.net_sum, 0.00)),
    ABS(w.balance - COALESCE(hist.net_sum, 0.00)),
    0.00,
    CONCAT('Đối soát số dư đầu kỳ Merchant #', w.merchant_id),
    w.created_at
FROM wallets w
LEFT JOIN (
    SELECT lp.merchant_id,
           SUM(CASE WHEN lp.direction = 'CREDIT' THEN lp.amount ELSE -lp.amount END) AS net_sum
    FROM ledger_postings lp
    WHERE lp.account_code = 'MERCHANT_PAYABLE'
    GROUP BY lp.merchant_id
) hist ON hist.merchant_id = w.merchant_id
WHERE (w.balance - COALESCE(hist.net_sum, 0.00)) <> 0
  AND NOT EXISTS (
      SELECT 1 FROM ledger_journals lj WHERE lj.journal_key = CONCAT('OPENING:MERCHANT:', w.merchant_id)
  );

INSERT INTO ledger_postings (journal_id, account_code, merchant_id, direction, amount, currency, created_at)
SELECT
    lj.id,
    'GATEWAY_CLEARING',
    lj.merchant_id,
    CASE WHEN (w.balance - COALESCE(hist.net_sum, 0.00)) > 0 THEN 'DEBIT' ELSE 'CREDIT' END,
    ABS(w.balance - COALESCE(hist.net_sum, 0.00)),
    lj.currency,
    lj.created_at
FROM ledger_journals lj
JOIN wallets w ON lj.journal_key = CONCAT('OPENING:MERCHANT:', w.merchant_id)
LEFT JOIN (
    SELECT lp.merchant_id,
           SUM(CASE WHEN lp.direction = 'CREDIT' THEN lp.amount ELSE -lp.amount END) AS net_sum
    FROM ledger_postings lp
    JOIN ledger_journals j2 ON j2.id = lp.journal_id
    WHERE lp.account_code = 'MERCHANT_PAYABLE'
      AND j2.journal_type <> 'OPENING_BALANCE_RECONCILIATION'
    GROUP BY lp.merchant_id
) hist ON hist.merchant_id = w.merchant_id
WHERE NOT EXISTS (
    SELECT 1 FROM ledger_postings lp2 WHERE lp2.journal_id = lj.id AND lp2.account_code = 'GATEWAY_CLEARING'
);

INSERT INTO ledger_postings (journal_id, account_code, merchant_id, direction, amount, currency, created_at)
SELECT
    lj.id,
    'MERCHANT_PAYABLE',
    lj.merchant_id,
    CASE WHEN (w.balance - COALESCE(hist.net_sum, 0.00)) > 0 THEN 'CREDIT' ELSE 'DEBIT' END,
    ABS(w.balance - COALESCE(hist.net_sum, 0.00)),
    lj.currency,
    lj.created_at
FROM ledger_journals lj
JOIN wallets w ON lj.journal_key = CONCAT('OPENING:MERCHANT:', w.merchant_id)
LEFT JOIN (
    SELECT lp.merchant_id,
           SUM(CASE WHEN lp.direction = 'CREDIT' THEN lp.amount ELSE -lp.amount END) AS net_sum
    FROM ledger_postings lp
    JOIN ledger_journals j2 ON j2.id = lp.journal_id
    WHERE lp.account_code = 'MERCHANT_PAYABLE'
      AND j2.journal_type <> 'OPENING_BALANCE_RECONCILIATION'
    GROUP BY lp.merchant_id
) hist ON hist.merchant_id = w.merchant_id
WHERE NOT EXISTS (
    SELECT 1 FROM ledger_postings lp2 WHERE lp2.journal_id = lj.id AND lp2.account_code = 'MERCHANT_PAYABLE'
);

