-- ============================================================
-- V2__seed_demo_data.sql
-- Identity Service Seed Data (Users, Drivers, Merchants, Vehicles)
-- BCrypt password for all demo accounts: 'password123'
-- Hash: '$2a$10$K3MAO7flXmSl4bhJkfoNE.naUcBeVgU4DV434CZWs6RfgR6ot60ce'
-- ============================================================

-- 1. SEED USERS (38 users: 3 Admins, 10 Merchants, 25 Drivers)
INSERT IGNORE INTO users (id, phone, email, full_name, password_hash, role, status, emergency_contact, address, status_reason, created_at, updated_at) VALUES
-- System Admins (IDs 1-3)
(1, '0903829145', 'admin@smartparking.com', 'Nguyễn Hoàng Nam', '$2a$10$K3MAO7flXmSl4bhJkfoNE.naUcBeVgU4DV434CZWs6RfgR6ot60ce', 'ADMIN', 'ACTIVE', '0903829146', 'Số 268 Lý Thường Kiệt, Phường 14, Quận 10, TP. Hồ Chí Minh', NULL, '2026-01-01 08:00:00', '2026-01-01 08:00:00'),
(2, '0918273645', 'mod.lan@smartparking.com', 'Trần Thị Tuyết Lan', '$2a$10$K3MAO7flXmSl4bhJkfoNE.naUcBeVgU4DV434CZWs6RfgR6ot60ce', 'ADMIN', 'ACTIVE', '0918273646', 'Số 45 Lê Duẩn, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh', NULL, '2026-01-05 09:30:00', '2026-01-05 09:30:00'),
(3, '0974829103', 'support.dung@smartparking.com', 'Phạm Quốc Dũng', '$2a$10$K3MAO7flXmSl4bhJkfoNE.naUcBeVgU4DV434CZWs6RfgR6ot60ce', 'ADMIN', 'ACTIVE', '0974829104', 'Số 12 Ung Văn Khiêm, Phường 25, Quận Bình Thạnh, TP. Hồ Chí Minh', NULL, '2026-01-10 10:15:00', '2026-01-10 10:15:00'),

-- Merchants (IDs 4-13)
(4, '0908123456', 'thanh.dongsaigon@gmail.com', 'Lê Văn Thành', '$2a$10$K3MAO7flXmSl4bhJkfoNE.naUcBeVgU4DV434CZWs6RfgR6ot60ce', 'MERCHANT', 'ACTIVE', '0908123457', 'Số 789 Nguyễn Thị Định, Phường Cát Lái, Thành phố Thủ Đức, TP. Hồ Chí Minh', NULL, '2026-01-15 08:30:00', '2026-01-15 08:30:00'),
(5, '0913987654', 'tri.taysaigon@gmail.com', 'Võ Minh Trí', '$2a$10$K3MAO7flXmSl4bhJkfoNE.naUcBeVgU4DV434CZWs6RfgR6ot60ce', 'MERCHANT', 'ACTIVE', '0913987655', 'Số 158 Quốc Lộ 1A, Thị trấn Tân Túc, Huyện Bình Chánh, TP. Hồ Chí Minh', NULL, '2026-01-16 09:00:00', '2026-01-16 09:00:00'),
(6, '0983456789', 'phuc.cangnhabe@gmail.com', 'Đặng Hữu Phúc', '$2a$10$K3MAO7flXmSl4bhJkfoNE.naUcBeVgU4DV434CZWs6RfgR6ot60ce', 'MERCHANT', 'ACTIVE', '0983456780', 'Số 324 Nguyễn Văn Tạo, Xã Long Thới, Huyện Nhà Bè, TP. Hồ Chí Minh', NULL, '2026-01-17 10:00:00', '2026-01-17 10:00:00'),
(7, '0938765432', 'ngan.tansonnhat@gmail.com', 'Hoàng Kim Ngân', '$2a$10$K3MAO7flXmSl4bhJkfoNE.naUcBeVgU4DV434CZWs6RfgR6ot60ce', 'MERCHANT', 'ACTIVE', '0938765433', 'Số 18 Trường Sơn, Phường 2, Quận Tân Bình, TP. Hồ Chí Minh', NULL, '2026-01-18 11:20:00', '2026-01-18 11:20:00'),
(8, '0979112233', 'huy.binhduong@gmail.com', 'Bùi Quang Huy', '$2a$10$K3MAO7flXmSl4bhJkfoNE.naUcBeVgU4DV434CZWs6RfgR6ot60ce', 'MERCHANT', 'ACTIVE', '0979112234', 'Số 88 Đại Lộ Bình Dương, Phường Thuận Giao, Thành phố Thuận An, Bình Dương', NULL, '2026-01-19 14:00:00', '2026-01-19 14:00:00'),
(9, '0909556677', 'sang.phuhuu@gmail.com', 'Trương Tấn Sang', '$2a$10$K3MAO7flXmSl4bhJkfoNE.naUcBeVgU4DV434CZWs6RfgR6ot60ce', 'MERCHANT', 'ACTIVE', '0909556678', 'Số 52 Đường Gò Cát, Phường Phú Hữu, Thành phố Thủ Đức, TP. Hồ Chí Minh', NULL, '2026-01-20 15:45:00', '2026-01-20 15:45:00'),
(10, '0918334455', 'trong.catlai@gmail.com', 'Ngô Đức Trọng', '$2a$10$K3MAO7flXmSl4bhJkfoNE.naUcBeVgU4DV434CZWs6RfgR6ot60ce', 'MERCHANT', 'ACTIVE', '0918334456', 'Số 104 Đồng Văn Cống, Phường Thạnh Mỹ Lợi, Thành phố Thủ Đức, TP. Hồ Chí Minh', NULL, '2026-01-21 08:15:00', '2026-01-21 08:15:00'),
(11, '0988667788', 'trieu.songthan@gmail.com', 'Nguyễn Đình Triều', '$2a$10$K3MAO7flXmSl4bhJkfoNE.naUcBeVgU4DV434CZWs6RfgR6ot60ce', 'MERCHANT', 'ACTIVE', '0988667789', 'Số 45 Quốc Lộ 1K, Phường Dĩ An, Thành phố Dĩ An, Bình Dương', NULL, '2026-01-22 09:30:00', '2026-01-22 09:30:00'),
(12, '0933221100', 'nam.benluc@gmail.com', 'Đinh Hoài Nam', '$2a$10$K3MAO7flXmSl4bhJkfoNE.naUcBeVgU4DV434CZWs6RfgR6ot60ce', 'MERCHANT', 'ACTIVE', '0933221101', 'Số 204 Quốc Lộ 1A, Thị trấn Bến Lức, Huyện Bến Lức, Long An', NULL, '2026-01-23 10:45:00', '2026-01-23 10:45:00'),
(13, '0902889900', 'cuong.bienhoa@gmail.com', 'Lâm Quốc Cường', '$2a$10$K3MAO7flXmSl4bhJkfoNE.naUcBeVgU4DV434CZWs6RfgR6ot60ce', 'MERCHANT', 'ACTIVE', '0902889901', 'Số 316 Xa Lộ Hà Nội, Phường Tân Biên, Thành phố Biên Hòa, Đồng Nai', NULL, '2026-01-24 13:20:00', '2026-01-24 13:20:00'),

-- Drivers (IDs 14-38)
(14, '0909128374', 'hung.nguyen@smartparking.com', 'Nguyễn Văn Hùng', '$2a$10$K3MAO7flXmSl4bhJkfoNE.naUcBeVgU4DV434CZWs6RfgR6ot60ce', 'DRIVER', 'ACTIVE', '0909128375', 'Số 42 Đường số 5, Phường Linh Xuân, Thành phố Thủ Đức, TP. Hồ Chí Minh', NULL, '2026-02-01 07:00:00', '2026-02-01 07:00:00'),
(15, '0918239485', 'tuan.tran@smartparking.com', 'Trần Quốc Tuấn', '$2a$10$K3MAO7flXmSl4bhJkfoNE.naUcBeVgU4DV434CZWs6RfgR6ot60ce', 'DRIVER', 'ACTIVE', '0918239486', 'Số 89 Tân Kỳ Tân Quý, Phường Tân Sơn Nhì, Quận Tân Phú, TP. Hồ Chí Minh', NULL, '2026-02-02 08:15:00', '2026-02-02 08:15:00'),
(16, '0978340596', 'long.le@smartparking.com', 'Lê Hoàng Long', '$2a$10$K3MAO7flXmSl4bhJkfoNE.naUcBeVgU4DV434CZWs6RfgR6ot60ce', 'DRIVER', 'ACTIVE', '0978340597', 'Số 234 Nguyễn Văn Linh, Phường Tân Thuận Tây, Quận 7, TP. Hồ Chí Minh', NULL, '2026-02-03 09:20:00', '2026-02-03 09:20:00'),
(17, '0989451607', 'nam.pham@smartparking.com', 'Phạm Văn Nam', '$2a$10$K3MAO7flXmSl4bhJkfoNE.naUcBeVgU4DV434CZWs6RfgR6ot60ce', 'DRIVER', 'ACTIVE', '0989451608', 'Số 15 Võ Văn Vân, Xã Vĩnh Lộc B, Huyện Bình Chánh, TP. Hồ Chí Minh', NULL, '2026-02-04 10:30:00', '2026-02-04 10:30:00'),
(18, '0934562718', 'duc.do@smartparking.com', 'Đỗ Minh Đức', '$2a$10$K3MAO7flXmSl4bhJkfoNE.naUcBeVgU4DV434CZWs6RfgR6ot60ce', 'DRIVER', 'ACTIVE', '0934562719', 'Số 67 Đường số 8, Phường Tăng Nhơn Phú B, Thành phố Thủ Đức, TP. Hồ Chí Minh', NULL, '2026-02-05 11:45:00', '2026-02-05 11:45:00'),
(19, '0902673829', 'tai.huynh@smartparking.com', 'Huỳnh Văn Tài', '$2a$10$K3MAO7flXmSl4bhJkfoNE.naUcBeVgU4DV434CZWs6RfgR6ot60ce', 'DRIVER', 'ACTIVE', '0902673830', 'Số 512 Hương Lộ 2, Phường Bình Trị Đông, Quận Bình Tân, TP. Hồ Chí Minh', NULL, '2026-02-06 13:00:00', '2026-02-06 13:00:00'),
(20, '0917784930', 'phuoc.phan@smartparking.com', 'Phan Hữu Phước', '$2a$10$K3MAO7flXmSl4bhJkfoNE.naUcBeVgU4DV434CZWs6RfgR6ot60ce', 'DRIVER', 'ACTIVE', '0917784931', 'Số 114 Nguyễn Ảnh Thủ, Phường Hiệp Thành, Quận 12, TP. Hồ Chí Minh', NULL, '2026-02-07 14:15:00', '2026-02-07 14:15:00'),
(21, '0988895041', 'dang.vu@smartparking.com', 'Vũ Hải Đăng', '$2a$10$K3MAO7flXmSl4bhJkfoNE.naUcBeVgU4DV434CZWs6RfgR6ot60ce', 'DRIVER', 'ACTIVE', '0988895042', 'Số 33 Đỗ Xuân Hợp, Phường Phước Long B, Thành phố Thủ Đức, TP. Hồ Chí Minh', NULL, '2026-02-08 15:30:00', '2026-02-08 15:30:00'),
(22, '0973906152', 'danh.trinh@smartparking.com', 'Trịnh Công Danh', '$2a$10$K3MAO7flXmSl4bhJkfoNE.naUcBeVgU4DV434CZWs6RfgR6ot60ce', 'DRIVER', 'ACTIVE', '0973906153', 'Số 90 Bến Bình Đông, Phường 14, Quận 8, TP. Hồ Chí Minh', NULL, '2026-02-09 16:40:00', '2026-02-09 16:40:00'),
(23, '0939017263', 'khoa.mai@smartparking.com', 'Mai Văn Khoa', '$2a$10$K3MAO7flXmSl4bhJkfoNE.naUcBeVgU4DV434CZWs6RfgR6ot60ce', 'DRIVER', 'ACTIVE', '0939017264', 'Số 19 Đường số 1, Phường An Phú, Thành phố Thủ Đức, TP. Hồ Chí Minh', NULL, '2026-02-10 17:50:00', '2026-02-10 17:50:00'),
(24, '0908128394', 'vinh.dinh@smartparking.com', 'Đinh Thế Vinh', '$2a$10$K3MAO7flXmSl4bhJkfoNE.naUcBeVgU4DV434CZWs6RfgR6ot60ce', 'DRIVER', 'ACTIVE', '0908128395', 'Số 74 Bà Hom, Phường 13, Quận 6, TP. Hồ Chí Minh', NULL, '2026-02-11 08:20:00', '2026-02-11 08:20:00'),
(25, '0919239405', 'tung.cao@smartparking.com', 'Cao Thanh Tùng', '$2a$10$K3MAO7flXmSl4bhJkfoNE.naUcBeVgU4DV434CZWs6RfgR6ot60ce', 'DRIVER', 'ACTIVE', '0919239406', 'Số 350 Lê Đức Thọ, Phường 16, Quận Gò Vấp, TP. Hồ Chí Minh', NULL, '2026-02-12 09:30:00', '2026-02-12 09:30:00'),
(26, '0987340516', 'nghia.luong@smartparking.com', 'Lương Trọng Nghĩa', '$2a$10$K3MAO7flXmSl4bhJkfoNE.naUcBeVgU4DV434CZWs6RfgR6ot60ce', 'DRIVER', 'ACTIVE', '0987340517', 'Số 125 Phạm Văn Đồng, Phường 1, Quận Gò Vấp, TP. Hồ Chí Minh', NULL, '2026-02-13 10:40:00', '2026-02-13 10:40:00'),
(27, '0976451627', 'khai.ta@smartparking.com', 'Tạ Quang Khải', '$2a$10$K3MAO7flXmSl4bhJkfoNE.naUcBeVgU4DV434CZWs6RfgR6ot60ce', 'DRIVER', 'ACTIVE', '0976451628', 'Số 41 Hoàng Hữu Nam, Phường Long Thạnh Mỹ, Thành phố Thủ Đức, TP. Hồ Chí Minh', NULL, '2026-02-14 11:50:00', '2026-02-14 11:50:00'),
(28, '0935562738', 'loi.duong@smartparking.com', 'Dương Văn Lợi', '$2a$10$K3MAO7flXmSl4bhJkfoNE.naUcBeVgU4DV434CZWs6RfgR6ot60ce', 'DRIVER', 'ACTIVE', '0935562739', 'Số 63 Liên Tỉnh 5, Phường 5, Quận 8, TP. Hồ Chí Minh', NULL, '2026-02-15 13:10:00', '2026-02-15 13:10:00'),
(29, '0904673849', 'tam.doan@smartparking.com', 'Đoàn Minh Tâm', '$2a$10$K3MAO7flXmSl4bhJkfoNE.naUcBeVgU4DV434CZWs6RfgR6ot60ce', 'DRIVER', 'ACTIVE', '0904673850', 'Số 208 Nguyễn Hữu Cảnh, Phường 22, Quận Bình Thạnh, TP. Hồ Chí Minh', NULL, '2026-02-16 14:20:00', '2026-02-16 14:20:00'),
(30, '0916784950', 'phat.lam@smartparking.com', 'Lâm Vĩnh Phát', '$2a$10$K3MAO7flXmSl4bhJkfoNE.naUcBeVgU4DV434CZWs6RfgR6ot60ce', 'DRIVER', 'ACTIVE', '0916784951', 'Số 15 Kha Vạn Cân, Phường Hiệp Bình Chánh, Thành phố Thủ Đức, TP. Hồ Chí Minh', NULL, '2026-02-17 15:30:00', '2026-02-17 15:30:00'),
(31, '0985895061', 'thai.dang@smartparking.com', 'Đặng Quốc Thái', '$2a$10$K3MAO7flXmSl4bhJkfoNE.naUcBeVgU4DV434CZWs6RfgR6ot60ce', 'DRIVER', 'ACTIVE', '0985895062', 'Số 82 Huỳnh Tấn Phát, Phường Tân Phú, Quận 7, TP. Hồ Chí Minh', NULL, '2026-02-18 16:40:00', '2026-02-18 16:40:00'),
(32, '0972906172', 'truong.ha@smartparking.com', 'Hà Xuân Trường', '$2a$10$K3MAO7flXmSl4bhJkfoNE.naUcBeVgU4DV434CZWs6RfgR6ot60ce', 'DRIVER', 'ACTIVE', '0972906173', 'Số 95 Tô Ngọc Vân, Phường Tam Phú, Thành phố Thủ Đức, TP. Hồ Chí Minh', NULL, '2026-02-19 17:50:00', '2026-02-19 17:50:00'),
(33, '0938017283', 'bay.thai@smartparking.com', 'Thái Văn Bảy', '$2a$10$K3MAO7flXmSl4bhJkfoNE.naUcBeVgU4DV434CZWs6RfgR6ot60ce', 'DRIVER', 'ACTIVE', '0938017284', 'Số 29 Song Hành, Phường An Phú, Thành phố Thủ Đức, TP. Hồ Chí Minh', NULL, '2026-02-20 08:30:00', '2026-02-20 08:30:00'),
(34, '0907128394', 'hau.truong@smartparking.com', 'Trương Công Hậu', '$2a$10$K3MAO7flXmSl4bhJkfoNE.naUcBeVgU4DV434CZWs6RfgR6ot60ce', 'DRIVER', 'ACTIVE', '0907128395', 'Số 112 Tỉnh Lộ 10, Phường Tân Tạo, Quận Bình Tân, TP. Hồ Chí Minh', NULL, '2026-02-21 09:40:00', '2026-02-21 09:40:00'),
(35, '0915239405', 'hung.luu@smartparking.com', 'Lưu Quang Hưng', '$2a$10$K3MAO7flXmSl4bhJkfoNE.naUcBeVgU4DV434CZWs6RfgR6ot60ce', 'DRIVER', 'ACTIVE', '0915239406', 'Số 76 Quốc Lộ 13, Phường Hiệp Bình Phước, Thành phố Thủ Đức, TP. Hồ Chí Minh', NULL, '2026-02-22 10:50:00', '2026-02-22 10:50:00'),
(36, '0984340516', 'binh.ton@smartparking.com', 'Tôn Thất Bình', '$2a$10$K3MAO7flXmSl4bhJkfoNE.naUcBeVgU4DV434CZWs6RfgR6ot60ce', 'DRIVER', 'ACTIVE', '0984340517', 'Số 304 Thoại Ngọc Hầu, Phường Phú Thạnh, Quận Tân Phú, TP. Hồ Chí Minh', NULL, '2026-02-23 12:00:00', '2026-02-23 12:00:00'),
(37, '0971451627', 'sang.ngo@smartparking.com', 'Ngô Thanh Sang', '$2a$10$K3MAO7flXmSl4bhJkfoNE.naUcBeVgU4DV434CZWs6RfgR6ot60ce', 'DRIVER', 'INACTIVE', '0971451628', 'Số 18 Đường số 4, Phường Linh Tây, Thành phố Thủ Đức, TP. Hồ Chí Minh', 'Tài khoản chưa hoàn tất xác thực thông tin giấy phép lái xe', '2026-02-24 13:10:00', '2026-02-24 13:10:00'),
(38, '0932562738', 'canh.chu@smartparking.com', 'Chu Văn Cảnh', '$2a$10$K3MAO7flXmSl4bhJkfoNE.naUcBeVgU4DV434CZWs6RfgR6ot60ce', 'DRIVER', 'BANNED', '0932562739', 'Số 59 Vườn Lài, Phường An Phú Đông, Quận 12, TP. Hồ Chí Minh', 'Vi phạm nhiều lần: Bỏ xe tại bãi quá thời hạn và không thanh toán phí', '2026-02-25 14:20:00', '2026-02-25 14:20:00');

-- 2. SEED MERCHANTS (10 records mapped to users 4-13)
INSERT IGNORE INTO merchants (id, user_id, citizen_id, business_license, bank_name, bank_account_num, wallet_balance, created_at, updated_at) VALUES
(1, 4, '079085001234', 'GPKD-0315891234', 'Vietcombank', '0071001234567', 48500000.00, '2026-01-15 08:30:00', '2026-01-15 08:30:00'),
(2, 5, '079082002345', 'GPKD-0316782345', 'Techcombank', '19034567890123', 62300000.00, '2026-01-16 09:00:00', '2026-01-16 09:00:00'),
(3, 6, '079079003456', 'GPKD-0317893456', 'BIDV', '31410001234567', 89200000.00, '2026-01-17 10:00:00', '2026-01-17 10:00:00'),
(4, 7, '079188004567', 'GPKD-0318904567', 'MB Bank', '0800100123456', 35400000.00, '2026-01-18 11:20:00', '2026-01-18 11:20:00'),
(5, 8, '074084005678', 'GPKD-3702895678', 'VPBank', '158900123456', 74100000.00, '2026-01-19 14:00:00', '2026-01-19 14:00:00'),
(6, 9, '079080006789', 'GPKD-0319016789', 'ACB', '2468101214', 51000000.00, '2026-01-20 15:45:00', '2026-01-20 15:45:00'),
(7, 10, '079075007890', 'GPKD-0319127890', 'Sacombank', '060123456789', 93800000.00, '2026-01-21 08:15:00', '2026-01-21 08:15:00'),
(8, 11, '074086008901', 'GPKD-3703018901', 'TPBank', '02345678901', 41600000.00, '2026-01-22 09:30:00', '2026-01-22 09:30:00'),
(9, 12, '080083009012', 'GPKD-1101929012', 'VietinBank', '101867123456', 38900000.00, '2026-01-23 10:45:00', '2026-01-23 10:45:00'),
(10, 13, '075087010123', 'GPKD-3603450123', 'HDBank', '012704012345', 67500000.00, '2026-01-24 13:20:00', '2026-01-24 13:20:00');

-- 3. SEED DRIVERS (25 records mapped to users 14-38)
INSERT IGNORE INTO drivers (id, user_id, full_name, rating_average, created_at, updated_at) VALUES
(1, 14, 'Nguyễn Văn Hùng', 4.90, '2026-02-01 07:00:00', '2026-02-01 07:00:00'),
(2, 15, 'Trần Quốc Tuấn', 4.85, '2026-02-02 08:15:00', '2026-02-02 08:15:00'),
(3, 16, 'Lê Hoàng Long', 5.00, '2026-02-03 09:20:00', '2026-02-03 09:20:00'),
(4, 17, 'Phạm Văn Nam', 4.70, '2026-02-04 10:30:00', '2026-02-04 10:30:00'),
(5, 18, 'Đỗ Minh Đức', 4.65, '2026-02-05 11:45:00', '2026-02-05 11:45:00'),
(6, 19, 'Huỳnh Văn Tài', 4.95, '2026-02-06 13:00:00', '2026-02-06 13:00:00'),
(7, 20, 'Phan Hữu Phước', 4.80, '2026-02-07 14:15:00', '2026-02-07 14:15:00'),
(8, 21, 'Vũ Hải Đăng', 4.90, '2026-02-08 15:30:00', '2026-02-08 15:30:00'),
(9, 22, 'Trịnh Công Danh', 4.50, '2026-02-09 16:40:00', '2026-02-09 16:40:00'),
(10, 23, 'Mai Văn Khoa', 4.75, '2026-02-10 17:50:00', '2026-02-10 17:50:00'),
(11, 24, 'Đinh Thế Vinh', 4.85, '2026-02-11 08:20:00', '2026-02-11 08:20:00'),
(12, 25, 'Cao Thanh Tùng', 4.60, '2026-02-12 09:30:00', '2026-02-12 09:30:00'),
(13, 26, 'Lương Trọng Nghĩa', 4.90, '2026-02-13 10:40:00', '2026-02-13 10:40:00'),
(14, 27, 'Tạ Quang Khải', 4.95, '2026-02-14 11:50:00', '2026-02-14 11:50:00'),
(15, 28, 'Dương Văn Lợi', 4.70, '2026-02-15 13:10:00', '2026-02-15 13:10:00'),
(16, 29, 'Đoàn Minh Tâm', 4.80, '2026-02-16 14:20:00', '2026-02-16 14:20:00'),
(17, 30, 'Lâm Vĩnh Phát', 4.85, '2026-02-17 15:30:00', '2026-02-17 15:30:00'),
(18, 31, 'Đặng Quốc Thái', 5.00, '2026-02-18 16:40:00', '2026-02-18 16:40:00'),
(19, 32, 'Hà Xuân Trường', 4.65, '2026-02-19 17:50:00', '2026-02-19 17:50:00'),
(20, 33, 'Thái Văn Bảy', 4.75, '2026-02-20 08:30:00', '2026-02-20 08:30:00'),
(21, 34, 'Trương Công Hậu', 4.90, '2026-02-21 09:40:00', '2026-02-21 09:40:00'),
(22, 35, 'Lưu Quang Hưng', 4.80, '2026-02-22 10:50:00', '2026-02-22 10:50:00'),
(23, 36, 'Tôn Thất Bình', 4.85, '2026-02-23 12:00:00', '2026-02-23 12:00:00'),
(24, 37, 'Ngô Thanh Sang', 4.20, '2026-02-24 13:10:00', '2026-02-24 13:10:00'),
(25, 38, 'Chu Văn Cảnh', 3.80, '2026-02-25 14:20:00', '2026-02-25 14:20:00');

-- 4. SEED VEHICLES (45 vehicles across 5 vehicle types)
INSERT IGNORE INTO vehicles (id, driver_id, license_plate, vehicle_type, weight_ton, brand_model, status, created_at, updated_at) VALUES
-- Containers (Đầu kéo & Rơ-moóc)
(1, 1, '51C-482.91', 'CONTAINER', 32.50, 'Chenglong H7 420HP', 'ACTIVE', '2026-02-01 07:10:00', '2026-02-01 07:10:00'),
(2, 2, '50H-192.83', 'CONTAINER', 34.00, 'Howo A7 375HP', 'ACTIVE', '2026-02-02 08:25:00', '2026-02-02 08:25:00'),
(3, 3, '60C-582.10', 'CONTAINER', 33.00, 'Hyundai HD1000', 'ACTIVE', '2026-02-03 09:35:00', '2026-02-03 09:35:00'),
(4, 4, '61C-739.24', 'CONTAINER', 31.80, 'Maxxforce International', 'ACTIVE', '2026-02-04 10:45:00', '2026-02-04 10:45:00'),
(5, 5, '62C-381.90', 'CONTAINER', 32.00, 'Daewoo Novus SE', 'ACTIVE', '2026-02-05 11:55:00', '2026-02-05 11:55:00'),
(6, 6, '72C-294.15', 'CONTAINER', 35.00, 'Freightliner Cascadia', 'ACTIVE', '2026-02-06 13:15:00', '2026-02-06 13:15:00'),
(7, 7, '51D-839.42', 'CONTAINER', 32.50, 'Dongfeng D310', 'ACTIVE', '2026-02-07 14:30:00', '2026-02-07 14:30:00'),
(8, 8, '50H-928.37', 'CONTAINER', 34.50, 'Isuzu Giga 420', 'ACTIVE', '2026-02-08 15:45:00', '2026-02-08 15:45:00'),
(9, 9, '60C-619.48', 'CONTAINER', 31.50, 'Scania R500', 'ACTIVE', '2026-02-09 16:55:00', '2026-02-09 16:55:00'),
(10, 10, '61C-847.19', 'CONTAINER', 33.50, 'MAN TGX 26.440', 'ACTIVE', '2026-02-10 18:05:00', '2026-02-10 18:05:00'),

-- Heavy Trucks (Xe tải nặng 3-4 chân 14-22 tấn)
(11, 11, '51C-592.83', 'HEAVY_TRUCK', 15.00, 'Hino 500 Series FL', 'ACTIVE', '2026-02-11 08:35:00', '2026-02-11 08:35:00'),
(12, 12, '51C-681.94', 'HEAVY_TRUCK', 14.50, 'Isuzu FVM 15 tấn', 'ACTIVE', '2026-02-12 09:45:00', '2026-02-12 09:45:00'),
(13, 13, '60C-492.05', 'HEAVY_TRUCK', 18.00, 'Hyundai HD320 4 chân', 'ACTIVE', '2026-02-13 10:55:00', '2026-02-13 10:55:00'),
(14, 14, '61C-384.16', 'HEAVY_TRUCK', 15.50, 'Chenglong 3 chân 260HP', 'ACTIVE', '2026-02-14 12:05:00', '2026-02-14 12:05:00'),
(15, 15, '62C-719.27', 'HEAVY_TRUCK', 14.00, 'Fuso Fighter 3 chân', 'ACTIVE', '2026-02-15 13:25:00', '2026-02-15 13:25:00'),
(16, 16, '51D-472.38', 'HEAVY_TRUCK', 16.00, 'Howo Man VX 3 chân', 'ACTIVE', '2026-02-16 14:35:00', '2026-02-16 14:35:00'),
(17, 17, '50H-381.49', 'HEAVY_TRUCK', 15.00, 'Thaco Auman C160', 'ACTIVE', '2026-02-17 15:45:00', '2026-02-17 15:45:00'),
(18, 18, '60C-829.50', 'HEAVY_TRUCK', 19.00, 'Dongfeng 4 chân ISL315', 'ACTIVE', '2026-02-18 16:55:00', '2026-02-18 16:55:00'),
(19, 19, '61C-938.61', 'HEAVY_TRUCK', 14.80, 'Hino 500 Series FG', 'ACTIVE', '2026-02-19 18:05:00', '2026-02-19 18:05:00'),
(20, 20, '72C-548.72', 'HEAVY_TRUCK', 15.20, 'Isuzu FVZ 15 tấn', 'ACTIVE', '2026-02-20 08:45:00', '2026-02-20 08:45:00'),

-- Light Trucks (Xe tải nhẹ 1.5 - 5 tấn chở hàng nội đô)
(21, 21, '51D-283.94', 'LIGHT_TRUCK', 2.40, 'Kia K250 2.4 tấn', 'ACTIVE', '2026-02-21 09:55:00', '2026-02-21 09:55:00'),
(22, 22, '51D-394.05', 'LIGHT_TRUCK', 1.90, 'Hyundai Porter H150', 'ACTIVE', '2026-02-22 11:05:00', '2026-02-22 11:05:00'),
(23, 23, '50H-405.16', 'LIGHT_TRUCK', 3.50, 'Isuzu QKR 270', 'ACTIVE', '2026-02-23 12:15:00', '2026-02-23 12:15:00'),
(24, 24, '60C-516.27', 'LIGHT_TRUCK', 4.90, 'Hino 300 Series XZU', 'ACTIVE', '2026-02-24 13:25:00', '2026-02-24 13:25:00'),
(25, 25, '61C-627.38', 'LIGHT_TRUCK', 2.50, 'Thaco Ollin 500B', 'ACTIVE', '2026-02-25 14:35:00', '2026-02-25 14:35:00'),
(26, 1, '51D-738.49', 'LIGHT_TRUCK', 3.49, 'Hyundai Mighty EX8', 'ACTIVE', '2026-02-26 08:00:00', '2026-02-26 08:00:00'),
(27, 2, '51D-849.50', 'LIGHT_TRUCK', 1.99, 'Isuzu NMR 310', 'ACTIVE', '2026-02-26 09:00:00', '2026-02-26 09:00:00'),
(28, 3, '50H-950.61', 'LIGHT_TRUCK', 2.20, 'Fuso Canter 4.99', 'ACTIVE', '2026-02-26 10:00:00', '2026-02-26 10:00:00'),
(29, 4, '60C-061.72', 'LIGHT_TRUCK', 3.50, 'JAC N350 Plus', 'ACTIVE', '2026-02-26 11:00:00', '2026-02-26 11:00:00'),
(30, 5, '61C-172.83', 'LIGHT_TRUCK', 1.50, 'Suzuki Carry Pro', 'ACTIVE', '2026-02-26 12:00:00', '2026-02-26 12:00:00'),

-- Bus / Coach (Xe khách 16 - 47 chỗ)
(31, 6, '51B-183.94', 'BUS_COACH', 12.00, 'Thaco Mobihome 36 giường', 'ACTIVE', '2026-02-26 13:00:00', '2026-02-26 13:00:00'),
(32, 7, '51B-294.05', 'BUS_COACH', 14.00, 'Hyundai Universe 47 chỗ', 'ACTIVE', '2026-02-26 14:00:00', '2026-02-26 14:00:00'),
(33, 8, '50F-305.16', 'BUS_COACH', 10.50, 'Tracomeco Global 29 chỗ', 'ACTIVE', '2026-02-26 15:00:00', '2026-02-26 15:00:00'),
(34, 9, '60B-416.27', 'BUS_COACH', 13.50, 'Samco Primas 34 phòng', 'ACTIVE', '2026-02-26 16:00:00', '2026-02-26 16:00:00'),
(35, 10, '61B-527.38', 'BUS_COACH', 4.50, 'Ford Transit 16 chỗ', 'ACTIVE', '2026-02-26 17:00:00', '2026-02-26 17:00:00'),
(36, 11, '72B-638.49', 'BUS_COACH', 4.80, 'Hyundai Solati 16 chỗ', 'ACTIVE', '2026-02-26 18:00:00', '2026-02-26 18:00:00'),
(37, 12, '51B-749.50', 'BUS_COACH', 14.50, 'Kia Granbird 45 chỗ', 'ACTIVE', '2026-02-27 08:30:00', '2026-02-27 08:30:00'),

-- Sedan & SUV (Bán tải / 7 chỗ)
(38, 13, '51K-850.61', 'SEDAN_SUV', 2.20, 'Toyota Fortuner Legender', 'ACTIVE', '2026-02-27 09:30:00', '2026-02-27 09:30:00'),
(39, 14, '51K-961.72', 'SEDAN_SUV', 2.30, 'Ford Everest Titanium', 'ACTIVE', '2026-02-27 10:30:00', '2026-02-27 10:30:00'),
(40, 15, '50E-072.83', 'SEDAN_SUV', 2.40, 'Ford Ranger Wildtrak 4x4', 'ACTIVE', '2026-02-27 11:30:00', '2026-02-27 11:30:00'),
(41, 16, '60A-183.94', 'SEDAN_SUV', 2.10, 'Mitsubishi Pajero Sport', 'ACTIVE', '2026-02-27 12:30:00', '2026-02-27 12:30:00'),
(42, 17, '61K-294.05', 'SEDAN_SUV', 2.35, 'Toyota Hilux Adventure', 'ACTIVE', '2026-02-27 13:30:00', '2026-02-27 13:30:00'),
(43, 18, '51K-305.16', 'SEDAN_SUV', 1.80, 'Hyundai SantaFe Hybrid', 'ACTIVE', '2026-02-27 14:30:00', '2026-02-27 14:30:00'),
(44, 24, '51D-999.01', 'LIGHT_TRUCK', 2.40, 'Thaco Towner 990', 'INACTIVE', '2026-02-27 15:30:00', '2026-02-27 15:30:00'),
(45, 25, '51C-888.02', 'HEAVY_TRUCK', 15.00, 'Dongfeng Hoàng Huy', 'INACTIVE', '2026-02-27 16:30:00', '2026-02-27 16:30:00');

-- 5. SEED AUDIT LOGS (Admin actions & system traces)
INSERT IGNORE INTO audit_logs (id, actor_user_id, target_id, target_type, action, detail, result, created_at) VALUES
(1, 1, 4, 'MERCHANT', 'APPROVE_MERCHANT', 'Phê duyệt hồ sơ merchant Công Ty Vận Tải Đông Sài Gòn', 'SUCCESS', '2026-01-15 09:00:00'),
(2, 1, 5, 'MERCHANT', 'APPROVE_MERCHANT', 'Phê duyệt hồ sơ merchant Hợp Tác Xã Vận Tải Tây Sài Gòn', 'SUCCESS', '2026-01-16 09:30:00'),
(3, 2, 6, 'MERCHANT', 'APPROVE_MERCHANT', 'Phê duyệt hồ sơ merchant Công Ty TNHH Logistics Cảng Biển & Nhà Bè', 'SUCCESS', '2026-01-17 10:30:00'),
(4, 2, 7, 'MERCHANT', 'APPROVE_MERCHANT', 'Phê duyệt hồ sơ merchant Bãi Xe Nội Đô & Tiếp Vận Tân Sơn Nhất', 'SUCCESS', '2026-01-18 12:00:00'),
(5, 1, 8, 'MERCHANT', 'APPROVE_MERCHANT', 'Phê duyệt hồ sơ merchant Kho Bãi Cửa Ngõ Bình Dương', 'SUCCESS', '2026-01-19 14:30:00'),
(6, 3, 37, 'USER', 'SUSPEND_DRIVER', 'Tạm ngưng tài khoản driver Ngô Thanh Sang do thiếu bằng lái', 'SUCCESS', '2026-02-24 13:30:00'),
(7, 1, 38, 'USER', 'BAN_DRIVER', 'Khóa vĩnh viễn tài khoản Chu Văn Cảnh do bỏ xe và trốn tránh trách nhiệm', 'SUCCESS', '2026-02-25 15:00:00');
