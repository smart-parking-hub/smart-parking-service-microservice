-- ============================================================
-- V9: Add image_url to parking_lot_images for Cloudinary / CDN support
-- ============================================================

ALTER TABLE parking_lot_images
    MODIFY COLUMN stored_file_name VARCHAR(255) NOT NULL,
    ADD COLUMN image_url VARCHAR(500) NULL AFTER stored_file_name;
