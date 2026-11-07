-- Separate email channel: legacy simulated phone challenges must not verify email.
CREATE TABLE email_otp_challenges (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipient VARCHAR(100) NOT NULL,
    purpose VARCHAR(30) NOT NULL,
    code_hash VARCHAR(100) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6) NULL,
    sent_at DATETIME(6) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    CONSTRAINT uq_email_otp_recipient_purpose UNIQUE (recipient, purpose)
);
ALTER TABLE users ADD COLUMN email_verified_at DATETIME(6) NULL;
