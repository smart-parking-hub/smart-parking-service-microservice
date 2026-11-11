CREATE TABLE IF NOT EXISTS dispute_evidence_files (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stored_file_name VARCHAR(120) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    uploader_driver_id BIGINT NOT NULL,
    dispute_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_dispute_evidence_stored_name UNIQUE (stored_file_name),
    INDEX idx_dispute_evidence_dispute (dispute_id),
    INDEX idx_dispute_evidence_driver (uploader_driver_id),
    CONSTRAINT fk_dispute_evidence_dispute FOREIGN KEY (dispute_id) REFERENCES disputes(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

