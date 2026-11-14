# BÁO CÁO SPRINT 3 REVIEW — SMART PARKING HUB (NHÓM 4)

> **Dự án:** SmartParkingHub — Hệ thống Quản lý và Điều hành Bãi đỗ xe Thông minh  
> **Nền tảng quản lý:** Trello ([Smart Parking Hub - Delivery](https://trello.com/b/QrKLrlz1/smart-parking-hub-delivery))  
> **Cột nghiệm thu:** `Sprint 03 - Done`  
> **Thời điểm xuất báo cáo:** 14/11/2026  

---

## 🎯 1. Sprint Goal của Sprint 3
> **Sprint Goal 03:** Hoàn thiện Quản lý Sàn (Admin/Merchant), Tích hợp Thanh toán PayOS & Đối soát Rút tiền, Tối ưu Hàng đợi ảo & Khiếu nại Tài xế, và Kiểm thử toàn diện E2E tự động (Playwright & CI/CD).

---

## 📊 2. Tổng Hợp Tiến Độ Toàn Đội

- **Số Story Points (SP) đạt được / mục tiêu:** **112 / 181 SP** *(đạt 61.9% theo Backlog phân công thực tế trên Board)* hoặc **112 / 154 SP** *(đạt 72.7% theo Kế hoạch cam kết ban đầu)*
- **Số Task đạt được / mục tiêu:** **31 / 49 Tasks** *(đạt 63.3%)* hoặc **31 / 44 Tasks** *(đạt 70.5%)*

---

## 👥 3. Thống Kê Chi Tiết Từng Thành Viên

### 1. Lâm Gia Huy (Huyy Gia) — Fullstack & AI / QA Lead
- **Số SP đạt được / mục tiêu:** **32 / 40 SP** *(hoặc 32 / 34 SP theo Plan)*
- **Số task đạt được / mục tiêu:** **8 / 10 Task** *(hoặc 8 / 8 Task theo Plan)*
- **Các hạng mục chính đã hoàn thành:**
  1. `[SP-S3-UI-01]` Bảng Điều khiển Tổng quan Vận hành Chủ bãi (Merchant Dashboard) — (5 SP)
  2. `[SP-S3-UI-02]` Trung tâm Điều hành Toàn sàn Super Admin (Admin Dashboard) — (5 SP)
  3. `[SP-S3-INT-01]` Bộ kịch bản kiểm thử E2E tự động Playwright (Giữ chỗ 10p & Barie QR) — (5 SP)
  4. `[SP-S3-AI-01]` Widget Trợ lý ảo AI Copilot tra cứu bãi đỗ — (5 SP)
  5. `[SP-S3-GOV-04]` Danh bạ Quản lý Tài khoản & Phân quyền Toàn sàn — (3 SP)
  6. `[SP-S3-GOV-02]` Hoàn thiện API Cảnh báo & Lịch sử Kỷ luật Tài khoản Admin — (3 SP)
  7. `[SP-S3-BKG-01]` API truy vấn chi tiết đơn đặt chỗ cho Chủ bãi theo ID — (3 SP)
  8. `[SP-S3-INT-03]` Loại bỏ Mock Data và tích hợp API chuỗi doanh thu ngày Merchant — (3 SP)

---

### 2. Hoàng Đình Tùng (Tung Hoang Dinh) — DevOps, Security & Architecture Lead
- **Số SP đạt được / mục tiêu:** **29 / 35 SP** *(hoặc 29 / 19 SP theo Plan ban đầu - Vượt cam kết)*
- **Số task đạt được / mục tiêu:** **9 / 11 Task** *(hoặc 9 / 7 Task theo Plan)*
- **Các hạng mục chính đã hoàn thành:**
  1. `[SP-S3-UI-05]` Xây dựng bộ UI Component dùng chung (Skeleton, Toast, Modal) theo Stitch DS — (5 SP)
  2. `[SP-S3-INT-02]` Sửa lỗi lệch Contract DTO & cơ chế Fallback Mock khi Đặt cọc giữ chỗ — (5 SP)
  3. `[SP-S3-OPS-01]` Thiết lập Pipeline GitHub Actions kiểm tra chất lượng Frontend v2 — (3 SP)
  4. `[SP-S3-OPS-03]` GitHub Actions Pipeline kiểm soát chất lượng Zero-Error trước merge PR — (3 SP)
  5. `[SP-S3-OPS-04]` Cấu hình Docker Compose đa môi trường & Nginx Reverse Proxy tối ưu cache — (3 SP)
  6. `[SP-S3-DAT-01]` Spatial Index (ST_Distance_Sphere) & Bộ đệm Redis Cache-Aside tìm bãi — (3 SP)
  7. `[SP-S3-SEC-03]` Nhật ký Kiểm toán An ninh & Vết Quản trị (Admin Audit Logs) — (3 SP)
  8. `[SP-S3-SEC-02]` Rà soát & loại bỏ việc gửi mã OTP trong response demo — (2 SP)
  9. `[SP-S3-SEC-04]` Chuẩn hóa apiClient gắn Authorization Token cho Notification API — (2 SP)

---

### 3. Huỳnh La Tiến Lộc (Huynh La Tien Loc) — Payment & Settlement Lead
- **Số SP đạt được / mục tiêu:** **28 / 62 SP** *(hoặc 28 / 32 SP theo Plan)*
- **Số task đạt được / mục tiêu:** **8 / 15 Task** *(hoặc 8 / 7 Task theo Plan)*
- **Các hạng mục chính đã hoàn thành:**
  1. `[SP-S3-PAY-06]` Phê duyệt & Quyết toán Lệnh Rút tiền Ví Chủ bãi — (5 SP)
  2. `[SP-S3-PAY-04]` Tạo Yêu cầu Rút tiền & Quản lý Giải ngân Merchant — (5 SP)
  3. `[SP-S3-PAY-02]` Kiểm thử toàn diện quy trình Đối soát Tài chính kép & Duyệt rút tiền — (5 SP)
  4. `[SP-S3-PAY-09]` Luồng tiếp nhận & Giao diện Kết quả Thanh toán PayOS Thành công — (5 SP)
  5. `[SP-S3-PAY-08]` Hồ sơ Chủ bãi & Thông tin Ngân hàng Rút tiền VietQR — (2 SP)
  6. `[SP-S3-UI-16]` Trung tâm Phát sóng & Cảnh báo Điều hành Toàn sàn — (2 SP)
  7. `[SP-S3-UI-21]` Bảng giá Dịch vụ & Gói Bãi xe — (2 SP)
  8. `[SP-S3-OPS-02]` Dọn dẹp nợ kỹ thuật: Xóa Ant Design Sider & trang mồ côi — (2 SP)

---

### 4. Hồ Quốc Khải (Ho Quoc Khai) — Governance, Booking & Dispute Lead
- **Số SP đạt được / mục tiêu:** **23 / 44 SP** *(hoặc 23 / 32 SP theo Plan)*
- **Số task đạt được / mục tiêu:** **6 / 13 Task** *(hoặc 6 / 9 Task theo Plan)*
- **Các hạng mục chính đã hoàn thành:**
  1. `[SP-S3-UI-23]` Nâng cấp Giao diện Tìm bãi Toàn màn hình, Real-time & Fix Pan Route — (5 SP)
  2. `[SP-S3-GOV-01]` Bộ API & Luồng Quản lý Khiếu nại cho Tài xế (Driver Dispute API) — (5 SP)
  3. `[SP-S3-UI-03]` Giao diện khởi tạo và theo dõi khiếu nại cho Tài xế — (5 SP)
  4. `[SP-S3-UI-13]` Quản lý Lịch sử & Đơn Đặt chỗ Khách hàng — (3 SP)
  5. `[SP-S3-UI-14]` Chi tiết Đơn Đặt chỗ & Nhật ký Barie — (3 SP)
  6. `[SP-S3-UI-06]` Trung tâm Thông báo Vận hành Tài xế — (2 SP)

---

## 📋 4. Bảng Kê 31 Thẻ Hoàn Thành Trong Cột `Sprint 03 - Done`

| STT | Mã Thẻ | Tên Thẻ Nghiệp Vụ | Thành viên | Story Points |
| :---: | :--- | :--- | :---: | :---: |
| 1 | `SP-S3-GOV-04` | Xây dựng Danh bạ Quản lý Tài khoản & Phân quyền Toàn sàn | Lâm Gia Huy | 3 STP |
| 2 | `SP-S3-UI-01` | Xây dựng Bảng Điều khiển Tổng quan Vận hành Chủ bãi | Lâm Gia Huy | 5 STP |
| 3 | `SP-S3-UI-02` | Xây dựng Trung tâm Điều hành Toàn sàn Super Admin | Lâm Gia Huy | 5 STP |
| 4 | `SP-S3-INT-01` | Xây dựng bộ kịch bản kiểm thử E2E tự động (Playwright) luồng Giữ chỗ & Barie | Lâm Gia Huy | 5 STP |
| 5 | `SP-S3-INT-03` | Loại bỏ Mock Data biểu đồ & tích hợp API chuỗi doanh thu ngày Merchant | Lâm Gia Huy | 3 STP |
| 6 | `SP-S3-GOV-02` | Triển khai Hoàn thiện API Cảnh báo & Lịch sử Kỷ luật Tài khoản Admin | Lâm Gia Huy | 3 STP |
| 7 | `SP-S3-AI-01` | Tích hợp widget trợ lý ảo AI Copilot tra cứu bãi đỗ | Lâm Gia Huy | 5 STP |
| 8 | `SP-S3-BKG-01` | Triển khai Bổ sung API truy vấn chi tiết đơn đặt chỗ cho chủ bãi theo ID | Lâm Gia Huy | 3 STP |
| 9 | `SP-S3-SEC-04` | Thay thế axios trần bằng apiClient để gắn Token cho Notification API | Hoàng Đình Tùng | 2 STP |
| 10 | `SP-S3-DAT-01` | Spatial Index (ST_Distance_Sphere) & bộ đệm Redis Cache-Aside tìm bãi | Hoàng Đình Tùng | 3 STP |
| 11 | `SP-S3-OPS-01` | Thiết lập pipeline GitHub Actions tự động kiểm tra chất lượng Frontend v2 | Hoàng Đình Tùng | 3 STP |
| 12 | `SP-S3-SEC-02` | Rà soát và loại bỏ việc gửi mã OTP trong response demo | Hoàng Đình Tùng | 2 STP |
| 13 | `SP-S3-SEC-03` | Xây dựng Nhật ký Kiểm toán An ninh & Vết Quản trị | Hoàng Đình Tùng | 3 STP |
| 14 | `SP-S3-UI-05` | Xây dựng bộ UI Component dùng chung (Skeleton, Toast, Modal) chuẩn Stitch | Hoàng Đình Tùng | 5 STP |
| 15 | `SP-S3-INT-02` | Sửa lỗi lệch Contract DTO & cơ chế Fallback Mock khi Đặt cọc giữ chỗ | Hoàng Đình Tùng | 5 STP |
| 16 | `SP-S3-OPS-03` | GitHub Actions Pipeline kiểm soát chất lượng tự động Zero-Error trước PR | Hoàng Đình Tùng | 3 STP |
| 17 | `SP-S3-OPS-04` | Cấu hình Docker Compose đa môi trường & Nginx Reverse Proxy tối ưu cache | Hoàng Đình Tùng | 3 STP |
| 18 | `SP-S3-UI-23` | Nâng cấp Giao diện Tìm bãi Toàn màn hình, Real-time & Fix Lỗi Pan Route | Hồ Quốc Khải | 5 STP |
| 19 | `SP-S3-UI-14` | Xây dựng Chi tiết Đơn Đặt chỗ & Nhật ký Barie | Hồ Quốc Khải | 3 STP |
| 20 | `SP-S3-UI-13` | Xây dựng Quản lý Lịch sử & Đơn Đặt chỗ Khách hàng | Hồ Quốc Khải | 3 STP |
| 21 | `SP-S3-UI-03` | Xây dựng Giao diện khởi tạo và theo dõi khiếu nại cho Tài xế | Hồ Quốc Khải | 5 STP |
| 22 | `SP-S3-UI-06` | Xây dựng Trung tâm Thông báo Vận hành Tài xế | Hồ Quốc Khải | 2 STP |
| 23 | `SP-S3-GOV-01` | Triển khai bộ API & Luồng Quản lý Khiếu nại cho Tài xế (Driver Dispute API) | Hồ Quốc Khải | 5 STP |
| 24 | `SP-S3-OPS-02` | Dọn dẹp nợ kỹ thuật: Xóa bỏ Ant Design Sider chết & trang mồ côi | Huỳnh La Tiến Lộc | 2 STP |
| 25 | `SP-S3-UI-16` | Xây dựng Trung tâm Phát sóng & Cảnh báo Điều hành | Huỳnh La Tiến Lộc | 2 STP |
| 26 | `SP-S3-PAY-06` | Xây dựng Phê duyệt & Quyết toán Lệnh Rút tiền Ví Chủ bãi | Huỳnh La Tiến Lộc | 5 STP |
| 27 | `SP-S3-UI-21` | Xây dựng Bảng giá Dịch vụ & Gói Bãi xe | Huỳnh La Tiến Lộc | 2 STP |
| 28 | `SP-S3-PAY-02` | Kiểm thử toàn diện quy trình đối soát tài chính kép & duyệt rút tiền | Huỳnh La Tiến Lộc | 5 STP |
| 29 | `SP-S3-PAY-09` | Xây dựng Kết quả Thanh toán PayOS Thành công | Huỳnh La Tiến Lộc | 5 STP |
| 30 | `SP-S3-PAY-08` | Xây dựng Hồ sơ Chủ bãi & Thông tin Ngân hàng Rút tiền | Huỳnh La Tiến Lộc | 2 STP |
| 31 | `SP-S3-PAY-04` | Tạo Yêu cầu Rút tiền & Quản lý Giải ngân | Huỳnh La Tiến Lộc | 5 STP |
