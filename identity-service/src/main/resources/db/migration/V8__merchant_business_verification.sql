-- V8__merchant_business_verification.sql
-- Schema for Merchant Business Verification (KYC) Dossiers, Submissions, Documents, Assessment and History

CREATE TABLE IF NOT EXISTS merchant_verification_dossiers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    merchant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    draft_revision BIGINT NOT NULL DEFAULT 1,
    submission_version INT NULL,
    business_registration_number VARCHAR(50) NULL,
    legal_name VARCHAR(255) NULL,
    resubmission_note TEXT NULL,
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT UK_mvd_merchant_id UNIQUE (merchant_id),
    CONSTRAINT FK_mvd_merchant FOREIGN KEY (merchant_id) REFERENCES merchants (id) ON DELETE CASCADE,
    CONSTRAINT FK_mvd_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_mvd_status ON merchant_verification_dossiers (status);
CREATE INDEX idx_mvd_user_id ON merchant_verification_dossiers (user_id);

CREATE TABLE IF NOT EXISTS merchant_verification_documents (
    id VARCHAR(64) NOT NULL,
    dossier_id BIGINT NOT NULL,
    document_type VARCHAR(50) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256_hash VARCHAR(64) NOT NULL,
    uploaded_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    in_draft BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    CONSTRAINT FK_mvd_doc_dossier FOREIGN KEY (dossier_id) REFERENCES merchant_verification_dossiers (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_mvd_doc_dossier_type ON merchant_verification_documents (dossier_id, document_type);

CREATE TABLE IF NOT EXISTS merchant_verification_document_bytes (
    document_id VARCHAR(64) NOT NULL,
    file_bytes LONGBLOB NOT NULL,
    PRIMARY KEY (document_id),
    CONSTRAINT FK_mvdb_doc FOREIGN KEY (document_id) REFERENCES merchant_verification_documents (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS merchant_verification_submissions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    dossier_id BIGINT NOT NULL,
    submission_version INT NOT NULL,
    business_registration_number VARCHAR(50) NOT NULL,
    legal_name VARCHAR(255) NOT NULL,
    resubmission_note TEXT NULL,
    submitted_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    assessment_status VARCHAR(30) NOT NULL DEFAULT 'NOT_STARTED',
    assessment_checked_at DATETIME(6) NULL,
    decision_action VARCHAR(30) NULL,
    decision_actor VARCHAR(100) NULL,
    decision_note TEXT NULL,
    decision_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT UK_mvs_dossier_version UNIQUE (dossier_id, submission_version),
    CONSTRAINT FK_mvs_dossier FOREIGN KEY (dossier_id) REFERENCES merchant_verification_dossiers (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_mvs_submitted_at ON merchant_verification_submissions (submitted_at);

CREATE TABLE IF NOT EXISTS merchant_verification_submission_documents (
    submission_id BIGINT NOT NULL,
    document_id VARCHAR(64) NOT NULL,
    PRIMARY KEY (submission_id, document_id),
    CONSTRAINT FK_mvsd_submission FOREIGN KEY (submission_id) REFERENCES merchant_verification_submissions (id) ON DELETE CASCADE,
    CONSTRAINT FK_mvsd_document FOREIGN KEY (document_id) REFERENCES merchant_verification_documents (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS merchant_verification_assessment_checks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    submission_id BIGINT NOT NULL,
    code VARCHAR(50) NOT NULL,
    result VARCHAR(20) NOT NULL,
    document_type VARCHAR(50) NULL,
    field VARCHAR(50) NULL,
    declared_value VARCHAR(255) NULL,
    extracted_value VARCHAR(255) NULL,
    message VARCHAR(500) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT FK_mvac_submission FOREIGN KEY (submission_id) REFERENCES merchant_verification_submissions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_mvac_submission ON merchant_verification_assessment_checks (submission_id);

CREATE TABLE IF NOT EXISTS merchant_verification_requested_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    submission_id BIGINT NOT NULL,
    item_key VARCHAR(50) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT FK_mvri_submission FOREIGN KEY (submission_id) REFERENCES merchant_verification_submissions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_mvri_submission ON merchant_verification_requested_items (submission_id);

CREATE TABLE IF NOT EXISTS merchant_verification_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    dossier_id BIGINT NOT NULL,
    submission_version INT NOT NULL,
    action VARCHAR(30) NOT NULL,
    actor VARCHAR(100) NULL,
    internal_note TEXT NULL,
    occurred_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT FK_mvh_dossier FOREIGN KEY (dossier_id) REFERENCES merchant_verification_dossiers (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_mvh_dossier ON merchant_verification_history (dossier_id);
