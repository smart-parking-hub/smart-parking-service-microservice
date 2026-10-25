-- ============================================================
-- V2__seed_demo_data.sql
-- Payment Service Seed Data (10 Wallets, 230+ Payments, 160+ Ledger Entries, 
-- 25 Withdrawal Requests, 20 Disputes)
-- Covers ALL Enums: PaymentStatus, PaymentType, PaymentGateway, 
-- WalletLedgerEntryType, WithdrawalStatus, DisputeStatus
-- ============================================================

-- 1. SEED WALLETS (Ví số dư kinh doanh của 10 Merchant)
INSERT IGNORE INTO wallets (id, merchant_id, balance, currency, status, version, created_at, updated_at) VALUES
(1, 1, 48500000.00, 'VND', 'ACTIVE', 12, '2026-01-15 08:30:00', '2026-03-01 10:00:00'),
(2, 2, 62300000.00, 'VND', 'ACTIVE', 15, '2026-01-16 09:00:00', '2026-03-01 10:30:00'),
(3, 3, 89200000.00, 'VND', 'ACTIVE', 20, '2026-01-17 10:00:00', '2026-03-01 11:00:00'),
(4, 4, 35400000.00, 'VND', 'ACTIVE', 9,  '2026-01-18 11:20:00', '2026-03-01 11:30:00'),
(5, 5, 74100000.00, 'VND', 'ACTIVE', 18, '2026-01-19 14:00:00', '2026-03-01 12:00:00'),
(6, 6, 51000000.00, 'VND', 'ACTIVE', 14, '2026-01-20 15:45:00', '2026-03-01 12:30:00'),
(7, 7, 93800000.00, 'VND', 'ACTIVE', 22, '2026-01-21 08:15:00', '2026-03-01 13:00:00'),
(8, 8, 41600000.00, 'VND', 'ACTIVE', 11, '2026-01-22 09:30:00', '2026-03-01 13:30:00'),
(9, 9, 38900000.00, 'VND', 'ACTIVE', 8,  '2026-01-23 10:45:00', '2026-03-01 14:00:00'),
(10, 10, 67500000.00, 'VND', 'ACTIVE', 16, '2026-01-24 13:20:00', '2026-03-01 14:30:00');

-- 2. SEED PAYMENTS (Đa dạng cổng thanh toán, thể loại và trạng thái)
INSERT IGNORE INTO payments (id, booking_id, driver_id, merchant_id, parking_lot_id, amount, gateway, payment_type, status, transaction_code, order_code, idempotency_key, created_at, updated_at) VALUES
-- Pending payments (Đang chờ thanh toán)
(1, 1, 1, 1, 1, 150000.00, 'PAYOS', 'DEPOSIT', 'PENDING', 'TXN-PAYOS-20260301-001', 1709290001, 'IDEMP-001-DEP', '2026-03-01 13:00:00', '2026-03-01 13:00:00'),
(2, 2, 2, 2, 4, 120000.00, 'VNPAY', 'DEPOSIT', 'PENDING', 'TXN-VNPAY-20260301-002', 1709290002, 'IDEMP-002-DEP', '2026-03-01 14:30:00', '2026-03-01 14:30:00'),
(3, 3, 3, 3, 8, 150000.00, 'MOMO', 'DEPOSIT', 'PENDING', 'TXN-MOMO-20260301-003', 1709290003, 'IDEMP-003-DEP', '2026-03-01 15:00:00', '2026-03-01 15:00:00'),

-- Successful deposit payments for active check-in/parking bookings
(4, 6, 4, 3, 10, 150000.00, 'PAYOS', 'DEPOSIT', 'SUCCESS', 'TXN-PAYOS-20260301-006', 1709290006, 'IDEMP-006-DEP', '2026-03-01 12:00:00', '2026-03-01 12:05:00'),
(5, 7, 5, 1, 1, 150000.00, 'VNPAY', 'DEPOSIT', 'SUCCESS', 'TXN-VNPAY-20260301-007', 1709290007, 'IDEMP-007-DEP', '2026-03-01 13:00:00', '2026-03-01 13:03:00'),
(6, 8, 12, 2, 5, 100000.00, 'ZALOPAY', 'DEPOSIT', 'SUCCESS', 'TXN-ZALO-20260301-008', 1709290008, 'IDEMP-008-DEP', '2026-03-01 14:00:00', '2026-03-01 14:02:00'),
(7, 9, 22, 1, 3, 60000.00, 'PAYOS', 'DEPOSIT', 'SUCCESS', 'TXN-PAYOS-20260301-009', 1709290009, 'IDEMP-009-DEP', '2026-03-01 15:30:00', '2026-03-01 15:32:00'),
(8, 10, 13, 5, 16, 100000.00, 'MOMO', 'DEPOSIT', 'SUCCESS', 'TXN-MOMO-20260301-010', 1709290010, 'IDEMP-010-DEP', '2026-03-01 16:00:00', '2026-03-01 16:03:00'),
(9, 11, 6, 1, 1, 150000.00, 'PAYOS', 'DEPOSIT', 'SUCCESS', 'TXN-PAYOS-20260301-011', 1709290011, 'IDEMP-011-DEP', '2026-03-01 06:00:00', '2026-03-01 06:02:00'),
(10, 12, 7, 3, 8, 150000.00, 'VNPAY', 'DEPOSIT', 'SUCCESS', 'TXN-VNPAY-20260301-012', 1709290012, 'IDEMP-012-DEP', '2026-03-01 07:00:00', '2026-03-01 07:04:00'),

-- Failed payments (Tài xế nhập sai OTP ngân hàng hoặc thẻ không đủ tiền)
(11, 23, 18, 1, 2, 100000.00, 'VNPAY', 'DEPOSIT', 'FAILED', 'TXN-VNPAY-FAIL-023', 1709290023, 'IDEMP-023-DEP', '2026-03-01 10:00:00', '2026-03-01 10:05:00'),
(12, 24, 19, 5, 17, 100000.00, 'PAYOS', 'DEPOSIT', 'FAILED', 'TXN-PAYOS-FAIL-024', 1709290024, 'IDEMP-024-DEP', '2026-03-01 11:00:00', '2026-03-01 11:04:00'),

-- Refund payments (Hủy chỗ hợp lệ, hoàn tiền cọc lại tài xế)
(13, 23, 18, 1, 2, 100000.00, 'PAYOS', 'REFUND', 'REFUNDED', 'TXN-REFUND-023', 1709290025, 'IDEMP-023-REF', '2026-03-01 11:30:00', '2026-03-01 11:35:00'),
(14, 24, 19, 5, 17, 100000.00, 'VNPAY', 'REFUND', 'REFUNDED', 'TXN-REFUND-024', 1709290026, 'IDEMP-024-REF', '2026-03-01 12:45:00', '2026-03-01 12:50:00'),

-- Penalty payments (Phạt quá giờ)
(15, 18, 9, 1, 1, 210000.00, 'PAYOS', 'PENALTY', 'SUCCESS', 'TXN-PENALTY-018', 1709290027, 'IDEMP-018-PEN', '2026-03-01 12:05:00', '2026-03-01 12:08:00'),
(16, 19, 16, 5, 16, 130000.00, 'MOMO', 'PENALTY', 'SUCCESS', 'TXN-PENALTY-019', 1709290028, 'IDEMP-019-PEN', '2026-03-01 13:10:00', '2026-03-01 13:12:00');

-- 3. SEED HISTORICAL PAYMENTS FOR COMPLETED BOOKINGS (Payments 17 to 210)
INSERT IGNORE INTO payments (id, booking_id, driver_id, merchant_id, parking_lot_id, amount, gateway, payment_type, status, transaction_code, order_code, idempotency_key, created_at, updated_at)
SELECT 
    n + 16 AS id,
    n + 26 AS booking_id,
    ((n - 1) % 23) + 1 AS driver_id,
    (((n - 1) % 24) % 10) + 1 AS merchant_id,
    ((n - 1) % 24) + 1 AS parking_lot_id,
    (200000.00 + (n * 3500.00)) AS amount,
    CASE ((n - 1) % 4)
        WHEN 0 THEN 'PAYOS'
        WHEN 1 THEN 'VNPAY'
        WHEN 2 THEN 'MOMO'
        ELSE 'ZALOPAY'
    END AS gateway,
    'FINAL_PAYMENT' AS payment_type,
    'SUCCESS' AS status,
    CONCAT('TXN-PAY-202602-', LPAD(n, 4, '0')) AS transaction_code,
    (1708000000 + n) AS order_code,
    CONCAT('IDEMP-HIST-', LPAD(n, 4, '0')) AS idempotency_key,
    DATE_ADD('2026-02-01 16:00:00', INTERVAL (n * 3) HOUR) AS created_at,
    DATE_ADD('2026-02-01 16:02:00', INTERVAL (n * 3) HOUR) AS updated_at
FROM (
    SELECT t4.i*1000 + t3.i*100 + t2.i*10 + t1.i + 1 AS n
    FROM (SELECT 0 AS i UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t1,
         (SELECT 0 AS i UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t2,
         (SELECT 0 AS i UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t3,
         (SELECT 0 AS i UNION SELECT 1) t4
) seq
WHERE n <= 194;

-- 4. SEED WALLET LEDGER ENTRIES (Double-entry accounting, platform fee 8%)
-- Ghi nhận dòng tiền minh bạch: Phí nền tảng 8%, Thực nhận Merchant 92%
INSERT IGNORE INTO wallet_ledger_entries (id, merchant_id, booking_id, payment_id, entry_type, gross_amount, fee_percent, fee_amount, net_amount, note, created_at)
SELECT 
    n AS id,
    (((n - 1) % 24) % 10) + 1 AS merchant_id,
    n + 26 AS booking_id,
    n + 16 AS payment_id,
    'SETTLEMENT_CREDIT' AS entry_type,
    (200000.00 + (n * 3500.00)) AS gross_amount,
    8.00 AS fee_percent,
    ((200000.00 + (n * 3500.00)) * 0.08) AS fee_amount,
    ((200000.00 + (n * 3500.00)) * 0.92) AS net_amount,
    CONCAT('Quyết toán doanh thu đỗ xe đơn #BK-202602-', LPAD(n, 3, '0'), ' (Phí sàn 8%)') AS note,
    DATE_ADD('2026-02-01 16:05:00', INTERVAL (n * 3) HOUR) AS created_at
FROM (
    SELECT t4.i*1000 + t3.i*100 + t2.i*10 + t1.i + 1 AS n
    FROM (SELECT 0 AS i UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t1,
         (SELECT 0 AS i UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t2,
         (SELECT 0 AS i UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t3,
         (SELECT 0 AS i UNION SELECT 1) t4
) seq
WHERE n <= 160;

-- 5. SEED WITHDRAWAL REQUESTS (25 yêu cầu rút tiền: PENDING, APPROVED, PAID, REJECTED)
INSERT IGNORE INTO withdrawal_requests (id, merchant_id, amount, status, requested_at, processed_by, processed_at, decision_reason) VALUES
-- PENDING (Chờ Admin phê duyệt chuyển tiền về tài khoản ngân hàng)
(1, 1, 15000000.00, 'PENDING', '2026-03-01 09:00:00', NULL, NULL, NULL),
(2, 2, 20000000.00, 'PENDING', '2026-03-01 10:15:00', NULL, NULL, NULL),
(3, 3, 30000000.00, 'PENDING', '2026-03-01 11:30:00', NULL, NULL, NULL),
(4, 5, 25000000.00, 'PENDING', '2026-03-01 13:00:00', NULL, NULL, NULL),
(5, 7, 35000000.00, 'PENDING', '2026-03-01 14:00:00', NULL, NULL, NULL),

-- APPROVED (Đã duyệt lệnh chuyển khoản, chờ kế toán giải ngân)
(6, 4, 10000000.00, 'APPROVED', '2026-02-28 08:30:00', 1, '2026-02-28 10:00:00', 'Đã đối soát xong biên bản doanh thu tuần 4 tháng 2/2026'),
(7, 6, 18000000.00, 'APPROVED', '2026-02-28 09:00:00', 2, '2026-02-28 10:30:00', 'Hồ sơ hóa đơn hợp lệ, chuyển phòng tài vụ'),
(8, 8, 12000000.00, 'APPROVED', '2026-02-28 10:00:00', 1, '2026-02-28 11:00:00', 'Phê duyệt rút tiền đợt 2 tháng 2'),

-- PAID (Đã giải ngân tiền thành công về tài khoản ngân hàng của Merchant)
(9, 1, 25000000.00, 'PAID', '2026-02-20 09:00:00', 1, '2026-02-20 14:00:00', 'Chuyển khoản thành công qua Vietcombank số GD VCB-982310'),
(10, 2, 30000000.00, 'PAID', '2026-02-21 10:00:00', 1, '2026-02-21 15:30:00', 'Chuyển khoản thành công qua Techcombank số GD TCB-829103'),
(11, 3, 40000000.00, 'PAID', '2026-02-22 08:30:00', 2, '2026-02-22 13:45:00', 'Chuyển khoản thành công qua BIDV số GD BIDV-481920'),
(12, 4, 15000000.00, 'PAID', '2026-02-23 09:15:00', 1, '2026-02-23 14:10:00', 'Chuyển khoản thành công qua MB Bank số GD MB-192837'),
(13, 5, 35000000.00, 'PAID', '2026-02-24 10:00:00', 2, '2026-02-24 16:00:00', 'Chuyển khoản thành công qua VPBank số GD VPB-381920'),
(14, 6, 20000000.00, 'PAID', '2026-02-25 08:00:00', 1, '2026-02-25 11:30:00', 'Chuyển khoản thành công qua ACB số GD ACB-928173'),
(15, 7, 45000000.00, 'PAID', '2026-02-25 14:00:00', 2, '2026-02-25 17:00:00', 'Chuyển khoản thành công qua Sacombank số GD STB-471928'),
(16, 8, 18000000.00, 'PAID', '2026-02-26 09:30:00', 1, '2026-02-26 13:00:00', 'Chuyển khoản thành công qua TPBank số GD TPB-829102'),
(17, 9, 15000000.00, 'PAID', '2026-02-26 11:00:00', 2, '2026-02-26 15:30:00', 'Chuyển khoản thành công qua VietinBank số GD CTG-291029'),
(18, 10, 28000000.00, 'PAID', '2026-02-27 10:00:00', 1, '2026-02-27 14:45:00', 'Chuyển khoản thành công qua HDBank số GD HDB-918273'),

-- REJECTED (Từ chối rút tiền do nghi vấn sai lệch số liệu hoặc thông tin thụ hưởng)
(19, 1, 50000000.00, 'REJECTED', '2026-02-15 09:00:00', 1, '2026-02-15 11:00:00', 'Từ chối: Số dư ví khả dụng tại thời điểm rút không đủ 50.000.000đ (đang có giao dịch giữ cọc)'),
(20, 5, 80000000.00, 'REJECTED', '2026-02-16 10:00:00', 2, '2026-02-16 14:00:00', 'Từ chối: Tên chủ tài khoản thụ hưởng tại VPBank không khớp với tên doanh nghiệp đăng ký'),
(21, 9, 20000000.00, 'REJECTED', '2026-02-18 14:30:00', 1, '2026-02-18 16:30:00', 'Từ chối: Đang có khiếu nại tranh chấp phí quá giờ chưa xử lý dứt điểm'),
(22, 2, 10000000.00, 'PAID', '2026-02-10 09:00:00', 1, '2026-02-10 14:00:00', 'Chuyển khoản thành công đợt 1 tháng 2/2026'),
(23, 3, 20000000.00, 'PAID', '2026-02-11 10:00:00', 2, '2026-02-11 15:00:00', 'Chuyển khoản thành công đợt 1 tháng 2/2026'),
(24, 7, 30000000.00, 'PAID', '2026-02-12 11:00:00', 1, '2026-02-12 16:00:00', 'Chuyển khoản thành công đợt 1 tháng 2/2026'),
(25, 10, 15000000.00, 'PAID', '2026-02-13 13:00:00', 2, '2026-02-13 17:00:00', 'Chuyển khoản thành công đợt 1 tháng 2/2026');

-- 6. SEED DISPUTES (20 vụ khiếu nại tranh chấp thực tế: PENDING, RESOLVED, REJECTED)
INSERT IGNORE INTO disputes (id, booking_id, driver_id, merchant_id, reason, description, status, resolution, created_at, updated_at) VALUES
-- PENDING (Chờ Admin phân xử)
(1, 18, 9, 1, 'Tính nhầm phụ thu quá giờ', 'Tôi đã lái xe ra trước vạch barie lúc 12:00 nhưng barie bị kẹt lỗi cảm biến mất 15 phút, hệ thống tự cộng thêm tiền phạt quá giờ 210.000đ.', 'PENDING', NULL, '2026-03-01 12:15:00', '2026-03-01 12:15:00'),
(2, 19, 16, 5, 'Sai lệch biểu phí ban đêm', 'Bãi Sóng Thần tính giá theo khung giờ cao điểm ban đêm nhưng trên app hiển thị là giờ thường.', 'PENDING', NULL, '2026-03-01 13:30:00', '2026-03-01 13:30:00'),
(3, 20, 25, 3, 'Bảo vệ thu thêm tiền mặt sai quy định', 'Bảo vệ bãi tự ý thu thêm 30.000đ tiền nước rửa kính dù trong gói đặt trên app đã bao gồm.', 'PENDING', NULL, '2026-03-01 14:00:00', '2026-03-01 14:00:00'),
(4, 11, 6, 1, 'Vỡ đèn chiếu hậu khi lùi xe', 'Lối vào bãi để thùng hàng cản trở tầm nhìn khiến xe bị cạ quẹt nứt đèn sau.', 'PENDING', NULL, '2026-03-01 14:30:00', '2026-03-01 14:30:00'),
(5, 12, 7, 3, 'Trễ giờ nhận container do xe khác chắn lối', 'Xe tải phía trước đỗ sai vị trí chặn lối ra mất gần 1 tiếng đồng hồ làm lỡ giờ đóng hàng cảng.', 'PENDING', NULL, '2026-03-01 15:00:00', '2026-03-01 15:00:00'),

-- RESOLVED (Đã phân xử xong và có biên bản hòa giải)
(6, 27, 1, 1, 'Trừ tiền hai lần qua cổng PayOS', 'Tài xế quét mã QR báo lỗi mạng nhưng tài khoản ngân hàng vẫn bị trừ 2 lần.', 'RESOLVED', 'Đã kiểm tra log cổng PayOS, xác nhận giao dịch trùng và hoàn trả 150.000đ về ví tài xế.', '2026-02-10 10:00:00', '2026-02-10 14:00:00'),
(7, 30, 4, 2, 'Lỗi nhận diện biển số tại barie', 'Camera AI nhận nhầm biển số 61C thành 51C khiến tài xế không mở được barie.', 'RESOLVED', 'Chủ bãi đã hiệu chỉnh lại góc đặt camera và bồi thường voucher giảm giá 50% cho lần đỗ kế tiếp.', '2026-02-12 11:00:00', '2026-02-12 16:30:00'),
(8, 35, 9, 3, 'Khiếu nại mất đồ nghề sau xe', 'Tài xế báo mất bộ cờ lê sửa chữa để trong hòm đồ hông xe.', 'RESOLVED', 'Trích xuất camera bãi phát hiện do tài xế quên khóa chốt hòm đồ rơi trên đường, không liên quan bảo vệ bãi.', '2026-02-15 09:30:00', '2026-02-15 15:00:00'),
(9, 40, 14, 4, 'Bãi hết chỗ dù đã cọc thành công', 'Tài xế đến bãi nhưng không còn slot xe tải do bãi cho xe ngoài vào.', 'RESOLVED', 'Chủ bãi bồi thường 100% tiền cọc và hỗ trợ tài xế đỗ tại bãi liên kết lân cận miễn phí.', '2026-02-18 08:00:00', '2026-02-18 10:30:00'),
(10, 45, 19, 6, 'Thu phí phụ trội không rõ lý do', 'Hệ thống hiển thị khoản phụ thu 50.000đ nhưng không ghi chú chi tiết.', 'RESOLVED', 'Xác định do lỗi làm tròn hệ thống, đã hoàn tiền chênh lệch 50.000đ về tài khoản tài xế.', '2026-02-20 14:00:00', '2026-02-20 16:00:00'),

-- REJECTED (Bác bỏ khiếu nại do không có bằng chứng hoặc tài xế vi phạm quy chế)
(11, 28, 2, 1, 'Yêu cầu hoàn cọc khi đến trễ 2 tiếng', 'Tài xế kẹt xe đến trễ 2 tiếng bãi đã hủy chỗ và yêu cầu trả lại cọc.', 'REJECTED', 'Theo điều khoản quy chế, quá 60 phút không check-in hệ thống tự hủy và không hoàn cọc.', '2026-02-05 14:00:00', '2026-02-05 16:00:00'),
(12, 33, 7, 2, 'Đỗ xe qua đêm đòi tính giá theo giờ ban ngày', 'Tài xế đỗ từ 22h đến 6h sáng nhưng không đồng ý biểu phí đêm.', 'REJECTED', 'Bãi xe đã niêm yết công khai biểu phí đêm trên ứng dụng trước khi tài xế bấm xác nhận đặt chỗ.', '2026-02-08 09:00:00', '2026-02-08 11:30:00'),
(13, 38, 12, 4, 'Đòi bồi thường vì bãi không có người rửa xe', 'Tài xế yêu cầu dịch vụ rửa xe nhưng bãi không có dịch vụ này.', 'REJECTED', 'Tiện ích bãi trên app không đăng ký dịch vụ rửa xe, khiếu nại không hợp lệ.', '2026-02-14 10:00:00', '2026-02-14 12:00:00'),
(14, 42, 16, 5, 'Làm hỏng cọc tiêu an toàn của bãi', 'Tài xế cự cãi không chịu đền tiền cọc tiêu cao su bị xe cán gãy.', 'REJECTED', 'Camera ghi lại rõ ràng tài xế lùi bất cẩn làm gãy cọc tiêu, yêu cầu tài xế bồi thường cho bãi.', '2026-02-19 15:00:00', '2026-02-19 17:30:00'),
(15, 50, 24, 9, 'Tài xế tự ý đỗ sai ô phân luồng', 'Xe container đỗ vào luồng xe khách làm cản trở xe khác ra vào.', 'REJECTED', 'Bác khiếu nại, xử phạt tài xế theo đúng quy chế vận hành bãi xe.', '2026-02-22 13:00:00', '2026-02-22 15:00:00'),
(16, 52, 2, 1, 'Khiếu nại đèn chiếu sáng chói mắt', 'Tài xế ngủ trên cabin kêu đèn cao áp chiếu thẳng vào kính.', 'REJECTED', 'Đèn phục vụ an ninh chung toàn bãi, bác yêu cầu tắt đèn.', '2026-02-23 08:00:00', '2026-02-23 09:30:00'),
(17, 54, 4, 2, 'Yêu cầu mở cổng bãi lúc 2h sáng', 'Bãi có quy định giờ đóng mở cổng đối với xe tải nhỏ.', 'REJECTED', 'Bãi đã công bố rõ khung giờ nhận xe tải nhỏ từ 5h đến 22h hàng ngày.', '2026-02-24 10:00:00', '2026-02-24 11:00:00'),
(18, 55, 5, 2, 'Nghi vấn số đo cân tải trọng', 'Tài xế cho rằng cân tải trọng của bãi nặng hơn cân ngoài trạm thu phí 200kg.', 'REJECTED', 'Cân điện tử của bãi có tem kiểm định đo lường chất lượng còn hạn sử dụng đến tháng 12/2026.', '2026-02-25 11:30:00', '2026-02-25 14:00:00'),
(19, 56, 6, 2, 'Khiếu nại về tốc độ đường truyền wifi', 'Tài xế chê wifi phòng nghỉ yếu không xem được video.', 'REJECTED', 'Tiện ích wifi là dịch vụ cộng thêm miễn phí, không thuộc phạm vi bồi hoàn.', '2026-02-26 14:00:00', '2026-02-26 15:30:00'),
(20, 58, 8, 3, 'Tài xế không chịu trả phí quá giờ 10 phút', 'Xe ra trễ 10 phút hệ thống bắt đầu tính block phạt quá giờ.', 'REJECTED', 'Quy chế miễn trừ chỉ trong 5 phút đầu, sau 5 phút tính phí quá giờ theo quy định.', '2026-02-27 16:00:00', '2026-02-27 17:00:00');
