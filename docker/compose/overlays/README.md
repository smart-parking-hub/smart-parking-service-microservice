# Hướng dẫn chi tiết Docker Compose Overlays & Kịch bản Test

Thư mục `docker/compose/overlays/` chứa toàn bộ các **cấu hình ghi đè (Docker Compose Overlays / Extension Files)** dùng cho môi trường kiểm thử cục bộ (Local Testing / Labs) của hệ thống **SmartParkingHub Microservices**.

---

## 1. Nguyên lý hoạt động (Compose Merge Strategy)

Trong Docker Compose, bạn có thể truyền nhiều cờ `-f` theo thứ tự:
```bash
docker compose --project-directory . -f <file-base> -f <file-overlay-1> -f <file-overlay-2> up -d
```
* **File Base** (`docker/compose/local/compose.yaml`): Chứa định nghĩa đầy đủ của các services, networks, ports, cơ sở dữ liệu và Kafka.
* **File Overlay** (`docker/compose/overlays/compose.<kịch-bản>.yaml`): Chỉ khai báo đúng các trường cần thay đổi (image, environment, volume, hoặc thêm service phụ trợ). Docker Compose sẽ tự động hợp nhất (merge) các cấu hình này lại với nhau mà **không làm mất cấu hình gốc** và **không cần sửa code dự án**.

> [!TIP]
> Luôn chỉ định `--project-directory .` (hoặc chạy từ thư mục gốc của backend) để Docker Compose phân giải chính xác ngữ cảnh build và biến môi trường `.env`.

---

## 2. Chi tiết 7 kịch bản kiểm thử (7 Overlay Files)

### 2.1. `compose.email.local.yaml` (Test OTP / Hộp thư ảo Mailpit)
* **Mục đích:** Tạo phòng lab kiểm thử tính năng đăng ký, xác thực mã OTP qua Email hoàn toàn trên máy cục bộ mà không tốn cước phí gửi mail hay cần tài khoản SMTP thật.
* **Cơ chế:**
  - Khởi tạo container **`mailpit`** (giao diện web xem email ảo tại `http://127.0.0.1:8025`).
  - Ghi đè `notification-service` trỏ SMTP vào `mailpit:1025` (tắt authentication và TLS).
* **Lệnh chạy:**
  ```powershell
  docker compose --project-directory . --project-name docker --env-file .env.p0.local -f docker/compose/overlays/compose.email.local.yaml up -d --build
  ```

---

### 2.2. `compose.smtp.local.yaml` (Gửi Email thật qua Gmail / SendGrid)
* **Mục đích:** Gửi email OTP / thông báo thật ra ngoài Internet cho người dùng thực tế.
* **Cơ chế:**
  - Ghi đè 7 biến môi trường `MAIL_*` của `notification-service` lấy từ file `.env.email.local` (file này nằm trong `.gitignore` để tránh lộ mật khẩu).
  - Không khởi tạo thêm container nào, không thay đổi database.
* **Yêu cầu:** Tạo file `.env.email.local` tại thư mục gốc backend với nội dung:
  ```dotenv
  MAIL_HOST=smtp.gmail.com
  MAIL_PORT=587
  MAIL_USERNAME=your-email@gmail.com
  MAIL_FROM=your-email@gmail.com
  MAIL_PASSWORD=your-app-password
  MAIL_SMTP_AUTH=true
  MAIL_STARTTLS_ENABLE=true
  ```
* **Lệnh áp dụng (chỉ restart riêng `notification-service`):**
  ```powershell
  docker compose --project-directory . --project-name docker --env-file .env --env-file .env.email.local -f docker/compose/local/compose.yaml -f docker/compose/overlays/compose.restore-p0.local.yaml -f docker/compose/overlays/compose.smtp.local.yaml up -d --no-deps --pull never notification-service
  ```

---

### 2.3. `compose.no-request-rate.local.yaml` (Ghim artifact Booking cho P0)
* **Mục đích:** Giữ artifact Booking đã kiểm chứng cho dữ liệu P0. Đây không phải công tắc stress test tổng quát.
* **Cơ chế:**
  - File này chỉ vỏn vẹn 4 dòng.
  - Chỉ đổi image của `booking-service` sang tag `smart-parking/booking-service:no-request-rate-local-20261117`. Artifact bỏ giới hạn tần suất request cũ; quota/cooldown vẫn có hợp đồng riêng.
* **Lệnh áp dụng:**
  Thường được ghép sau `compose.schedule.local.yaml` khi chạy kịch bản P0.

---

### 2.4. `compose.schedule.local.yaml` (Release tính năng Đặt lịch mới)
* **Mục đích:** Áp dụng bản vá tính năng Đặt chỗ theo lịch trình (Booking Schedule Release) đã được kiểm thử cố định ngày 17/11/2026.
* **Cơ chế:**
  - Ghim image đã test cho 2 services: `parking-lot-service:schedule-local-20261117` và `booking-service:schedule-local-20261117`.
  - Mount volume lưu trữ ảnh bãi xe `parking_images` vào `/app/uploads/parking-lot-images` để bảo toàn ảnh thật của bãi đỗ xe (Lot 37).
* **Lệnh chạy:**
  Được tự động áp dụng qua script `scripts\dev\start-p0-local.bat`.

---

### 2.5. `compose.p0.local.yaml` (Môi trường tích hợp chuẩn P0)
* **Mục đích:** Cấu hình chuẩn tích hợp cục bộ cho toàn bộ hệ sinh thái Microservice.
* **Cơ chế:**
  - Bật cờ thanh toán giả lập `APP_DEMO_PAYMENT_ENABLED: "true"` ở `payment-service`.
  - Cấu hình timezone `Asia/Ho_Chi_Minh` đồng nhất cho tất cả các container.
  - Cấu hình chuỗi kết nối Database P0 (`smart_parking_*_p0_20261106`).
  - Nạp token xác thực nội bộ `P0_INTERNAL_SERVICE_TOKEN` từ `.env.p0.local`.

---

### 2.6. `compose.restore-p0.local.yaml` (Khôi phục Dataset P0)
* **Mục đích:** Giữ nguyên dữ liệu lịch sử P0 (Retained Dataset), đảm bảo không bị Flyway reset database hay mất mát dữ liệu kế toán/giao dịch.
* **Cơ chế:**
  - Ghim cấu hình database URL P0 có sẵn.
  - Giữ nguyên lineage di chuyển dữ liệu V6/V7 của thanh toán.
  - Cố định SMTP về Mailpit local cho kịch bản P0 khôi phục.

---

### 2.7. `compose.payos-local-v6-v7.yaml` (Tương thích Flyway PayOS)
* **Mục đích:** Khắc phục xung đột Flyway checksum trên cơ sở dữ liệu Payment cục bộ cũ đã lỡ chạy migration V6/V7.
* **Cơ chế:**
  - Đặt `SPRING_FLYWAY_LOCATIONS=classpath:db/local-v6-v7-compatible` riêng cho `payment-service` để Flyway bỏ qua kiểm tra checksum xung đột trên các script cũ.

---

## 3. Bảng tổng hợp tra cứu nhanh

| File Overlay | Service ảnh hưởng | Mục đích sử dụng | Khi nào cần dùng |
| :--- | :--- | :--- | :--- |
| `compose.email.local.yaml` | `identity`, `notification`, `mailpit` | Hòm thư ảo Mailpit (Port 8025) | Cần test nhận OTP qua web UI |
| `compose.smtp.local.yaml` | `notification-service` | Gửi mail thật (Gmail, v.v.) | Cần gửi thư thật đến hòm thư người dùng |
| `compose.no-request-rate.local.yaml` | `booking-service` | Ghim artifact Booking P0 | Dùng trong chuỗi restore/schedule đã kiểm chứng |
| `compose.schedule.local.yaml` | `parking-lot`, `booking` | Cập nhật bản build tính năng Schedule | Chạy tính năng đặt chỗ theo khung giờ |
| `compose.p0.local.yaml` | Tất cả microservices | Tích hợp chuẩn P0 (Demo payment) | Chạy môi trường test tích hợp đầy đủ |
| `compose.restore-p0.local.yaml` | Tất cả microservices | Khôi phục & ghim dữ liệu P0 gốc | Khởi động hệ thống giữ nguyên data test |
| `compose.payos-local-v6-v7.yaml` | `payment-service` | Tương thích migration PayOS V6/V7 | Fix lỗi Flyway trên DB Payment cục bộ |

---

## 4. Cách tự tạo thêm Overlay mới (Kịch bản kiểm thử riêng)

Nếu bạn muốn tạo thêm một kịch bản test mới (ví dụ: bật cổng Remote Debug `5005`, thay đổi dung lượng Redis, mock dịch vụ AI...), hãy làm theo các bước sau:

### Quy tắc vàng:
1. **Chỉ khai báo phần thay đổi**: Không copy toàn bộ cấu hình gốc, chỉ ghi đúng trường cần đè.
2. **Đặt tên file theo quy ước**: `compose.<tên-tính-năng>.local.yaml`.
3. **Thêm cờ `-f` ở cuối**: File nào truyền sau sẽ có quyền ghi đè giá trị của file trước.

---

### Mẫu 1: Tạo overlay Bật Debug Port (JVM 5005)
Tạo file `docker/compose/overlays/compose.debug-identity.local.yaml`:
```yaml
# Bật remote debugging port 5005 cho identity-service
services:
  identity-service:
    ports:
      - "5005:5005"
    environment:
      JAVA_TOOL_OPTIONS: "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
```
**Chạy:**
```powershell
docker compose --project-directory . -f docker/compose/local/compose.yaml -f docker/compose/overlays/compose.debug-identity.local.yaml up -d identity-service
```

---

### Mẫu 2: Tạo overlay Bật Log DEBUG chi tiết cho Booking Service
Tạo file `docker/compose/overlays/compose.verbose-booking.local.yaml`:
```yaml
# Tăng mức độ log để soi lỗi luồng đặt chỗ
services:
  booking-service:
    environment:
      LOGGING_LEVEL_COM_SMARTPARKING: "DEBUG"
      LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_WEB: "DEBUG"
```
**Chạy:**
```powershell
docker compose --project-directory . -f docker/compose/local/compose.yaml -f docker/compose/overlays/compose.verbose-booking.local.yaml up -d booking-service
```

---

### Mẫu 3: Tạo overlay Tắt Eureka / Dùng Mock URL
Tạo file `docker/compose/overlays/compose.mock-ai.local.yaml`:
```yaml
# Trỏ AI Service URL sang Mock Server ngoài máy host
services:
  api-gateway:
    environment:
      AI_SERVICE_URL: "http://host.docker.internal:9999"
```
