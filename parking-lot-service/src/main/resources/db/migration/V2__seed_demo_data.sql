-- ============================================================
-- V2__seed_demo_data.sql
-- Parking Lot Service Seed Data (30 Parking Lots, Slots, Pricing Rules, Images)
-- ============================================================

-- 1. SEED PARKING LOTS (30 realistic lots across TP.HCM, Bình Dương, Đồng Nai, Long An)
INSERT IGNORE INTO parking_lots (id, merchant_id, name, address, latitude, longitude, status, rating_average, review_reason, reviewed_by, reviewed_at, created_at, updated_at) VALUES
-- Merchant 1: Đông Sài Gòn (Thủ Đức - Cát Lái)
(1, 1, 'Bãi Đỗ Xe Container Cát Lái - Cổng G', 'Số 789 Nguyễn Thị Định, Phường Cát Lái, Thành phố Thủ Đức, TP. Hồ Chí Minh', 10.76214500, 106.78652100, 'APPROVED', 4.90, 'Hạ tầng bãi đỗ bê tông chịu lực tốt, trang bị PCCC tự động đầy đủ', 1, '2026-01-20 09:00:00', '2026-01-15 08:30:00', '2026-01-20 09:00:00'),
(2, 1, 'Bãi Xe Vận Tải Thủ Đức - Xa Lộ Hà Nội', 'Số 123 Xa Lộ Hà Nội, Phường Linh Trung, Thành phố Thủ Đức, TP. Hồ Chí Minh', 10.86543100, 106.77259000, 'APPROVED', 4.80, 'Vị trí đắc địa ngay mặt tiền Xa Lộ Hà Nội, có trạm rửa xe và căn tin', 1, '2026-01-20 10:00:00', '2026-01-15 09:00:00', '2026-01-20 10:00:00'),
(3, 1, 'Bãi Xe Tải Trung Chuyển Lê Văn Việt', 'Số 10 Lê Văn Việt, Phường Hiệp Phú, Thành phố Thủ Đức, TP. Hồ Chí Minh', 10.84652100, 106.79234500, 'APPROVED', 4.50, 'Đầy đủ hệ thống camera an ninh 24/7 và cổng rào barie tự động', 2, '2026-01-21 11:00:00', '2026-01-16 10:00:00', '2026-01-21 11:00:00'),

-- Merchant 2: Tây Sài Gòn (Bình Chánh, Bình Tân)
(4, 2, 'Bãi Xe Tải & Container Bình Chánh - QL.50', 'Km 8 Quốc Lộ 50, Xã Bình Chánh, Huyện Bình Chánh, TP. Hồ Chí Minh', 10.68720000, 106.56780000, 'APPROVED', 4.60, 'Mặt bằng rộng rãi, thuận tiện đón xe từ các tỉnh Miền Tây về TP.HCM', 1, '2026-01-22 09:30:00', '2026-01-17 09:00:00', '2026-01-22 09:30:00'),
(5, 2, 'Bãi Trung Chuyển Hàng Hóa Tân Túc - QL.1A', 'Số 158 Quốc Lộ 1A, Thị trấn Tân Túc, Huyện Bình Chánh, TP. Hồ Chí Minh', 10.69580000, 106.57340000, 'APPROVED', 4.70, 'Gần nút giao cao tốc Trung Lương, có xưởng sửa chữa lốp và cứu hộ', 2, '2026-01-22 14:00:00', '2026-01-17 11:00:00', '2026-01-22 14:00:00'),
(6, 2, 'Bãi Đỗ Xe Tải Bình Tân - Võ Văn Kiệt', 'Đường Võ Văn Kiệt, Phường An Lạc, Quận Bình Tân, TP. Hồ Chí Minh', 10.74120000, 106.61250000, 'APPROVED', 4.40, 'Đường vào thông thoáng, giới hạn tải trọng dưới 10 tấn ban ngày', 1, '2026-01-23 10:00:00', '2026-01-18 08:30:00', '2026-01-23 10:00:00'),
(7, 2, 'Bãi Xe Đêm Cửa Ngõ Miền Tây - Vĩnh Lộc', 'Đường Trần Văn Giàu, Xã Phạm Văn Hai, Huyện Bình Chánh, TP. Hồ Chí Minh', 10.77890000, 106.53450000, 'APPROVED', 4.30, 'Bãi đất nện kiên cố, bảo vệ túc trực 2 ca ngày đêm', 2, '2026-01-23 15:30:00', '2026-01-18 13:00:00', '2026-01-23 15:30:00'),

-- Merchant 3: Cảng & Nhà Bè (Nguyễn Văn Tạo, Nhơn Trạch, Q7)
(8, 3, 'Bãi Container Cảng Quốc Tế Hiệp Phước', 'Đường Số 1, KCN Hiệp Phước, Xã Hiệp Phước, Huyện Nhà Bè, TP. Hồ Chí Minh', 10.64560000, 106.74890000, 'APPROVED', 4.85, 'Tiêu chuẩn cảng biển quốc tế, có cân điện tử 120 tấn và cẩu vỏ container', 1, '2026-01-24 09:00:00', '2026-01-19 09:00:00', '2026-01-24 09:00:00'),
(9, 3, 'Bãi Xe Vận Tải Nhà Bè - Nguyễn Văn Tạo', 'Số 324 Nguyễn Văn Tạo, Xã Long Thới, Huyện Nhà Bè, TP. Hồ Chí Minh', 10.66540000, 106.73210000, 'APPROVED', 4.65, 'Thích hợp cho xe tải nặng và bồn chở xăng dầu', 2, '2026-01-24 11:30:00', '2026-01-19 10:30:00', '2026-01-24 11:30:00'),
(10, 3, 'Bãi Staging Xe Container Nhơn Trạch - CT.01', 'Khu vực nút giao 319, Huyện Nhơn Trạch, Tỉnh Đồng Nai', 10.71860000, 106.89040000, 'APPROVED', 4.75, 'Điểm dừng chân lý tưởng trước khi lên cao tốc Long Thành - Dầu Giây', 1, '2026-01-25 14:00:00', '2026-01-20 08:00:00', '2026-01-25 14:00:00'),
(11, 3, 'Bãi Xe Trung Chuyển Nguyễn Văn Linh - Quận 7', 'Số 500 Nguyễn Văn Linh, Phường Tân Phú, Quận 7, TP. Hồ Chí Minh', 10.72980000, 106.72150000, 'APPROVED', 4.40, 'Vị trí kết nối nhanh Khu chế xuất Tân Thuận và Đô thị Phú Mỹ Hưng', 2, '2026-01-25 16:00:00', '2026-01-20 14:00:00', '2026-01-25 16:00:00'),

-- Merchant 4: Nội Đô & Sân Bay Tân Sơn Nhất
(12, 4, 'Bãi Staging Sân Bay Quốc Tế Tân Sơn Nhất', 'Số 18 Trường Sơn, Phường 2, Quận Tân Bình, TP. Hồ Chí Minh', 10.81820000, 106.65890000, 'APPROVED', 4.60, 'Khu vực gom xe giao nhận hàng chuyển phát nhanh hàng không', 1, '2026-01-26 09:30:00', '2026-01-21 08:30:00', '2026-01-26 09:30:00'),
(13, 4, 'Bãi Xe Tải Nhẹ Cộng Hòa - Tân Bình', 'Số 204 Cộng Hòa, Phường 13, Quận Tân Bình, TP. Hồ Chí Minh', 10.80150000, 106.64320000, 'APPROVED', 4.20, 'Bãi xe tải van và xe tải dưới 2.5 tấn phục vụ thương mại điện tử', 2, '2026-01-26 14:30:00', '2026-01-21 11:00:00', '2026-01-26 14:30:00'),
(14, 4, 'Bãi Tiếp Vận Nông Sản Hóc Môn - QL.22', 'Quốc Lộ 22, Xã Bà Điểm, Huyện Hóc Môn, TP. Hồ Chí Minh', 10.88640000, 106.59510000, 'APPROVED', 4.30, 'Sát chợ đầu mối Hóc Môn, hoạt động nhộn nhịp 24/24', 1, '2026-01-27 10:00:00', '2026-01-22 09:00:00', '2026-01-27 10:00:00'),
(15, 4, 'Bãi Đỗ Xe Trung Tâm Quận 4 - Nguyễn Tất Thành', 'Số 38 Nguyễn Tất Thành, Phường 12, Quận 4, TP. Hồ Chí Minh', 10.76020000, 106.70280000, 'APPROVED', 4.10, 'Bãi đỗ ngắn hạn cho xe chở khách du lịch và xe tải nhỏ vào phố đi bộ', 2, '2026-01-27 15:00:00', '2026-01-22 13:00:00', '2026-01-27 15:00:00'),

-- Merchant 5: Bình Dương (Dĩ An, Thuận An)
(16, 5, 'Bãi Xe Vận Tải Sóng Thần - QL.1K', 'Số 456 Quốc Lộ 1K, Phường Dĩ An, Thành phố Dĩ An, Tỉnh Bình Dương', 10.90234500, 106.78212000, 'APPROVED', 4.70, 'Cửa ngõ logistics kết nối KCN Sóng Thần 1, 2 và cụm ICD', 1, '2026-01-28 09:00:00', '2026-01-23 08:00:00', '2026-01-28 09:00:00'),
(17, 5, 'Bãi Logistics Thuận An - Đại Lộ Bình Dương', 'Số 88 Đại Lộ Bình Dương, Phường Thuận Giao, Thành phố Thuận An, Bình Dương', 10.91560000, 106.72180000, 'APPROVED', 4.50, 'Mặt tiền rộng, có trạm xăng dầu và dịch vụ thay dầu nhớt lưu động', 2, '2026-01-28 11:30:00', '2026-01-23 10:00:00', '2026-01-28 11:30:00'),
(18, 5, 'Bãi Đỗ Xe Cao Tốc Mỹ Phước - Tân Vạn', 'Đường Mỹ Phước - Tân Vạn, Phường Tân Đông Hiệp, TP. Dĩ An, Bình Dương', 10.95520000, 106.73560000, 'APPROVED', 4.60, 'Trục đường chuyên dụng cho xe container đi Cảng Cái Mép - Thị Vải', 1, '2026-01-29 14:00:00', '2026-01-24 09:30:00', '2026-01-29 14:00:00'),

-- Merchant 6: Phú Hữu (Thủ Đức)
(19, 6, 'Bãi Tiếp Vận ICD Phú Hữu - Đường Gò Cát', 'Số 52 Đường Gò Cát, Phường Phú Hữu, Thành phố Thủ Đức, TP. Hồ Chí Minh', 10.78910000, 106.80450000, 'APPROVED', 4.80, 'Khu vực bãi đỗ phụ trợ giảm tải ùn tắc quanh vòng xoay Mỹ Thủy', 2, '2026-01-29 16:30:00', '2026-01-24 14:00:00', '2026-01-29 16:30:00'),
(20, 6, 'Bãi Xe Vành Đai 2 - Phú Hữu', 'Đường Vành Đai 2, Phường Phú Hữu, Thành phố Thủ Đức, TP. Hồ Chí Minh', 10.79540000, 106.81230000, 'APPROVED', 4.40, 'Quy hoạch đường thông thoáng, bảo vệ thân thiện hỗ trợ tài xế tận tình', 1, '2026-01-30 10:00:00', '2026-01-25 08:30:00', '2026-01-30 10:00:00'),

-- Merchant 7: Dịch Vụ Cát Lái
(21, 7, 'Bãi Chờ Xe Đêm Đồng Văn Cống - Cát Lái', 'Số 104 Đồng Văn Cống, Phường Thạnh Mỹ Lợi, Thành phố Thủ Đức, TP. Hồ Chí Minh', 10.77120000, 106.76890000, 'APPROVED', 4.70, 'Bãi đỗ chờ giờ mở line cảng ban đêm, có phòng nghỉ máy lạnh cho tài xế', 2, '2026-01-30 15:00:00', '2026-01-25 11:00:00', '2026-01-30 15:00:00'),
(22, 7, 'Bãi Xe Chuyên Dùng Cảng Tân Cảng', 'Đường A, KCN Cát Lái, Phường Thạnh Mỹ Lợi, Thành phố Thủ Đức, TP. Hồ Chí Minh', 10.76540000, 106.77980000, 'APPROVED', 4.90, 'Trang thiết bị đo kiểm tải trọng và trạm cấp điện cho container lạnh', 1, '2026-01-31 09:30:00', '2026-01-26 09:00:00', '2026-01-31 09:30:00'),

-- Merchant 8: Sóng Thần & Dĩ An
(23, 8, 'Bãi Đỗ Xe Liên Tỉnh Sóng Thần - DT743', 'Đường ĐT743, Phường An Bình, Thành phố Dĩ An, Tỉnh Bình Dương', 10.88760000, 106.75430000, 'APPROVED', 4.50, 'Kết nối đường sắt ga Sóng Thần, thuận tiện bốc dỡ hàng chuyển tàu', 2, '2026-01-31 14:00:00', '2026-01-26 13:00:00', '2026-01-31 14:00:00'),

-- Merchant 9: Long An - Bến Lức
(24, 9, 'Bãi Đỗ Xe Cửa Ngõ Miền Tây - Bến Lức Long An', 'Số 204 Quốc Lộ 1A, Thị trấn Bến Lức, Huyện Bến Lức, Tỉnh Long An', 10.63450000, 106.49120000, 'APPROVED', 4.60, 'Trạm dừng chân và đỗ xe quy mô lớn nhất cửa ngõ Tây Nam Bộ', 1, '2026-02-01 10:00:00', '2026-01-27 09:00:00', '2026-02-01 10:00:00'),

-- BÃI CHỜ DUYỆT (PENDING_APPROVAL - IDs 25, 26, 27)
(25, 10, 'Bãi Xe Tiếp Vận Tân Biên - Biên Hòa', 'Số 316 Xa Lộ Hà Nội, Phường Tân Biên, Thành phố Biên Hòa, Tỉnh Đồng Nai', 10.96540000, 106.88760000, 'PENDING_APPROVAL', 0.00, 'Hồ sơ mới gửi ngày 20/02/2026, đang chờ kiểm tra thực địa hệ thống thoát nước', NULL, NULL, '2026-02-20 09:00:00', '2026-02-20 09:00:00'),
(26, 6, 'Bãi Đỗ Xe Tải Nhẹ An Phú - Thủ Đức', 'Đường Lương Định Của, Phường An Phú, Thành phố Thủ Đức, TP. Hồ Chí Minh', 10.79120000, 106.73890000, 'PENDING_APPROVAL', 0.00, 'Chờ bổ sung giấy phép nghiệm thu PCCC từ công an PCCC TP. Thủ Đức', NULL, NULL, '2026-02-22 14:00:00', '2026-02-22 14:00:00'),
(27, 8, 'Bãi Xe Logistics Tân Bình - Dĩ An', 'Đường Tân Bình, Phường Tân Bình, Thành phố Dĩ An, Tỉnh Bình Dương', 10.93450000, 106.76540000, 'PENDING_APPROVAL', 0.00, 'Mới đăng ký, đang chờ chuyên viên đối soát giấy phép kinh doanh bãi đỗ', NULL, NULL, '2026-02-23 11:30:00', '2026-02-23 11:30:00'),

-- BÃI TẠM ĐÌNH CHỈ (SUSPENDED - IDs 28, 29)
(28, 5, 'Bãi Xe Tải Cũ An Thạnh - Thuận An', 'Đường Cách Mạng Tháng 8, Phường An Thạnh, Thành phố Thuận An, Bình Dương', 10.94210000, 106.69870000, 'SUSPENDED', 3.40, 'Tạm đình chỉ hoạt động do đang thi công thảm lại mặt đường nhựa và lắp cống mới', 1, '2026-02-15 16:00:00', '2026-01-24 10:00:00', '2026-02-15 16:00:00'),
(29, 2, 'Bãi Xe Lê Minh Xuân - Bình Chánh', 'Đường Trần Đại Nghĩa, Xã Lê Minh Xuân, Huyện Bình Chánh, TP. Hồ Chí Minh', 10.75120000, 106.51230000, 'SUSPENDED', 3.60, 'Tạm dừng tiếp nhận phương tiện theo quyết định xử phạt vi phạm niêm yết giá', 2, '2026-02-18 10:30:00', '2026-01-20 11:00:00', '2026-02-18 10:30:00'),

-- BÃI BỊ TỪ CHỐI (REJECTED - ID 30)
(30, 9, 'Bãi Tạm Ven Sông Vàm Cỏ Đông', 'Bờ Kè Sông Vàm Cỏ Đông, Xã An Thạnh, Huyện Bến Lức, Tỉnh Long An', 10.61230000, 106.46780000, 'REJECTED', 0.00, 'Từ chối phê duyệt: Bán kính quay đầu xe không đảm bảo cho xe container và không có rào chắn bảo vệ', 1, '2026-02-10 15:00:00', '2026-02-05 08:00:00', '2026-02-10 15:00:00');

-- 2. SEED SLOTS (Sức chứa bãi đỗ phân theo loại phương tiện)
-- Lưu ý: Một số bãi được cấu hình available_capacity = 0 để kiểm thử trạng thái hết chỗ & Hàng đợi
INSERT IGNORE INTO slots (id, parking_lot_id, vehicle_type, total_capacity, available_capacity) VALUES
-- Lot 1: Cát Lái (Bãi container cao điểm, đang gần hết chỗ)
(1, 1, 'CONTAINER', 60, 2),
(2, 1, 'HEAVY_TRUCK', 25, 4),
-- Lot 2: Linh Trung Thủ Đức
(3, 2, 'HEAVY_TRUCK', 30, 8),
(4, 2, 'LIGHT_TRUCK', 40, 15),
(5, 2, 'CONTAINER', 15, 3),
-- Lot 3: Lê Văn Việt
(6, 3, 'LIGHT_TRUCK', 50, 22),
(7, 3, 'SEDAN_SUV', 20, 9),
-- Lot 4: Bình Chánh QL.50 (Hết sạch slot Container -> Kích hoạt Queue)
(8, 4, 'CONTAINER', 40, 0),
(9, 4, 'HEAVY_TRUCK', 30, 5),
(10, 4, 'LIGHT_TRUCK', 20, 11),
-- Lot 5: Tân Túc QL.1A
(11, 5, 'CONTAINER', 35, 12),
(12, 5, 'HEAVY_TRUCK', 40, 18),
(13, 5, 'LIGHT_TRUCK', 25, 7),
-- Lot 6: Bình Tân Võ Văn Kiệt
(14, 6, 'LIGHT_TRUCK', 35, 14),
(15, 6, 'SEDAN_SUV', 15, 6),
-- Lot 7: Vĩnh Lộc
(16, 7, 'HEAVY_TRUCK', 30, 12),
(17, 7, 'CONTAINER', 20, 8),
-- Lot 8: Cảng Hiệp Phước (Rất rộng)
(18, 8, 'CONTAINER', 80, 28),
(19, 8, 'HEAVY_TRUCK', 40, 16),
-- Lot 9: Nguyễn Văn Tạo Nhà Bè
(20, 9, 'HEAVY_TRUCK', 25, 9),
(21, 9, 'CONTAINER', 30, 11),
-- Lot 10: Nhơn Trạch CT.01
(22, 10, 'CONTAINER', 50, 19),
(23, 10, 'HEAVY_TRUCK', 20, 7),
-- Lot 11: Nguyễn Văn Linh Q7
(24, 11, 'LIGHT_TRUCK', 30, 10),
(25, 11, 'SEDAN_SUV', 20, 8),
(26, 11, 'BUS_COACH', 10, 3),
-- Lot 12: Sân bay Tân Sơn Nhất (Hết sạch slot xe tải nhẹ -> Kích hoạt Queue)
(27, 12, 'LIGHT_TRUCK', 30, 0),
(28, 12, 'SEDAN_SUV', 20, 4),
(29, 12, 'BUS_COACH', 12, 2),
-- Lot 13: Cộng Hòa Tân Bình
(30, 13, 'LIGHT_TRUCK', 25, 8),
(31, 13, 'SEDAN_SUV', 15, 5),
-- Lot 14: Nông sản Hóc Môn
(32, 14, 'LIGHT_TRUCK', 40, 12),
(33, 14, 'HEAVY_TRUCK', 25, 6),
-- Lot 15: Quận 4 Nguyễn Tất Thành
(34, 15, 'SEDAN_SUV', 15, 4),
(35, 15, 'BUS_COACH', 10, 2),
(36, 15, 'LIGHT_TRUCK', 10, 1),
-- Lot 16: Sóng Thần Dĩ An
(37, 16, 'HEAVY_TRUCK', 35, 14),
(38, 16, 'CONTAINER', 45, 17),
(39, 16, 'LIGHT_TRUCK', 20, 9),
-- Lot 17: Thuận An Bình Dương
(40, 17, 'HEAVY_TRUCK', 30, 11),
(41, 17, 'LIGHT_TRUCK', 30, 15),
-- Lot 18: Mỹ Phước - Tân Vạn
(42, 18, 'CONTAINER', 55, 20),
(43, 18, 'HEAVY_TRUCK', 25, 8),
-- Lot 19: Phú Hữu Gò Cát
(44, 19, 'CONTAINER', 50, 15),
(45, 19, 'HEAVY_TRUCK', 20, 6),
-- Lot 20: Vành Đai 2 Phú Hữu
(46, 20, 'CONTAINER', 40, 16),
(47, 20, 'HEAVY_TRUCK', 20, 8),
-- Lot 21: Đồng Văn Cống
(48, 21, 'CONTAINER', 45, 18),
(49, 21, 'HEAVY_TRUCK', 15, 5),
-- Lot 22: Tân Cảng Cát Lái
(50, 22, 'CONTAINER', 70, 24),
-- Lot 23: Ga Sóng Thần
(51, 23, 'HEAVY_TRUCK', 30, 12),
(52, 23, 'LIGHT_TRUCK', 25, 10),
-- Lot 24: Bến Lức Long An
(53, 24, 'CONTAINER', 50, 21),
(54, 24, 'HEAVY_TRUCK', 40, 15),
(55, 24, 'LIGHT_TRUCK', 30, 14),
(56, 24, 'BUS_COACH', 15, 6);

-- 3. SEED PRICING RULES (Biểu phí, hệ số giờ cao điểm, phụ thu quá giờ)
INSERT IGNORE INTO pricing_rules (id, parking_lot_id, vehicle_type, base_price_per_hour, surge_multiplier, surge_start_time, surge_end_time, overtime_penalty_multiplier) VALUES
-- Lot 1
(1, 1, 'CONTAINER', 55000.00, 1.40, '06:00:00', '09:00:00', 1.80),
(2, 1, 'HEAVY_TRUCK', 40000.00, 1.30, '06:00:00', '09:00:00', 1.70),
-- Lot 2
(3, 2, 'HEAVY_TRUCK', 38000.00, 1.25, '17:00:00', '20:00:00', 1.60),
(4, 2, 'LIGHT_TRUCK', 20000.00, 1.20, '17:00:00', '20:00:00', 1.50),
(5, 2, 'CONTAINER', 50000.00, 1.35, '17:00:00', '20:00:00', 1.75),
-- Lot 3
(6, 3, 'LIGHT_TRUCK', 18000.00, 1.15, '07:00:00', '09:00:00', 1.50),
(7, 3, 'SEDAN_SUV', 20000.00, 1.15, '07:00:00', '09:00:00', 1.50),
-- Lot 4
(8, 4, 'CONTAINER', 48000.00, 1.30, '06:30:00', '09:30:00', 1.70),
(9, 4, 'HEAVY_TRUCK', 35000.00, 1.25, '06:30:00', '09:30:00', 1.60),
(10, 4, 'LIGHT_TRUCK', 18000.00, 1.15, '06:30:00', '09:30:00', 1.50),
-- Lot 5
(11, 5, 'CONTAINER', 50000.00, 1.30, '16:00:00', '19:00:00', 1.70),
(12, 5, 'HEAVY_TRUCK', 36000.00, 1.20, '16:00:00', '19:00:00', 1.60),
(13, 5, 'LIGHT_TRUCK', 18000.00, 1.15, '16:00:00', '19:00:00', 1.50),
-- Lot 6
(14, 6, 'LIGHT_TRUCK', 20000.00, 1.25, '07:00:00', '09:00:00', 1.50),
(15, 6, 'SEDAN_SUV', 22000.00, 1.20, '07:00:00', '09:00:00', 1.50),
-- Lot 7
(16, 7, 'HEAVY_TRUCK', 32000.00, 1.20, '18:00:00', '21:00:00', 1.50),
(17, 7, 'CONTAINER', 45000.00, 1.30, '18:00:00', '21:00:00', 1.65),
-- Lot 8
(18, 8, 'CONTAINER', 52000.00, 1.35, '06:00:00', '09:00:00', 1.75),
(19, 8, 'HEAVY_TRUCK', 38000.00, 1.25, '06:00:00', '09:00:00', 1.60),
-- Lot 9
(20, 9, 'HEAVY_TRUCK', 35000.00, 1.20, '07:00:00', '10:00:00', 1.50),
(21, 9, 'CONTAINER', 48000.00, 1.30, '07:00:00', '10:00:00', 1.65),
-- Lot 10
(22, 10, 'CONTAINER', 55000.00, 1.40, '17:00:00', '20:00:00', 1.80),
(23, 10, 'HEAVY_TRUCK', 40000.00, 1.30, '17:00:00', '20:00:00', 1.70),
-- Lot 11
(24, 11, 'LIGHT_TRUCK', 22000.00, 1.25, '07:30:00', '09:30:00', 1.50),
(25, 11, 'SEDAN_SUV', 25000.00, 1.20, '07:30:00', '09:30:00', 1.50),
(26, 11, 'BUS_COACH', 35000.00, 1.20, '07:30:00', '09:30:00', 1.60),
-- Lot 12
(27, 12, 'LIGHT_TRUCK', 28000.00, 1.35, '06:00:00', '10:00:00', 1.80),
(28, 12, 'SEDAN_SUV', 30000.00, 1.30, '06:00:00', '10:00:00', 1.80),
(29, 12, 'BUS_COACH', 45000.00, 1.25, '06:00:00', '10:00:00', 1.75),
-- Lot 13
(30, 13, 'LIGHT_TRUCK', 24000.00, 1.25, '08:00:00', '10:00:00', 1.50),
(31, 13, 'SEDAN_SUV', 25000.00, 1.20, '08:00:00', '10:00:00', 1.50),
-- Lot 14
(32, 14, 'LIGHT_TRUCK', 18000.00, 1.20, '01:00:00', '05:00:00', 1.50),
(33, 14, 'HEAVY_TRUCK', 32000.00, 1.25, '01:00:00', '05:00:00', 1.60),
-- Lot 15
(34, 15, 'SEDAN_SUV', 35000.00, 1.40, '18:00:00', '22:00:00', 2.00),
(35, 15, 'BUS_COACH', 50000.00, 1.35, '18:00:00', '22:00:00', 2.00),
(36, 15, 'LIGHT_TRUCK', 30000.00, 1.30, '18:00:00', '22:00:00', 1.80),
-- Lot 16
(37, 16, 'HEAVY_TRUCK', 35000.00, 1.20, '16:00:00', '19:00:00', 1.60),
(38, 16, 'CONTAINER', 48000.00, 1.30, '16:00:00', '19:00:00', 1.70),
(39, 16, 'LIGHT_TRUCK', 18000.00, 1.15, '16:00:00', '19:00:00', 1.50),
-- Lot 17
(40, 17, 'HEAVY_TRUCK', 34000.00, 1.20, '07:00:00', '09:00:00', 1.50),
(41, 17, 'LIGHT_TRUCK', 18000.00, 1.15, '07:00:00', '09:00:00', 1.50),
-- Lot 18
(42, 18, 'CONTAINER', 46000.00, 1.25, '16:30:00', '19:30:00', 1.65),
(43, 18, 'HEAVY_TRUCK', 33000.00, 1.20, '16:30:00', '19:30:00', 1.50),
-- Lot 19
(44, 19, 'CONTAINER', 50000.00, 1.35, '06:00:00', '09:00:00', 1.70),
(45, 19, 'HEAVY_TRUCK', 36000.00, 1.25, '06:00:00', '09:00:00', 1.60),
-- Lot 20
(46, 20, 'CONTAINER', 48000.00, 1.30, '07:00:00', '09:00:00', 1.65),
(47, 20, 'HEAVY_TRUCK', 35000.00, 1.20, '07:00:00', '09:00:00', 1.50),
-- Lot 21
(48, 21, 'CONTAINER', 52000.00, 1.35, '22:00:00', '04:00:00', 1.70),
(49, 21, 'HEAVY_TRUCK', 38000.00, 1.25, '22:00:00', '04:00:00', 1.60),
-- Lot 22
(50, 22, 'CONTAINER', 58000.00, 1.45, '06:00:00', '10:00:00', 1.85),
-- Lot 23
(51, 23, 'HEAVY_TRUCK', 34000.00, 1.20, '17:00:00', '20:00:00', 1.50),
(52, 23, 'LIGHT_TRUCK', 19000.00, 1.15, '17:00:00', '20:00:00', 1.50),
-- Lot 24
(53, 24, 'CONTAINER', 45000.00, 1.25, '06:00:00', '09:00:00', 1.60),
(54, 24, 'HEAVY_TRUCK', 32000.00, 1.20, '06:00:00', '09:00:00', 1.50),
(55, 24, 'LIGHT_TRUCK', 17000.00, 1.15, '06:00:00', '09:00:00', 1.50),
(56, 24, 'BUS_COACH', 30000.00, 1.20, '06:00:00', '09:00:00', 1.50);

-- 4. SEED PARKING LOT IMAGES (Cover photos for lots)
INSERT IGNORE INTO parking_lot_images (id, parking_lot_id, stored_file_name, original_file_name, content_type, size_bytes, is_cover, created_at, updated_at) VALUES
(1, 1, 'img_lot1_cover.webp', 'bai_cat_lai_cong_g_overview.jpg', 'image/webp', 452100, TRUE, '2026-01-20 09:00:00', '2026-01-20 09:00:00'),
(2, 2, 'img_lot2_cover.webp', 'bai_linh_trung_xa_lo_ha_noi.jpg', 'image/webp', 389400, TRUE, '2026-01-20 10:00:00', '2026-01-20 10:00:00'),
(3, 4, 'img_lot4_cover.webp', 'bai_binh_chanh_ql50.jpg', 'image/webp', 512300, TRUE, '2026-01-22 09:30:00', '2026-01-22 09:30:00'),
(4, 5, 'img_lot5_cover.webp', 'bai_tan_tuc_ql1a.jpg', 'image/webp', 467800, TRUE, '2026-01-22 14:00:00', '2026-01-22 14:00:00'),
(5, 8, 'img_lot8_cover.webp', 'cang_quoc_te_hiep_phuoc.jpg', 'image/webp', 623100, TRUE, '2026-01-24 09:00:00', '2026-01-24 09:00:00'),
(6, 12, 'img_lot12_cover.webp', 'staging_tan_son_nhat.jpg', 'image/webp', 415600, TRUE, '2026-01-26 09:30:00', '2026-01-26 09:30:00'),
(7, 16, 'img_lot16_cover.webp', 'song_than_di_an_ql1k.jpg', 'image/webp', 534200, TRUE, '2026-01-28 09:00:00', '2026-01-28 09:00:00'),
(8, 24, 'img_lot24_cover.webp', 'cua_ngo_ben_luc_long_an.jpg', 'image/webp', 489900, TRUE, '2026-02-01 10:00:00', '2026-02-01 10:00:00');
