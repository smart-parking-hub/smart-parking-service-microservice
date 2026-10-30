# 🚀 SmartParkingHub - Postman API Collection & Environment

Tài liệu hướng dẫn sử dụng bộ sưu tập Postman Collection kiểm thử toàn diện hệ thống **SmartParkingHub Microservices**.

---

## 📂 Danh mục tệp tin
* 📄 [`SmartParkingHub_API_Collection.postman_collection.json`](./SmartParkingHub_API_Collection.postman_collection.json): Định dạng Postman Collection v2.1.0 gồm **21 thư mục** và **104 requests** phủ kín toàn bộ 18 Controller (kèm AI Copilot và System Observability).
* 📄 [`SmartParkingHub_Local_Env.postman_environment.json`](./SmartParkingHub_Local_Env.postman_environment.json): File môi trường cấu hình sẵn `baseUrl: http://localhost:8088`, `ai_direct_url: http://localhost:8086` cùng các biến động tự động trích xuất.
* 📑 [`TEST_SCENARIOS.md`](./TEST_SCENARIOS.md): Tài liệu đặc tả kịch bản kiểm thử toàn diện End-to-End, Hàng đợi Waitlist, Negative tests bảo mật và hướng dẫn chạy Runner tự động.

---

## 📥 Hướng dẫn Import vào Postman
1. Mở ứng dụng **Postman**.
2. Nhấn nút **Import** ở góc trên cùng bên trái.
3. Kéo thả 2 file `SmartParkingHub_API_Collection.postman_collection.json` và `SmartParkingHub_Local_Env.postman_environment.json` vào Postman.
4. Ở góc trên bên phải của Postman, chọn Environment là: **`SmartParkingHub - Local Environment`**.

---

## 🔑 Cơ chế xác thực tự động (Auto-Token Scripts)
Mỗi role đều có tài khoản Demo sẵn được cấu hình với mật khẩu chung `password123`:

| Role | Số điện thoại (Tài khoản) | Email | Password | Biến lưu Token |
| :--- | :--- | :--- | :--- | :--- |
| **System Admin** | `0903829145` | `admin@smartparking.com` | `password123` | `{{admin_token}}` |
| **Merchant (Chủ bãi)** | `0908123456` | `thanh.dongsaigon@gmail.com` | `password123` | `{{merchant_token}}` |
| **Driver (Tài xế)** | `0909128374` | `hung.nguyen@smartparking.com` | `password123` | `{{driver_token}}` |

### Cách sử dụng Token:
1. Mở thư mục **`00. Authentication & Quick Logins`**.
2. Chạy lần lượt 3 request:
   * `01. Login as Admin` (SĐT: 0903829145)
   * `02. Login as Merchant` (SĐT: 0908123456)
   * `03. Login as Driver` (SĐT: 0909128374)
3. **Postman Test Script** đã được cài đặt tự động:
   * Tự bóc tách JWT Token từ response và lưu trực tiếp vào các biến môi trường (`{{admin_token}}`, `{{merchant_token}}`, `{{driver_token}}`, `{{token}}`).
   * Các thư mục tiếp theo (`Merchant`, `Driver`, `Admin`, `AI`) đều đã được gắn quyền tương ứng, **bạn không cần copy paste token thủ công!**

---

## 🔄 Luồng kiểm thử End-to-End (E2E Test Workflow)

Chi tiết đầy đủ các bước thực hiện và kịch bản ngoại lệ được mô tả tại tài liệu:  
👉 **[Xem chi tiết Kịch bản kiểm thử tại TEST_SCENARIOS.md](./TEST_SCENARIOS.md)**
