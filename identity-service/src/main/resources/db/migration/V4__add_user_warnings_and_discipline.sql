-- ============================================================
-- V4__add_user_warnings_and_discipline.sql
-- Migration for User Warnings & Account Discipline (GAP-11)
-- ============================================================

-- 1. Modify users.status ENUM to include 'SUSPENDED'
ALTER TABLE users 
    MODIFY COLUMN status ENUM('ACTIVE','INACTIVE','BANNED','SUSPENDED') NOT NULL DEFAULT 'ACTIVE';

-- 2. Add warning_count and suspended_until to users table
ALTER TABLE users 
    ADD COLUMN warning_count INT NOT NULL DEFAULT 0,
    ADD COLUMN suspended_until DATETIME(6) NULL;

-- 3. Create user_warnings table
CREATE TABLE IF NOT EXISTS user_warnings (
    id                 BIGINT NOT NULL AUTO_INCREMENT,
    user_id            BIGINT NOT NULL,
    reason             VARCHAR(1000) NOT NULL,
    penalty_type       VARCHAR(50) NOT NULL DEFAULT 'OFFICIAL_WARNING',
    notify_user        BOOLEAN NOT NULL DEFAULT TRUE,
    evidence_url       VARCHAR(500),
    issued_by_admin_id BIGINT NOT NULL,
    created_at         DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_user_warnings_user_id (user_id),
    KEY idx_user_warnings_admin_id (issued_by_admin_id),
    CONSTRAINT FK_user_warnings_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT FK_user_warnings_admin FOREIGN KEY (issued_by_admin_id) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

