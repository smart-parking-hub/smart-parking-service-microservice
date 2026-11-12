CREATE TABLE IF NOT EXISTS dispute_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dispute_id BIGINT NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    actor_user_id BIGINT NULL,
    actor_role VARCHAR(30) NOT NULL,
    message TEXT NOT NULL,
    resulting_status VARCHAR(30) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_dispute_history_dispute_created (dispute_id, created_at, id),
    INDEX idx_dispute_history_dispute_event (dispute_id, event_type),
    CONSTRAINT fk_dispute_history_dispute FOREIGN KEY (dispute_id) REFERENCES disputes(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

