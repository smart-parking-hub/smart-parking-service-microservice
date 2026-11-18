ALTER TABLE merchant_verification_submissions
    ADD COLUMN assessment_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN assessment_lease_until DATETIME(6) NULL,
    ADD INDEX idx_mvs_assessment_queue (assessment_status, assessment_lease_until, submitted_at);
