-- ============================================================
-- V11__add_description_to_parking_lots.sql
-- Add description column to parking_lots for AI generated & merchant custom operating guidelines
-- ============================================================

ALTER TABLE parking_lots
    ADD COLUMN description TEXT NULL AFTER review_reason;

-- Seed rich AI operating profiles for key demo lots and pending review lots
UPDATE parking_lots
SET description = '### Khả năng tiếp vận & Quy mô\nDepot chuyên dụng tiếp nhận đầu kéo container và xe tải nặng 3-4 chân, mặt bãi đầm nén bê tông cốt thép tải trọng trục 60T, hệ thống cắm điện lạnh Reefer 380V hoạt động liên tục 24/7.\n\n### Nội quy PCCC & An toàn\nNghiêm cấm sang chiết dầu trong khuôn viên depot. Bãi trang bị sẵn 08 họng trụ cứu hỏa D65 đấu nối nguồn nước áp lực cao cùng xe chữa cháy cơ động túc trực bảo vệ hàng hóa. Thời gian giữ chỗ hợp lệ tối đa 10 phút.\n\n### Hướng dẫn phân luồng & Lưu thông\nTài xế đi từ trục đường chính tiếp cận cổng, duy trì vận tốc < 10km/h khi qua cổng nhận diện ALPR để camera quét biển số và mở barie tự động.'
WHERE id IN (1, 2, 25, 26, 27) AND description IS NULL;
