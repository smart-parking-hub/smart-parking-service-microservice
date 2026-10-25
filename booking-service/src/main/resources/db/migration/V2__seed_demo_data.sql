-- ============================================================
-- V2__seed_demo_data.sql
-- Booking Service Seed Data (220 Bookings, 30 Queues, 85 Reviews)
-- Covers ALL Booking Statuses: PENDING_PAYMENT, PENDING_CHECKIN, PARKING, 
-- OVERTIME, AWAITING_FINAL_PAYMENT, COMPLETED, CANCELLED, EXPIRED, REVIEWED
-- ============================================================

-- 1. SEED BOOKINGS (Batch 1: Active, Pending, Overtime, In-Progress Bookings)
INSERT IGNORE INTO bookings (id, booking_code, driver_id, vehicle_id, parking_lot_id, merchant_id, vehicle_type, vehicle_plate, parking_lot_name, status, advance_deposit, total_amount, expected_check_in_time, expected_check_out_time, expires_at, check_in_time, check_out_time, created_at, updated_at) VALUES
-- PENDING_PAYMENT (Giữ chỗ 15 phút, chưa thanh toán cọc)
(1, 'BK-20260301-001', 1, 1, 1, 1, 'CONTAINER', '51C-482.91', 'Bãi Đỗ Xe Container Cát Lái - Cổng G', 'PENDING_PAYMENT', 150000.00, 440000.00, '2026-03-01 14:00:00', '2026-03-01 22:00:00', '2026-03-01 13:15:00', NULL, NULL, '2026-03-01 13:00:00', '2026-03-01 13:00:00'),
(2, 'BK-20260301-002', 2, 2, 4, 2, 'CONTAINER', '50H-192.83', 'Bãi Xe Tải & Container Bình Chánh - QL.50', 'PENDING_PAYMENT', 120000.00, 384000.00, '2026-03-01 15:30:00', '2026-03-01 23:30:00', '2026-03-01 14:45:00', NULL, NULL, '2026-03-01 14:30:00', '2026-03-01 14:30:00'),
(3, 'BK-20260301-003', 3, 3, 8, 3, 'CONTAINER', '60C-582.10', 'Bãi Container Cảng Quốc Tế Hiệp Phước', 'PENDING_PAYMENT', 150000.00, 520000.00, '2026-03-01 16:00:00', '2026-03-02 02:00:00', '2026-03-01 15:15:00', NULL, NULL, '2026-03-01 15:00:00', '2026-03-01 15:00:00'),
(4, 'BK-20260301-004', 11, 11, 2, 1, 'HEAVY_TRUCK', '51C-592.83', 'Bãi Xe Vận Tải Thủ Đức - Xa Lộ Hà Nội', 'PENDING_PAYMENT', 100000.00, 304000.00, '2026-03-01 17:00:00', '2026-03-02 01:00:00', '2026-03-01 16:15:00', NULL, NULL, '2026-03-01 16:00:00', '2026-03-01 16:00:00'),
(5, 'BK-20260301-005', 21, 21, 12, 4, 'LIGHT_TRUCK', '51D-283.94', 'Bãi Staging Sân Bay Quốc Tế Tân Sơn Nhất', 'PENDING_PAYMENT', 80000.00, 224000.00, '2026-03-01 18:00:00', '2026-03-02 02:00:00', '2026-03-01 17:15:00', NULL, NULL, '2026-03-01 17:00:00', '2026-03-01 17:00:00'),

-- PENDING_CHECKIN (Đã thanh toán cọc thành công, đang trên đường đến bãi)
(6, 'BK-20260301-006', 4, 4, 10, 3, 'CONTAINER', '61C-739.24', 'Bãi Staging Xe Container Nhơn Trạch - CT.01', 'PENDING_CHECKIN', 150000.00, 550000.00, '2026-03-01 18:30:00', '2026-03-02 04:30:00', '2026-03-01 19:30:00', NULL, NULL, '2026-03-01 12:00:00', '2026-03-01 12:10:00'),
(7, 'BK-20260301-007', 5, 5, 1, 1, 'CONTAINER', '62C-381.90', 'Bãi Đỗ Xe Container Cát Lái - Cổng G', 'PENDING_CHECKIN', 150000.00, 440000.00, '2026-03-01 19:00:00', '2026-03-02 03:00:00', '2026-03-01 20:00:00', NULL, NULL, '2026-03-01 13:00:00', '2026-03-01 13:08:00'),
(8, 'BK-20260301-008', 12, 12, 5, 2, 'HEAVY_TRUCK', '51C-681.94', 'Bãi Trung Chuyển Hàng Hóa Tân Túc - QL.1A', 'PENDING_CHECKIN', 100000.00, 288000.00, '2026-03-01 20:00:00', '2026-03-02 04:00:00', '2026-03-01 21:00:00', NULL, NULL, '2026-03-01 14:00:00', '2026-03-01 14:05:00'),
(9, 'BK-20260301-009', 22, 22, 3, 1, 'LIGHT_TRUCK', '51D-394.05', 'Bãi Xe Tải Trung Chuyển Lê Văn Việt', 'PENDING_CHECKIN', 60000.00, 144000.00, '2026-03-01 21:00:00', '2026-03-02 05:00:00', '2026-03-01 22:00:00', NULL, NULL, '2026-03-01 15:30:00', '2026-03-01 15:35:00'),
(10, 'BK-20260301-010', 13, 13, 16, 5, 'HEAVY_TRUCK', '60C-492.05', 'Bãi Xe Vận Tải Sóng Thần - QL.1K', 'PENDING_CHECKIN', 100000.00, 350000.00, '2026-03-01 22:00:00', '2026-03-02 06:00:00', '2026-03-01 23:00:00', NULL, NULL, '2026-03-01 16:00:00', '2026-03-01 16:06:00'),

-- PARKING (Xe đang đỗ trong bãi thực tế)
(11, 'BK-20260301-011', 6, 6, 1, 1, 'CONTAINER', '72C-294.15', 'Bãi Đỗ Xe Container Cát Lái - Cổng G', 'PARKING', 150000.00, 440000.00, '2026-03-01 08:00:00', '2026-03-01 18:00:00', '2026-03-01 09:00:00', '2026-03-01 08:05:00', NULL, '2026-03-01 06:00:00', '2026-03-01 08:05:00'),
(12, 'BK-20260301-012', 7, 7, 8, 3, 'CONTAINER', '51D-839.42', 'Bãi Container Cảng Quốc Tế Hiệp Phước', 'PARKING', 150000.00, 520000.00, '2026-03-01 09:00:00', '2026-03-01 19:00:00', '2026-03-01 10:00:00', '2026-03-01 08:55:00', NULL, '2026-03-01 07:00:00', '2026-03-01 08:55:00'),
(13, 'BK-20260301-013', 14, 14, 2, 1, 'HEAVY_TRUCK', '61C-384.16', 'Bãi Xe Vận Tải Thủ Đức - Xa Lộ Hà Nội', 'PARKING', 100000.00, 304000.00, '2026-03-01 10:00:00', '2026-03-01 18:00:00', '2026-03-01 11:00:00', '2026-03-01 10:02:00', NULL, '2026-03-01 08:00:00', '2026-03-01 10:02:00'),
(14, 'BK-20260301-014', 23, 23, 6, 2, 'LIGHT_TRUCK', '50H-405.16', 'Bãi Đỗ Xe Tải Bình Tân - Võ Văn Kiệt', 'PARKING', 60000.00, 160000.00, '2026-03-01 11:00:00', '2026-03-01 19:00:00', '2026-03-01 12:00:00', '2026-03-01 11:10:00', NULL, '2026-03-01 09:30:00', '2026-03-01 11:10:00'),
(15, 'BK-20260301-015', 8, 8, 18, 5, 'CONTAINER', '50H-928.37', 'Bãi Đỗ Xe Cao Tốc Mỹ Phước - Tân Vạn', 'PARKING', 150000.00, 460000.00, '2026-03-01 11:30:00', '2026-03-01 21:30:00', '2026-03-01 12:30:00', '2026-03-01 11:28:00', NULL, '2026-03-01 10:00:00', '2026-03-01 11:28:00'),
(16, 'BK-20260301-016', 15, 15, 5, 2, 'HEAVY_TRUCK', '62C-719.27', 'Bãi Trung Chuyển Hàng Hóa Tân Túc - QL.1A', 'PARKING', 100000.00, 288000.00, '2026-03-01 12:00:00', '2026-03-01 20:00:00', '2026-03-01 13:00:00', '2026-03-01 12:05:00', NULL, '2026-03-01 10:30:00', '2026-03-01 12:05:00'),
(17, 'BK-20260301-017', 24, 24, 14, 4, 'LIGHT_TRUCK', '60C-516.27', 'Bãi Tiếp Vận Nông Sản Hóc Môn - QL.22', 'PARKING', 50000.00, 144000.00, '2026-03-01 12:30:00', '2026-03-01 20:30:00', '2026-03-01 13:30:00', '2026-03-01 12:35:00', NULL, '2026-03-01 11:00:00', '2026-03-01 12:35:00'),

-- OVERTIME (Đỗ quá giờ dự kiến ban đầu, đang tính phí phạt)
(18, 'BK-20260301-018', 9, 9, 1, 1, 'CONTAINER', '60C-619.48', 'Bãi Đỗ Xe Container Cát Lái - Cổng G', 'OVERTIME', 150000.00, 650000.00, '2026-03-01 04:00:00', '2026-03-01 12:00:00', '2026-03-01 05:00:00', '2026-03-01 04:10:00', NULL, '2026-03-01 02:00:00', '2026-03-01 12:05:00'),
(19, 'BK-20260301-019', 16, 16, 16, 5, 'HEAVY_TRUCK', '51D-472.38', 'Bãi Xe Vận Tải Sóng Thần - QL.1K', 'OVERTIME', 100000.00, 480000.00, '2026-03-01 05:00:00', '2026-03-01 13:00:00', '2026-03-01 06:00:00', '2026-03-01 05:05:00', NULL, '2026-03-01 03:00:00', '2026-03-01 13:10:00'),
(20, 'BK-20260301-020', 25, 25, 11, 3, 'LIGHT_TRUCK', '61C-627.38', 'Bãi Xe Trung Chuyển Nguyễn Văn Linh - Quận 7', 'OVERTIME', 60000.00, 260000.00, '2026-03-01 06:00:00', '2026-03-01 12:00:00', '2026-03-01 07:00:00', '2026-03-01 06:15:00', NULL, '2026-03-01 04:00:00', '2026-03-01 12:15:00'),

-- AWAITING_FINAL_PAYMENT (Đã check-out tại barie, chờ tài xế quét mã trả phần tiền còn lại)
(21, 'BK-20260301-021', 10, 10, 19, 6, 'CONTAINER', '61C-847.19', 'Bãi Tiếp Vận ICD Phú Hữu - Đường Gò Cát', 'AWAITING_FINAL_PAYMENT', 150000.00, 400000.00, '2026-03-01 06:00:00', '2026-03-01 14:00:00', '2026-03-01 07:00:00', '2026-03-01 06:08:00', '2026-03-01 14:02:00', '2026-03-01 04:30:00', '2026-03-01 14:02:00'),
(22, 'BK-20260301-022', 17, 17, 24, 9, 'HEAVY_TRUCK', '50H-381.49', 'Bãi Đỗ Xe Cửa Ngõ Miền Tây - Bến Lức Long An', 'AWAITING_FINAL_PAYMENT', 100000.00, 256000.00, '2026-03-01 07:00:00', '2026-03-01 15:00:00', '2026-03-01 08:00:00', '2026-03-01 07:12:00', '2026-03-01 15:05:00', '2026-03-01 05:00:00', '2026-03-01 15:05:00'),

-- CANCELLED (Tài xế chủ động hủy trước giờ hẹn)
(23, 'BK-20260301-023', 18, 18, 2, 1, 'HEAVY_TRUCK', '60C-829.50', 'Bãi Xe Vận Tải Thủ Đức - Xa Lộ Hà Nội', 'CANCELLED', 100000.00, 0.00, '2026-03-01 14:00:00', '2026-03-01 20:00:00', '2026-03-01 15:00:00', NULL, NULL, '2026-03-01 10:00:00', '2026-03-01 11:30:00'),
(24, 'BK-20260301-024', 19, 19, 17, 5, 'HEAVY_TRUCK', '61C-938.61', 'Bãi Logistics Thuận An - Đại Lộ Bình Dương', 'CANCELLED', 100000.00, 0.00, '2026-03-01 16:00:00', '2026-03-01 22:00:00', '2026-03-01 17:00:00', NULL, NULL, '2026-03-01 11:00:00', '2026-03-01 12:45:00'),

-- EXPIRED (Hết hạn giữ chỗ không thanh toán hoặc quá giờ hẹn mà không đến)
(25, 'BK-20260301-025', 20, 20, 4, 2, 'HEAVY_TRUCK', '72C-548.72', 'Bãi Xe Tải & Container Bình Chánh - QL.50', 'EXPIRED', 0.00, 0.00, '2026-03-01 08:00:00', '2026-03-01 16:00:00', '2026-03-01 08:15:00', NULL, NULL, '2026-03-01 08:00:00', '2026-03-01 08:16:00'),
(26, 'BK-20260301-026', 1, 26, 12, 4, 'LIGHT_TRUCK', '51D-738.49', 'Bãi Staging Sân Bay Quốc Tế Tân Sơn Nhất', 'EXPIRED', 0.00, 0.00, '2026-03-01 09:00:00', '2026-03-01 15:00:00', '2026-03-01 09:15:00', NULL, NULL, '2026-03-01 09:00:00', '2026-03-01 09:16:00');

-- 2. SEED HISTORICAL COMPLETED BOOKINGS (Bookings 27 to 220: Vòng lặp lịch sử từ tháng 01 đến cuối tháng 02/2026)
-- Tạo 194 đơn đặt chỗ hoàn tất trải đều các bãi đỗ, các tài xế và các loại xe
INSERT IGNORE INTO bookings (id, booking_code, driver_id, vehicle_id, parking_lot_id, merchant_id, vehicle_type, vehicle_plate, parking_lot_name, status, advance_deposit, total_amount, expected_check_in_time, expected_check_out_time, expires_at, check_in_time, check_out_time, created_at, updated_at)
SELECT 
    n + 26 AS id,
    CONCAT('BK-202602-', LPAD(n, 3, '0')) AS booking_code,
    ((n - 1) % 23) + 1 AS driver_id,
    ((n - 1) % 43) + 1 AS vehicle_id,
    ((n - 1) % 24) + 1 AS parking_lot_id,
    (((n - 1) % 24) % 10) + 1 AS merchant_id,
    CASE ((n - 1) % 5)
        WHEN 0 THEN 'CONTAINER'
        WHEN 1 THEN 'HEAVY_TRUCK'
        WHEN 2 THEN 'LIGHT_TRUCK'
        WHEN 3 THEN 'BUS_COACH'
        ELSE 'SEDAN_SUV'
    END AS vehicle_type,
    CONCAT('51C-', LPAD(100 + n, 3, '0'), '.', LPAD(20 + n, 2, '0')) AS vehicle_plate,
    CASE (((n - 1) % 24) + 1)
        WHEN 1 THEN 'Bãi Đỗ Xe Container Cát Lái - Cổng G'
        WHEN 2 THEN 'Bãi Xe Vận Tải Thủ Đức - Xa Lộ Hà Nội'
        WHEN 3 THEN 'Bãi Xe Tải Trung Chuyển Lê Văn Việt'
        WHEN 4 THEN 'Bãi Xe Tải & Container Bình Chánh - QL.50'
        WHEN 5 THEN 'Bãi Trung Chuyển Hàng Hóa Tân Túc - QL.1A'
        WHEN 6 THEN 'Bãi Đỗ Xe Tải Bình Tân - Võ Văn Kiệt'
        WHEN 7 THEN 'Bãi Xe Đêm Cửa Ngõ Miền Tây - Vĩnh Lộc'
        WHEN 8 THEN 'Bãi Container Cảng Quốc Tế Hiệp Phước'
        WHEN 9 THEN 'Bãi Xe Vận Tải Nhà Bè - Nguyễn Văn Tạo'
        WHEN 10 THEN 'Bãi Staging Xe Container Nhơn Trạch - CT.01'
        WHEN 11 THEN 'Bãi Xe Trung Chuyển Nguyễn Văn Linh - Quận 7'
        WHEN 12 THEN 'Bãi Staging Sân Bay Quốc Tế Tân Sơn Nhất'
        WHEN 13 THEN 'Bãi Xe Tải Nhẹ Cộng Hòa - Tân Bình'
        WHEN 14 THEN 'Bãi Tiếp Vận Nông Sản Hóc Môn - QL.22'
        WHEN 15 THEN 'Bãi Đỗ Xe Trung Tâm Quận 4 - Nguyễn Tất Thành'
        WHEN 16 THEN 'Bãi Xe Vận Tải Sóng Thần - QL.1K'
        WHEN 17 THEN 'Bãi Logistics Thuận An - Đại Lộ Bình Dương'
        WHEN 18 THEN 'Bãi Đỗ Xe Cao Tốc Mỹ Phước - Tân Vạn'
        WHEN 19 THEN 'Bãi Tiếp Vận ICD Phú Hữu - Đường Gò Cát'
        WHEN 20 THEN 'Bãi Xe Vành Đai 2 - Phú Hữu'
        WHEN 21 THEN 'Bãi Chờ Xe Đêm Đồng Văn Cống - Cát Lái'
        WHEN 22 THEN 'Bãi Xe Chuyên Dùng Cảng Tân Cảng'
        WHEN 23 THEN 'Bãi Đỗ Xe Liên Tỉnh Sóng Thần - DT743'
        ELSE 'Bãi Đỗ Xe Cửa Ngõ Miền Tây - Bến Lức Long An'
    END AS parking_lot_name,
    'COMPLETED' AS status,
    100000.00 AS advance_deposit,
    (200000.00 + (n * 3500.00)) AS total_amount,
    DATE_ADD('2026-02-01 08:00:00', INTERVAL (n * 3) HOUR) AS expected_check_in_time,
    DATE_ADD('2026-02-01 16:00:00', INTERVAL (n * 3) HOUR) AS expected_check_out_time,
    DATE_ADD('2026-02-01 09:00:00', INTERVAL (n * 3) HOUR) AS expires_at,
    DATE_ADD('2026-02-01 08:05:00', INTERVAL (n * 3) HOUR) AS check_in_time,
    DATE_ADD('2026-02-01 15:55:00', INTERVAL (n * 3) HOUR) AS check_out_time,
    DATE_ADD('2026-02-01 06:00:00', INTERVAL (n * 3) HOUR) AS created_at,
    DATE_ADD('2026-02-01 16:00:00', INTERVAL (n * 3) HOUR) AS updated_at
FROM (
    -- Sequence generator 1 to 194
    SELECT t4.i*1000 + t3.i*100 + t2.i*10 + t1.i + 1 AS n
    FROM (SELECT 0 AS i UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t1,
         (SELECT 0 AS i UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t2,
         (SELECT 0 AS i UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t3,
         (SELECT 0 AS i UNION SELECT 1) t4
) seq
WHERE n <= 194;

-- 3. SEED QUEUES (30 records covering all Queue statuses: WAITING, NOTIFIED, CONVERTED, EXPIRED, CANCELLED)
INSERT IGNORE INTO queues (id, driver_id, vehicle_id, parking_lot_id, vehicle_type, status, joined_at, response_deadline, created_at, updated_at) VALUES
-- WAITING (Đang trong hàng đợi chờ lượt trống tại bãi)
(1, 1, 1, 4, 'CONTAINER', 'WAITING', '2026-03-01 13:00:00', NULL, '2026-03-01 13:00:00', '2026-03-01 13:00:00'),
(2, 2, 2, 4, 'CONTAINER', 'WAITING', '2026-03-01 13:10:00', NULL, '2026-03-01 13:10:00', '2026-03-01 13:10:00'),
(3, 3, 3, 4, 'CONTAINER', 'WAITING', '2026-03-01 13:25:00', NULL, '2026-03-01 13:25:00', '2026-03-01 13:25:00'),
(4, 21, 21, 12, 'LIGHT_TRUCK', 'WAITING', '2026-03-01 13:30:00', NULL, '2026-03-01 13:30:00', '2026-03-01 13:30:00'),
(5, 22, 22, 12, 'LIGHT_TRUCK', 'WAITING', '2026-03-01 13:45:00', NULL, '2026-03-01 13:45:00', '2026-03-01 13:45:00'),
(6, 23, 23, 12, 'LIGHT_TRUCK', 'WAITING', '2026-03-01 14:00:00', NULL, '2026-03-01 14:00:00', '2026-03-01 14:00:00'),

-- NOTIFIED (Hệ thống đã phát hiện slot trống và gửi thông báo mời tài xế xác nhận trong 15p)
(7, 4, 4, 1, 'CONTAINER', 'NOTIFIED', '2026-03-01 11:30:00', '2026-03-01 11:45:00', '2026-03-01 11:30:00', '2026-03-01 11:30:00'),
(8, 5, 5, 1, 'CONTAINER', 'NOTIFIED', '2026-03-01 11:35:00', '2026-03-01 11:50:00', '2026-03-01 11:35:00', '2026-03-01 11:35:00'),
(9, 24, 24, 15, 'LIGHT_TRUCK', 'NOTIFIED', '2026-03-01 12:00:00', '2026-03-01 12:15:00', '2026-03-01 12:00:00', '2026-03-01 12:00:00'),

-- CONVERTED (Tài xế đã bấm xác nhận thành công và chuyển thành booking chính thức)
(10, 6, 6, 1, 'CONTAINER', 'CONVERTED', '2026-02-28 08:00:00', '2026-02-28 08:15:00', '2026-02-28 08:00:00', '2026-02-28 08:10:00'),
(11, 7, 7, 12, 'LIGHT_TRUCK', 'CONVERTED', '2026-02-28 09:00:00', '2026-02-28 09:15:00', '2026-02-28 09:00:00', '2026-02-28 09:05:00'),
(12, 8, 8, 4, 'CONTAINER', 'CONVERTED', '2026-02-28 10:00:00', '2026-02-28 10:15:00', '2026-02-28 10:00:00', '2026-02-28 10:12:00'),
(13, 9, 9, 16, 'HEAVY_TRUCK', 'CONVERTED', '2026-02-28 11:00:00', '2026-02-28 11:15:00', '2026-02-28 11:00:00', '2026-02-28 11:08:00'),
(14, 10, 10, 8, 'CONTAINER', 'CONVERTED', '2026-02-28 13:00:00', '2026-02-28 13:15:00', '2026-02-28 13:00:00', '2026-02-28 13:09:00'),

-- EXPIRED (Hết thời hạn 15p mà tài xế không phản hồi -> Bỏ qua cho người tiếp theo)
(15, 11, 11, 1, 'CONTAINER', 'EXPIRED', '2026-02-27 14:00:00', '2026-02-27 14:15:00', '2026-02-27 14:00:00', '2026-02-27 14:16:00'),
(16, 12, 12, 12, 'LIGHT_TRUCK', 'EXPIRED', '2026-02-27 15:00:00', '2026-02-27 15:15:00', '2026-02-27 15:00:00', '2026-02-27 15:16:00'),
(17, 13, 13, 4, 'CONTAINER', 'EXPIRED', '2026-02-27 16:00:00', '2026-02-27 16:15:00', '2026-02-27 16:00:00', '2026-02-27 16:16:00'),

-- CANCELLED (Tài xế chủ động rời khỏi hàng đợi)
(18, 14, 14, 1, 'CONTAINER', 'CANCELLED', '2026-02-26 09:00:00', NULL, '2026-02-26 09:00:00', '2026-02-26 09:30:00'),
(19, 15, 15, 12, 'LIGHT_TRUCK', 'CANCELLED', '2026-02-26 10:00:00', NULL, '2026-02-26 10:00:00', '2026-02-26 10:45:00'),
(20, 16, 16, 16, 'HEAVY_TRUCK', 'CANCELLED', '2026-02-26 11:00:00', NULL, '2026-02-26 11:00:00', '2026-02-26 11:20:00'),
(21, 17, 17, 8, 'CONTAINER', 'WAITING', '2026-03-01 14:10:00', NULL, '2026-03-01 14:10:00', '2026-03-01 14:10:00'),
(22, 18, 18, 8, 'CONTAINER', 'WAITING', '2026-03-01 14:15:00', NULL, '2026-03-01 14:15:00', '2026-03-01 14:15:00'),
(23, 19, 19, 10, 'CONTAINER', 'WAITING', '2026-03-01 14:20:00', NULL, '2026-03-01 14:20:00', '2026-03-01 14:20:00'),
(24, 20, 20, 10, 'CONTAINER', 'WAITING', '2026-03-01 14:30:00', NULL, '2026-03-01 14:30:00', '2026-03-01 14:30:00'),
(25, 1, 26, 14, 'LIGHT_TRUCK', 'WAITING', '2026-03-01 14:35:00', NULL, '2026-03-01 14:35:00', '2026-03-01 14:35:00'),
(26, 2, 27, 14, 'LIGHT_TRUCK', 'WAITING', '2026-03-01 14:40:00', NULL, '2026-03-01 14:40:00', '2026-03-01 14:40:00'),
(27, 3, 28, 14, 'LIGHT_TRUCK', 'WAITING', '2026-03-01 14:45:00', NULL, '2026-03-01 14:45:00', '2026-03-01 14:45:00'),
(28, 4, 29, 14, 'LIGHT_TRUCK', 'WAITING', '2026-03-01 14:50:00', NULL, '2026-03-01 14:50:00', '2026-03-01 14:50:00'),
(29, 5, 30, 14, 'LIGHT_TRUCK', 'WAITING', '2026-03-01 14:55:00', NULL, '2026-03-01 14:55:00', '2026-03-01 14:55:00'),
(30, 6, 31, 11, 'BUS_COACH', 'WAITING', '2026-03-01 15:00:00', NULL, '2026-03-01 15:00:00', '2026-03-01 15:00:00');

-- 4. SEED REVIEWS (85 chân thực, gắn liền với các đơn đặt chỗ hoàn tất từ booking_id 27 trở đi)
INSERT IGNORE INTO reviews (id, booking_id, parking_lot_id, driver_id, rating, comment, created_at, updated_at) VALUES
(1, 27, 1, 1, 5, 'Bãi Cát Lái rất rộng, mặt nền bê tông sạch sẽ, có bảo vệ xi-nhan hướng dẫn xe container vào chuồng nhiệt tình!', '2026-02-02 10:00:00', '2026-02-02 10:00:00'),
(2, 28, 2, 2, 4, 'Bãi ngay mặt tiền Xa Lộ Hà Nội rất tiện, tuy nhiên giờ tan tầm 17h-18h hơi đông xe, giá hợp lý.', '2026-02-02 14:30:00', '2026-02-02 14:30:00'),
(3, 29, 3, 3, 5, 'Bãi có mái che một phần, an ninh tốt, camera rọi rõ từng góc. Rất yên tâm gửi hàng qua đêm.', '2026-02-03 09:15:00', '2026-02-03 09:15:00'),
(4, 30, 4, 4, 3, 'Đường vào hơi nhiều ổ gà mùa mưa, mong chủ bãi dặm vá lại cho xe container dễ vào hơn.', '2026-02-03 16:00:00', '2026-02-03 16:00:00'),
(5, 31, 5, 5, 5, 'Vị trí bãi ngay Tân Túc thuận tiện vô cùng khi từ cao tốc Trung Lương xuống. Căn tin bán cơm ngon.', '2026-02-04 11:20:00', '2026-02-04 11:20:00'),
(6, 32, 6, 6, 4, 'Bãi xe Võ Văn Kiệt thoáng, barie nhận diện biển số tự động quét nhanh trong 2 giây.', '2026-02-04 17:40:00', '2026-02-04 17:40:00'),
(7, 33, 7, 7, 4, 'Bãi đêm Vĩnh Lộc yên tĩnh, bảo vệ tuần tra liên tục. Đỗ qua đêm giá rất hữu nghị.', '2026-02-05 08:30:00', '2026-02-05 08:30:00'),
(8, 34, 8, 8, 5, 'Bãi cảng Hiệp Phước đẳng cấp quốc tế, cân điện tử chuẩn xác, có luôn chỗ cắm điện moóc lạnh.', '2026-02-05 13:10:00', '2026-02-05 13:10:00'),
(9, 35, 9, 9, 4, 'Gần kho xăng dầu Nhà Bè, thuận tiện đỗ chờ nhận lệnh xuất kho.', '2026-02-06 10:00:00', '2026-02-06 10:00:00'),
(10, 36, 10, 10, 5, 'Cực kỳ tiện cho xe đi cao tốc Long Thành. Phòng chờ tài xế có máy lạnh và nước uống miễn phí.', '2026-02-06 15:45:00', '2026-02-06 15:45:00'),
(11, 37, 11, 11, 5, 'Khu vực Phú Mỹ Hưng đường sá thông thoáng, giao diện đặt chỗ trên app trừ tiền cọc rất chuẩn.', '2026-02-07 09:20:00', '2026-02-07 09:20:00'),
(12, 38, 12, 12, 4, 'Bãi Tân Sơn Nhất gom hàng nhanh, giá giờ cao điểm hơi cao xíu nhưng xứng đáng vì không bị kẹt xe.', '2026-02-07 14:15:00', '2026-02-07 14:15:00'),
(13, 39, 13, 13, 4, 'Gần đường Cộng Hòa, phù hợp xe tải nhỏ đi giao hàng cho các sàn thương mại điện tử.', '2026-02-08 11:00:00', '2026-02-08 11:00:00'),
(14, 40, 14, 14, 3, 'Chợ đầu mối Hóc Môn đêm xe ra vào tấp nập, lối vào hơi chật nhưng bảo vệ điều tiết khá tốt.', '2026-02-08 16:30:00', '2026-02-08 16:30:00'),
(15, 41, 15, 15, 4, 'Bãi Quận 4 sát trung tâm nên gửi xe rất tiện khi đưa khách du lịch tham quan phố đi bộ.', '2026-02-09 10:10:00', '2026-02-09 10:10:00'),
(16, 42, 16, 16, 5, 'Bãi Sóng Thần rộng mênh mông, đèn chiếu sáng ban đêm sáng trưng như ban ngày.', '2026-02-09 15:00:00', '2026-02-09 15:00:00'),
(17, 43, 17, 17, 4, 'Có trạm xăng dầu kế bên, rửa xe sạch sẽ giá ưu đãi cho tài xế của app SmartParking.', '2026-02-10 09:40:00', '2026-02-10 09:40:00'),
(18, 44, 18, 18, 5, 'Mỹ Phước Tân Vạn xe công chạy nhiều, có bãi staging này đỡ phải dừng đỗ bậy bên lề đường.', '2026-02-10 14:50:00', '2026-02-10 14:50:00'),
(19, 45, 19, 19, 5, 'Bãi Phú Hữu giảm tải tốt cho vòng xoay Mỹ Thủy, dịch vụ chuyên nghiệp.', '2026-02-11 11:30:00', '2026-02-11 11:30:00'),
(20, 46, 20, 20, 4, 'Bãi sạch đẹp, thủ tục check-in bằng mã QR trên app cực kỳ nhanh.', '2026-02-11 16:15:00', '2026-02-11 16:15:00'),
(21, 47, 21, 1, 5, 'Đỗ đêm chờ giờ vào cảng rất an tâm, không lo bị rút trộm dầu hay mất bình ắc quy.', '2026-02-12 08:30:00', '2026-02-12 08:30:00'),
(22, 48, 22, 2, 5, 'Bãi Tân Cảng chuẩn 5 sao cho anh em xe container, nhân viên hỗ trợ nhiệt tình 10/10.', '2026-02-12 13:45:00', '2026-02-12 13:45:00'),
(23, 49, 23, 3, 4, 'Kết nối ga Sóng Thần tiện lợi bốc dỡ hàng chuyển sang toa tàu hỏa.', '2026-02-13 10:20:00', '2026-02-13 10:20:00'),
(24, 50, 24, 4, 5, 'Trạm Bến Lức Long An cực lớn, đồ ăn nước uống đầy đủ, bãi đậu xe tải nặng thoải mái.', '2026-02-13 15:30:00', '2026-02-13 15:30:00'),
(25, 51, 1, 5, 5, 'Rất hài lòng, sẽ tiếp tục đặt bãi này khi quay đầu hàng từ miền Trung vào.', '2026-02-14 09:00:00', '2026-02-14 09:00:00'),
(26, 52, 2, 6, 4, 'Bảo vệ ca đêm vui vẻ, hỗ trợ cắm sạc bình ắc quy hộ khi xe bị cạn điện.', '2026-02-14 14:10:00', '2026-02-14 14:10:00'),
(27, 53, 3, 7, 4, 'Thủ tục ra vào nhanh gọn, xuất hóa đơn điện tử VAT gửi thẳng vào email.', '2026-02-15 10:00:00', '2026-02-15 10:00:00'),
(28, 54, 4, 8, 3, 'Giá cả hợp lý nhưng cần tăng cường thêm bóng đèn cao áp ở dãy cuối bãi.', '2026-02-15 16:20:00', '2026-02-15 16:20:00'),
(29, 55, 5, 9, 5, 'Điểm dừng chân quen thuộc của công ty vận tải chúng tôi trên Quốc Lộ 1A.', '2026-02-16 09:30:00', '2026-02-16 09:30:00'),
(30, 56, 6, 10, 4, 'Đỗ xe an toàn, vị trí trung tâm Tây Sài Gòn thuận tiện phân phối hàng.', '2026-02-16 15:00:00', '2026-02-16 15:00:00');
