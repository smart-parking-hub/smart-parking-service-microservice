ALTER TABLE merchant_verification_documents
    ADD COLUMN cloudinary_public_id VARCHAR(150) NULL,
    ADD UNIQUE KEY uk_mvd_cloudinary_public_id (cloudinary_public_id);

ALTER TABLE merchant_verification_submissions
    ADD COLUMN ocr_document_id VARCHAR(64) NULL,
    ADD COLUMN ocr_method VARCHAR(24) NULL;

CREATE TABLE merchant_verification_cloudinary_cleanup (
    public_id VARCHAR(150) NOT NULL PRIMARY KEY,
    next_attempt_at DATETIME(6) NOT NULL,
    INDEX idx_mvcc_next_attempt (next_attempt_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
