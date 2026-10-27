ALTER TABLE withdrawal_requests
    ADD COLUMN bank_name VARCHAR(100) NULL AFTER decision_reason,
    ADD COLUMN bank_account_num VARCHAR(50) NULL AFTER bank_name,
    ADD COLUMN account_holder_name VARCHAR(100) NULL AFTER bank_account_num;
