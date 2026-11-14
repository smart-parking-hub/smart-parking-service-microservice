# KẾ HOẠCH BẮT LỖI HOÀN THIỆN SPRINT 3 & PHÂN BỔ SPRINT 4 CHI TIẾT

> **Dự án:** SmartParkingHub — Hệ thống Quản lý và Điều hành Bãi đỗ xe Thông minh  
> **Nền tảng Trello:** [Smart Parking Hub - Delivery](https://trello.com/b/QrKLrlz1/smart-parking-hub-delivery)  
> **Thời điểm thực hiện:** 14/11/2026  
> **Trạng thái:** Đã nghiệm thu bổ sung Sprint 3 và dời toàn bộ thẻ tồn đọng sang Sprint 4.

---

## 🎯 1. Tổng Kết Nghiệm Thu Chốt Sprint 3

- **Tổng số Task nghiệm thu đạt chuẩn:** **32 / 32 Tasks** hoàn thành trong cột `Sprint 03 - Done`.
- **Tổng Story Points (STP) hoàn tất:** **115 STP** (Tăng thêm 3 STP sau khi chốt hoàn thành hạng mục `SP-S3-SEC-01` - Tái cấu trúc OTP SMTP).
- **Hạ tầng VPS Cluster:** Đã kiểm thử live 100% không còn lỗi trên 3 node:
  - VPS 1: Nginx (80) & API Gateway (8088), Identity Service (8081).
  - VPS 2: MySQL (3306), Kafka (9092), Notification (8085), Payment (8084), AI (8086).
  - VPS 3: Parking Lot Service (8082), Booking Service (8087), Redis Core (6379).

---

## 🔍 2. Kế Hoạch Bắt Lỗi, Kiểm Thử Toàn Diện (Bug Triage & QA Plan) Tuần Tới

Để đảm bảo tuần sau kết thúc sprint có thể xuất báo cáo hoàn thiện không tì vết, đội ngũ sẽ triển khai quy trình 4 giai đoạn:

### Giai đoạn 1: Rà soát & Bắt lỗi Luồng Nghiệp vụ Trọng yếu (Thứ 2 - Thứ 3)
1. **Luồng Giữ chỗ & Khóa Slot 10 Phút (Booking Hold TTL):**
   - Kiểm tra Redis key expiration khi tài xế đặt chỗ nhưng không thanh toán cọc.
   - Kiểm tra giải phóng slot tự động trên MySQL và phát sự kiện `booking.cancelled` qua Kafka.
2. **Luồng Cổng Barie & Thanh toán Tiền mặt (Barie Check-In/Check-Out):**
   - Xác thực hợp đồng DTO giữa Mobile App quét QR và endpoint `confirm-cash` của Booking Service.
   - Chặn đứng lỗi ngoại lệ `IllegalArgumentException` khi client gửi request không đúng chuẩn.
3. **Luồng Hoàn tiền & Tranh chấp Tự động (Dispute & Settlement):**
   - Kiểm tra API tạo khiếu nại tài xế (`/api/v1/drivers/disputes`) kết nối tới Payment Service xử lý bồi thường cọc.

### Giai đoạn 2: Tự động hóa Kiểm thử E2E & Contract DTO (Thứ 4)
1. **Chạy E2E Playwright Suite:**
   - Chạy toàn bộ test suites kiểm thử giao diện `smart-parking-app-v2` mô phỏng hành vi: Đăng ký OTP -> Đăng nhập -> Tìm kiếm bãi trên bản đồ -> Đặt cọc PayOS -> Nhận QR Barie.
2. **Kiểm tra Zero-Mock:**
   - Quét mã nguồn Frontend để đảm bảo cờ `VITE_USE_MOCK_DATA=false`, chuyển 100% sang API thật từ Gateway.

### Giai đoạn 3: Tải & Khả năng Chịu lỗi (Stress & Resilience Testing) (Thứ 5)
1. **Kiểm thử CircuitBreaker & Timeout:**
   - Mô phỏng ngắt kết nối tạm thời giữa VPS 1 và VPS 2 để kiểm chứng phản ứng của CircuitBreaker `otp-mail`.
2. **Tải đồng thời tìm bãi (Spatial Search):**
   - Dùng k6 hoặc Apache Bench bắn đồng thời 50 req/s vào endpoint `/api/v1/parking-lots/search` để kiểm tra Spatial Indexing và Redis Cache-Aside.

### Giai đoạn 4: Đóng gói & Báo cáo Tổng kết Sprint (Thứ 6)
1. Kiểm tra log rotation và tài nguyên trên cụm 3 VPS (`free -h`, `df -h`).
2. Xuất báo cáo nghiệm thu hoàn chỉnh, cập nhật toàn bộ changelog và video demo sản phẩm.

---

## 📋 3. Bảng Phân Bổ 25 Thẻ Chuyển Sang `Sprint 04 - Backlog` Cho Từng Thành Viên

Toàn bộ **25 thẻ chưa hoàn thành của Sprint 3** đã được chuyển sang danh sách **`Sprint 04 - Backlog`** trên Trello và gán người chịu trách nhiệm chính xác 100% theo đúng chuyên môn:

### 1. Lâm Gia Huy (Huyy Gia) — Fullstack, AI & E2E QA Lead (6 Tasks - 19 STP)
| STT | Mã Thẻ | Tên Nghiệp Vụ | STP | Danh mục |
| :---: | :--- | :--- | :---: | :--- |
| 1 | `[SP-S3-PRC-01]` | Xây dựng Thiết lập Biểu phí Ma trận 4 Thông số (MerchantPricingPage) | 5 STP | Pricing Rules |
| 2 | `[SP-S3-PAY-01]` | Đồng bộ hóa hợp đồng API thu tiền mặt Barie confirm-cash | 3 STP | Booking/Payment Contract |
| 3 | `[SP-S3-INT-04]` | Tích hợp thư viện quét mã Barie camera html5-qrcode trên Mobile HTTPS | 3 STP | Hardware/Webcam QR |
| 4 | `[SP-S3-UI-04]` | Xây dựng Trang chủ Marketing & Live Hero Map (LandingPage) | 3 STP | Public Portal |
| 5 | `[SP-S3-UI-09]` | Sửa lỗi parse Response API danh sách đặt chỗ cá nhân MyBookings | 2 STP | Contract Fix |
| 6 | `[SP-S3-UI-15]` | Chuẩn hóa cơ chế xử lý lỗi API và cờ cấu hình Mock Data môi trường | 3 STP | Architecture FE |

---

### 2. Hoàng Đình Tùng (Tung Hoang Dinh) — DevOps, Security & Architecture Lead (6 Tasks - 15 STP)
| STT | Mã Thẻ | Tên Nghiệp Vụ | STP | Danh mục |
| :---: | :--- | :--- | :---: | :--- |
| 1 | `[SP-S3-GOV-05]` | Xây dựng Bàn Trọng tài & Danh sách Tranh chấp Mở (AdminDisputesPage) | 3 STP | Security & Gov |
| 2 | `[SP-S3-UI-17]` | Xây dựng Tài liệu Tích hợp API & Webhooks cho đối tác bãi đỗ | 2 STP | DevPortal Docs |
| 3 | `[SP-S3-UI-19]` | Xây dựng Liên hệ Hợp tác & Hỗ trợ Kỹ thuật 24/7 | 3 STP | Public Portal |
| 4 | `[SP-S3-UI-20]` | Xây dựng Thông báo Lỗi Truy cập 403 Forbidden | 1 STP | Gateway Error UX |
| 5 | `[SP-S3-DAT-02]` | Đồng bộ hóa danh mục loại phương tiện TRAILER giữa FE và Backend | 3 STP | Data Schema |
| 6 | `[SP-S3-RT-01]` | Triển khai API Bật/Tắt Trạng thái Mở/Tạm dừng Bãi Đỗ Xe Thời Gian Thực | 3 STP | Real-time Status |

---

### 3. Huỳnh La Tiến Lộc (Huynh La Tien Loc) — Payment & Settlement Lead (6 Tasks - 25 STP)
| STT | Mã Thẻ | Tên Nghiệp Vụ | STP | Danh mục |
| :---: | :--- | :--- | :---: | :--- |
| 1 | `[SP-S3-PAY-07]` | Xây dựng Phán quyết Trọng tài & Phân bổ Tiền bồi thường | 5 STP | Settlement & Escrow |
| 2 | `[SP-S3-SAGA-01]` | Tự động hóa Đối soát và Xử lý Hoàn tiền Tranh chấp (Auto-Refund Settlement) | 5 STP | SAGA Pattern |
| 3 | `[SP-S3-PAY-10]` | Xây dựng Sổ cái Lịch sử Thanh toán & Đối soát Cọc | 2 STP | Double-entry Ledger |
| 4 | `[SP-S3-L01]` | Sổ Cái Kép & Quyết Toán Doanh Thu Chủ Bãi | 8 STP | Core Ledger Settlement |
| 5 | `[SP-S3-UI-08]` | Xây dựng Thông báo Thanh toán Bị hủy & Hướng dẫn Thử lại | 2 STP | Payment UX |
| 6 | `[SP-S3-UI-11]` | Xây dựng Chi tiết Vận hành & Doanh thu Bãi đỗ (MerchantParkingLotDetailPage) | 3 STP | Financial Analytics |

---

### 4. Hồ Quốc Khải (Ho Quoc Khai) — Governance, Booking & Dispute Lead (7 Tasks - 20 STP)
| STT | Mã Thẻ | Tên Nghiệp Vụ | STP | Danh mục |
| :---: | :--- | :--- | :---: | :--- |
| 1 | `[SP-S3-BKG-02]` | Khắc phục đứt gãy luồng Hàng đợi ảo: Bổ sung trạng thái NOTIFIED và API Accept Slot | 5 STP | Virtual Queue Engine |
| 2 | `[SP-S3-GOV-06]` | Xây dựng Danh sách Khiếu nại từ Tài xế (MerchantDisputesPage) | 2 STP | Merchant Dispute |
| 3 | `[SP-S3-GOV-07]` | Xây dựng Chi tiết Khiếu nại, Lời khai & Bằng chứng (MerchantDisputeDetailPage) | 3 STP | Dispute Evidence |
| 4 | `[SP-S3-UI-10]` | Xây dựng Danh sách & Lưới Trạng thái Bãi đỗ xe (MerchantParkingLotsPage) | 3 STP | Merchant Operations |
| 5 | `[SP-S3-UI-12]` | Xây dựng Chỉnh sửa Thông tin & Tiện ích Bãi đỗ (EditMerchantParkingLotPage) | 3 STP | Merchant Operations |
| 6 | `[SP-S3-UI-07]` | Xây dựng Trung tâm Thông báo Vận hành Bãi xe (MerchantNotificationsPage) | 2 STP | Operations Center |
| 7 | `[SP-S3-UI-18]` | Xây dựng Cơ hội Nghề nghiệp & Tuyển dụng (CareersPage) | 2 STP | Public Portal |

---

## 📊 4. Tổng Kết Chỉ Số Phân Bổ Sprint 4

| Thành viên | Lĩnh vực phụ trách | Số Task Sprint 4 | Story Points | Tỷ trọng |
| :--- | :--- | :---: | :---: | :---: |
| **Lâm Gia Huy** | Fullstack, AI, Biểu phí & E2E QA | 6 Tasks | **19 STP** | 24.1% |
| **Hoàng Đình Tùng** | DevOps, Cloud, Realtime & Bảo mật | 6 Tasks | **15 STP** | 19.0% |
| **Huỳnh La Tiến Lộc** | Thanh toán PayOS, Sổ cái kép & Bồi thường | 6 Tasks | **25 STP** | 31.6% |
| **Hồ Quốc Khải** | Hàng đợi ảo, Tranh chấp & Vận hành bãi | 7 Tasks | **20 STP** | 25.3% |
| **TỔNG CỘNG** | **Toàn đội Smart Parking Hub** | **25 Tasks** | **79 STP** | **100%** |
