-- V2 seeded these eight metadata-only demo images without any binary assets.
-- Remove only unchanged placeholders. Keep CDN images and edited/uploaded records.
DELETE FROM parking_lot_images
WHERE image_url IS NULL AND content_type = 'image/webp' AND is_cover = TRUE
  AND (
    (id = 1 AND parking_lot_id = 1 AND stored_file_name = 'img_lot1_cover.webp' AND original_file_name = 'bai_cat_lai_cong_g_overview.jpg' AND size_bytes = 452100 AND created_at = '2026-01-20 09:00:00' AND updated_at = '2026-01-20 09:00:00')
 OR (id = 2 AND parking_lot_id = 2 AND stored_file_name = 'img_lot2_cover.webp' AND original_file_name = 'bai_linh_trung_xa_lo_ha_noi.jpg' AND size_bytes = 389400 AND created_at = '2026-01-20 10:00:00' AND updated_at = '2026-01-20 10:00:00')
 OR (id = 3 AND parking_lot_id = 4 AND stored_file_name = 'img_lot4_cover.webp' AND original_file_name = 'bai_binh_chanh_ql50.jpg' AND size_bytes = 512300 AND created_at = '2026-01-22 09:30:00' AND updated_at = '2026-01-22 09:30:00')
 OR (id = 4 AND parking_lot_id = 5 AND stored_file_name = 'img_lot5_cover.webp' AND original_file_name = 'bai_tan_tuc_ql1a.jpg' AND size_bytes = 467800 AND created_at = '2026-01-22 14:00:00' AND updated_at = '2026-01-22 14:00:00')
 OR (id = 5 AND parking_lot_id = 8 AND stored_file_name = 'img_lot8_cover.webp' AND original_file_name = 'cang_quoc_te_hiep_phuoc.jpg' AND size_bytes = 623100 AND created_at = '2026-01-24 09:00:00' AND updated_at = '2026-01-24 09:00:00')
 OR (id = 6 AND parking_lot_id = 12 AND stored_file_name = 'img_lot12_cover.webp' AND original_file_name = 'staging_tan_son_nhat.jpg' AND size_bytes = 415600 AND created_at = '2026-01-26 09:30:00' AND updated_at = '2026-01-26 09:30:00')
 OR (id = 7 AND parking_lot_id = 16 AND stored_file_name = 'img_lot16_cover.webp' AND original_file_name = 'song_than_di_an_ql1k.jpg' AND size_bytes = 534200 AND created_at = '2026-01-28 09:00:00' AND updated_at = '2026-01-28 09:00:00')
 OR (id = 8 AND parking_lot_id = 24 AND stored_file_name = 'img_lot24_cover.webp' AND original_file_name = 'cua_ngo_ben_luc_long_an.jpg' AND size_bytes = 489900 AND created_at = '2026-02-01 10:00:00' AND updated_at = '2026-02-01 10:00:00')
  );
