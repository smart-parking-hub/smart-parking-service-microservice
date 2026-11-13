CREATE TABLE admin_event_outbox (
    event_id VARCHAR(100) PRIMARY KEY,
    topic_name VARCHAR(100) NOT NULL,
    partition_key VARCHAR(100) NOT NULL,
    payload LONGTEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    next_attempt_at TIMESTAMP(6) NOT NULL,
    published_at TIMESTAMP(6) NULL,
    INDEX idx_admin_event_pending (status, next_attempt_at)
);

ALTER TABLE parking_lots ADD COLUMN review_submission_version BIGINT NOT NULL DEFAULT 1;
