-- V5 carries forward the available wallet balance, including legacy holds,
-- but does not populate the separate withdrawal-hold account. Preserve that
-- liability before the new application can approve or reject old requests.
-- No wallet balance, request status or historical wallet entry is changed.
INSERT INTO ledger_journals
    (journal_key, journal_type, merchant_id, currency, total_debit, total_credit, fee_percent, description, created_at)
SELECT CONCAT('LEGACY_WITHDRAWAL_HOLD:MERCHANT:', w.merchant_id), 'LEGACY_WITHDRAWAL_HOLD', w.merchant_id,
       'VND', w.amount - COALESCE(h.amount, 0), w.amount - COALESCE(h.amount, 0), 0,
       CONCAT('Opening missing withdrawal liability for merchant #', w.merchant_id), CURRENT_TIMESTAMP(6)
FROM (SELECT merchant_id, SUM(amount) amount FROM withdrawal_requests WHERE status='PENDING' GROUP BY merchant_id) w
LEFT JOIN (SELECT merchant_id, SUM(CASE WHEN direction='CREDIT' THEN amount ELSE -amount END) amount
           FROM ledger_postings WHERE account_code='MERCHANT_WITHDRAWAL_HOLD' GROUP BY merchant_id) h
ON h.merchant_id=w.merchant_id
WHERE w.amount > COALESCE(h.amount, 0)
  AND NOT EXISTS (SELECT 1 FROM ledger_journals j WHERE j.journal_key = CONCAT('LEGACY_WITHDRAWAL_HOLD:MERCHANT:', w.merchant_id));

INSERT INTO ledger_postings (journal_id, account_code, merchant_id, direction, amount, currency, created_at)
SELECT j.id, 'GATEWAY_CLEARING', j.merchant_id, 'DEBIT', j.total_debit, j.currency, j.created_at
FROM ledger_journals j WHERE j.journal_type = 'LEGACY_WITHDRAWAL_HOLD'
  AND NOT EXISTS (SELECT 1 FROM ledger_postings p WHERE p.journal_id = j.id AND p.account_code = 'GATEWAY_CLEARING');

INSERT INTO ledger_postings (journal_id, account_code, merchant_id, direction, amount, currency, created_at)
SELECT j.id, 'MERCHANT_WITHDRAWAL_HOLD', j.merchant_id, 'CREDIT', j.total_credit, j.currency, j.created_at
FROM ledger_journals j WHERE j.journal_type = 'LEGACY_WITHDRAWAL_HOLD'
  AND NOT EXISTS (SELECT 1 FROM ledger_postings p WHERE p.journal_id = j.id AND p.account_code = 'MERCHANT_WITHDRAWAL_HOLD');
