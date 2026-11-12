-- ============================================================
-- V5__add_merchant_account_holder_name.sql
-- SP-S3-PAY-08: Add account_holder_name to merchants table
-- ============================================================

ALTER TABLE merchants
    ADD COLUMN account_holder_name VARCHAR(100) NULL AFTER bank_account_num;
