-- ============================================================
-- V2__seed_demo_data.sql
-- Notification Service Seed Data (220+ Notifications & Processed Events)
-- Covers ALL Channels (IN_APP, EMAIL, SMS, PUSH)
-- Covers ALL Types (BOOKING, PAYMENT, QUEUE, SYSTEM)
-- Balanced Read vs Unread distribution for badge counting
-- ============================================================

-- 1. SEED NOTIFICATIONS (Batch 1: Real-time and Important Notifications)
INSERT IGNORE INTO notifications (id, user_id, title, content, channel, type, target_type, target_id, read_at, status, created_at, sent_at) VALUES
-- Admin Notifications (Users 1-3)
(1, 1, 'Yêu cầu rút tiền mới', 'Merchant Lê Văn Thành (Đông Sài Gòn) vừa yêu cầu rút 15.000.000 VNĐ về tài khoản Vietcombank.', 'IN_APP', 'PAYMENT', 'WITHDRAWAL', 1, NULL, 'SENT', '2026-03-01 09:05:00', '2026-03-01 09:05:00'),
(2, 1, 'Khiếu nại tranh chấp mới #DISP-001', 'Tài xế Trịnh Công Danh khiếu nại bãi Cát Lái tính nhầm phụ thu quá giờ 210.000 VNĐ.', 'IN_APP', 'SYSTEM', 'DISPUTE', 1, NULL, 'SENT', '2026-03-01 12:20:00', '2026-03-01 12:20:00'),
(3, 2, 'Bãi xe mới chờ phê duyệt', 'Bãi Xe Tiếp Vận Tân Biên - Biên Hòa vừa nộp hồ sơ xin cấp phép hoạt động.', 'EMAIL', 'SYSTEM', 'PARKING_LOT', 25, NULL, 'SENT', '2026-02-20 09:10:00', '2026-02-20 09:10:00'),
(4, 1, 'Cảnh báo vi phạm niêm yết giá', 'Bãi xe Lê Minh Xuân bị nhiều tài xế phản ánh thu phí sai quy định. Đã kích hoạt lệnh tạm đình chỉ.', 'IN_APP', 'SYSTEM', 'PARKING_LOT', 29, '2026-02-18 11:00:00', 'SENT', '2026-02-18 10:35:00', '2026-02-18 10:35:00'),
(5, 3, 'Tài xế bị báo cáo vi phạm', 'Tài xế Chu Văn Cảnh bị hệ thống khóa tài khoản vĩnh viễn do bỏ xe và trốn tránh thanh toán.', 'IN_APP', 'SYSTEM', 'USER', 38, '2026-02-25 15:30:00', 'SENT', '2026-02-25 15:05:00', '2026-02-25 15:05:00'),

-- Merchant Notifications (Users 4-13)
(6, 4, 'Biến động số dư ví: +404.800 VNĐ', 'Quyết toán doanh thu xe container 51C-482.91 tại Bãi Cát Lái (đã khấu trừ 8% phí sàn).', 'IN_APP', 'PAYMENT', 'WALLET', 1, NULL, 'SENT', '2026-03-01 16:05:00', '2026-03-01 16:05:00'),
(7, 4, 'Đặt chỗ mới thành công', 'Tài xế Nguyễn Văn Hùng vừa đặt chỗ xe container 51C-482.91 lúc 14:00 hôm nay.', 'PUSH', 'BOOKING', 'BOOKING', 1, NULL, 'SENT', '2026-03-01 13:02:00', '2026-03-01 13:02:00'),
(8, 5, 'Biến động số dư ví: +353.280 VNĐ', 'Quyết toán doanh thu xe tải nặng 51C-681.94 tại Bãi Tân Túc QL.1A.', 'IN_APP', 'PAYMENT', 'WALLET', 2, NULL, 'SENT', '2026-03-01 16:10:00', '2026-03-01 16:10:00'),
(9, 6, 'Lệnh rút tiền đã được duyệt', 'Yêu cầu rút 40.000.000 VNĐ về BIDV đã được giải ngân thành công.', 'SMS', 'PAYMENT', 'WITHDRAWAL', 11, '2026-02-22 14:00:00', 'SENT', '2026-02-22 13:50:00', '2026-02-22 13:50:00'),
(10, 7, 'Bãi xe gần kín chỗ!', 'Bãi Staging Sân Bay Tân Sơn Nhất chỉ còn 0 slot xe tải nhẹ khả dụng. Đã tự động kích hoạt hàng đợi.', 'PUSH', 'QUEUE', 'PARKING_LOT', 12, NULL, 'SENT', '2026-03-01 12:00:00', '2026-03-01 12:00:00'),

-- Driver Notifications (Users 14-38)
(11, 14, 'Đặt chỗ thành công #BK-20260301-001', 'Bạn đã giữ chỗ thành công tại Bãi Cát Lái - Cổng G. Vui lòng thanh toán cọc trong 15 phút.', 'IN_APP', 'BOOKING', 'BOOKING', 1, NULL, 'SENT', '2026-03-01 13:00:00', '2026-03-01 13:00:00'),
(12, 17, 'Thanh toán cọc thành công #BK-20260301-006', 'Đã nhận 150.000 VNĐ tiền cọc. Vui lòng check-in trước 18:30 ngày 01/03/2026.', 'PUSH', 'PAYMENT', 'BOOKING', 6, NULL, 'SENT', '2026-03-01 12:10:00', '2026-03-01 12:10:00'),
(13, 19, 'Check-in thành công!', 'Xe 72C-294.15 đã vào bãi Cát Lái lúc 08:05. Chúc bạn một ngày làm việc thuận lợi!', 'PUSH', 'BOOKING', 'BOOKING', 11, '2026-03-01 08:10:00', 'SENT', '2026-03-01 08:05:00', '2026-03-01 08:05:00'),
(14, 22, 'CẢNH BÁO: Đỗ quá giờ hẹn!', 'Xe 60C-619.48 của bạn đã đỗ quá 12:00. Hệ thống bắt đầu tính phụ thu quá giờ 1.8x.', 'SMS', 'BOOKING', 'BOOKING', 18, NULL, 'SENT', '2026-03-01 12:05:00', '2026-03-01 12:05:00'),
(15, 23, 'Có slot trống tại Bãi Cát Lái!', 'Đến lượt bạn trong hàng đợi! Bấm vào đây để xác nhận đặt chỗ trong vòng 15 phút tới.', 'PUSH', 'QUEUE', 'QUEUE', 7, NULL, 'SENT', '2026-03-01 11:30:00', '2026-03-01 11:30:00'),
(16, 24, 'Có slot trống tại Bãi Cát Lái!', 'Đến lượt bạn trong hàng đợi! Bấm vào đây để xác nhận đặt chỗ trong vòng 15 phút tới.', 'PUSH', 'QUEUE', 'QUEUE', 8, NULL, 'SENT', '2026-03-01 11:35:00', '2026-03-01 11:35:00'),
(17, 31, 'Hết hạn giữ chỗ trong hàng đợi', 'Bạn đã không xác nhận lượt trong vòng 15 phút. Vị trí đã được nhường cho xe phía sau.', 'IN_APP', 'QUEUE', 'QUEUE', 15, '2026-02-27 15:00:00', 'SENT', '2026-02-27 14:16:00', '2026-02-27 14:16:00'),
(18, 32, 'Hoàn tiền cọc thành công', 'Đã hoàn trả 100.000 VNĐ tiền cọc vào tài khoản của bạn do hủy đặt chỗ hợp lệ.', 'IN_APP', 'PAYMENT', 'BOOKING', 23, '2026-03-01 12:00:00', 'SENT', '2026-03-01 11:35:00', '2026-03-01 11:35:00'),
(19, 14, 'Cảm ơn bạn đã để lại đánh giá!', 'Đánh giá 5 sao của bạn cho Bãi Cát Lái đã được ghi nhận. Tặng bạn 50 điểm thưởng tích lũy.', 'IN_APP', 'SYSTEM', 'BOOKING', 27, '2026-02-02 11:00:00', 'SENT', '2026-02-02 10:05:00', '2026-02-02 10:05:00'),
(20, 15, 'Hóa đơn điện tử VAT', 'Hóa đơn giá trị gia tăng cho chuyến đỗ xe #BK-202602-002 đã được gửi vào email của bạn.', 'EMAIL', 'SYSTEM', 'BOOKING', 28, '2026-02-02 15:00:00', 'SENT', '2026-02-02 14:35:00', '2026-02-02 14:35:00');

-- 2. SEED HISTORICAL NOTIFICATIONS (Notifications 21 to 220)
-- Tạo 200 thông báo đa dạng lịch sử cho tài xế và chủ bãi (phân bổ trạng thái đọc và chưa đọc)
INSERT IGNORE INTO notifications (id, user_id, title, content, channel, type, target_type, target_id, read_at, status, created_at, sent_at)
SELECT 
    n + 20 AS id,
    -- Luân chuyển người nhận giữa Drivers (14-36) và Merchants (4-13)
    CASE 
        WHEN (n % 3 = 0) THEN (((n - 1) % 10) + 4) -- Merchant
        ELSE (((n - 1) % 23) + 14)                -- Driver
    END AS user_id,
    CASE ((n - 1) % 5)
        WHEN 0 THEN CONCAT('Thanh toán thành công đơn #BK-202602-', LPAD(n, 3, '0'))
        WHEN 1 THEN CONCAT('Nhắc nhở giờ hẹn check-in bãi xe #BK-202602-', LPAD(n, 3, '0'))
        WHEN 2 THEN CONCAT('Quyết toán doanh thu đỗ xe #BK-202602-', LPAD(n, 3, '0'))
        WHEN 3 THEN CONCAT('Check-out thành công đơn #BK-202602-', LPAD(n, 3, '0'))
        ELSE 'Khuyến mãi giờ vàng giảm giá 20% tại cụm bãi TP. Thủ Đức'
    END AS title,
    CASE ((n - 1) % 5)
        WHEN 0 THEN CONCAT('Hệ thống đã nhận đủ thanh toán cho lượt đỗ xe tại bãi. Cảm ơn quý khách đã tin dùng SmartParkingHub.')
        WHEN 1 THEN CONCAT('Chuyến đỗ xe của bạn sẽ bắt đầu trong 30 phút nữa. Vui lòng chuẩn bị mã QR trên app để quét tại cổng barie.')
        WHEN 2 THEN CONCAT('Tài khoản ví của bạn vừa được cộng doanh thu từ lượt đỗ xe thành công sau khi trừ 8% phí nền tảng.')
        WHEN 3 THEN CONCAT('Xe của bạn đã rời bãi đỗ an toàn. Đừng quên đánh giá chất lượng phục vụ của bãi để nhận quà nhé!')
        ELSE 'Nhập mã SMARTTRUCK20 để được giảm 20% phí đỗ xe vào khung giờ thấp điểm từ 11:00 đến 14:00 hàng ngày.'
    END AS content,
    CASE ((n - 1) % 4)
        WHEN 0 THEN 'IN_APP'
        WHEN 1 THEN 'PUSH'
        WHEN 2 THEN 'EMAIL'
        ELSE 'SMS'
    END AS channel,
    CASE ((n - 1) % 4)
        WHEN 0 THEN 'PAYMENT'
        WHEN 1 THEN 'BOOKING'
        WHEN 2 THEN 'SYSTEM'
        ELSE 'QUEUE'
    END AS type,
    'BOOKING' AS target_type,
    n + 26 AS target_id,
    -- 65% đã đọc (read_at có thời gian), 35% chưa đọc (read_at NULL) để hiển thị unread count trên header
    CASE 
        WHEN (n % 3 != 0) THEN DATE_ADD('2026-02-01 08:30:00', INTERVAL (n * 3) HOUR)
        ELSE NULL 
    END AS read_at,
    'SENT' AS status,
    DATE_ADD('2026-02-01 08:00:00', INTERVAL (n * 3) HOUR) AS created_at,
    DATE_ADD('2026-02-01 08:00:00', INTERVAL (n * 3) HOUR) AS sent_at
FROM (
    SELECT t4.i*1000 + t3.i*100 + t2.i*10 + t1.i + 1 AS n
    FROM (SELECT 0 AS i UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t1,
         (SELECT 0 AS i UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t2,
         (SELECT 0 AS i UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t3,
         (SELECT 0 AS i UNION SELECT 1) t4
) seq
WHERE n <= 200;

-- 3. SEED PROCESSED EVENTS (Deduplication record history for idempotent consumer)
INSERT IGNORE INTO processed_events (event_id, event_type, processed_at) VALUES
('EVT-PAY-20260301-001', 'PaymentCompletedEvent', '2026-03-01 12:05:00'),
('EVT-PAY-20260301-002', 'PaymentCompletedEvent', '2026-03-01 13:03:00'),
('EVT-BOOK-20260301-001', 'BookingCheckInEvent', '2026-03-01 08:05:00'),
('EVT-BOOK-20260301-002', 'BookingCheckOutEvent', '2026-03-01 14:02:00'),
('EVT-WITHDRAW-20260228-01', 'WithdrawalApprovedEvent', '2026-02-28 10:00:00');
