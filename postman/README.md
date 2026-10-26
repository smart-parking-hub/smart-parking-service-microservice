# 🚀 SmartParkingHub - Postman API Collection & Environment

Tài liệu hướng dẫn sử dụng bộ sưu tập Postman Collection kiểm thử toàn diện hệ thống **SmartParkingHub Microservices**.

---

## 📂 Danh mục tệp tin
* 📄 [`SmartParkingHub_API_Collection.postman_collection.json`](./SmartParkingHub_API_Collection.postman_collection.json): Định dạng Postman Collection v2.1.0 gồm **19 thư mục** và **86 requests** phủ kín toàn bộ 17 Controller.
* 📄 [`SmartParkingHub_Local_Env.postman_environment.json`](./SmartParkingHub_Local_Env.postman_environment.json): File môi trường cấu hình sẵn `baseUrl: http://localhost:8088` cùng các biến động.

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
   * Các thư mục tiếp theo (`Merchant`, `Driver`, `Admin`) đều đã được gắn quyền tương ứng, **bạn không cần copy paste token thủ công!**

---

## 🔄 Luồng kiểm thử End-to-End (E2E Test Workflow)

Bạn có thể chạy thử theo kịch bản nghiệp vụ thực tế như sau:

1. **Đăng nhập**: Chạy `00. Authentication` để nạp token.
2. **Tài xế thêm xe**: Chạy `02. Driver - Vehicles Management` -> `02. Add New Vehicle` (ID xe tự lưu vào `{{vehicleId}}`).
3. **Chủ bãi tạo bãi đỗ xe**: Chạy `04. Merchant - Parking Lots Management` -> `01. Create Parking Lot` (ID bãi tự lưu vào `{{parkingLotId}}`).
4. **Cấu hình bãi**:
   * Chạy `06. Bulk Configure All Vehicle Slots` để thêm slot cho các loại xe.
   * Chạy `08. Bulk Configure Pricing Rules` để nạp bảng giá.
5. **Admin duyệt bãi xe**: Chạy `17. Admin - Parking Lots Moderation` -> `03. Admin - Approve Parking Lot`.
6. **Tài xế tìm kiếm & Đặt chỗ**:
   * Chạy `06. Public & Driver - Search & View Lots` -> `01. Search Parking Lots`.
   * Chạy `07. Driver - Booking Operations` -> `01. Create Booking` (Mã booking tự lưu vào `{{bookingId}}` và `{{bookingCode}}`).
7. **Thanh toán cọc**: Chạy `10. Driver - Payments` -> `02. Confirm Demo Payment (Instant Success)`.
8. **Xe đến bãi & Rời bãi**:
   * Chạy `08. Merchant - Check-In & Check-Out` -> `01. Check-In Vehicle into Lot`.
   * Chạy `02. Check-Out Vehicle from Lot` để tính tiền thực tế.
9. **Đánh giá bãi xe**: Chạy `13. Reviews & Ratings` -> `01. Driver - Create Review`.
10. **Rút tiền doanh thu**: Chạy `11. Merchant & Admin - Withdrawals` -> Merchant tạo lệnh rút, Admin phê duyệt lệnh.
