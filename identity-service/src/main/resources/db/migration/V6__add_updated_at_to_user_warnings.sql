-- ============================================================
-- V6__add_updated_at_to_user_warnings.sql
-- Add updated_at column to user_warnings for BaseEntity audit
-- ============================================================

ALTER TABLE user_warnings 
    ADD COLUMN updated_at DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6);
