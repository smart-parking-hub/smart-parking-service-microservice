# Security Model & Mô hình Tin cậy (Microservices Security)

## 1. Edge Authentication (Xác thực tại Gateway)
1. Request từ Client đi qua Nginx đến `api-gateway`.
2. Đối với các endpoint yêu cầu bảo mật, `api-gateway` trích xuất `Authorization: Bearer <JWT>`.
3. Gateway giải mã chữ ký JWT, kiểm tra thời hạn (exp). Nếu hợp lệ:
   - Trích xuất `userId`, `role`, `email`.
   - Bổ sung các header tin cậy nội bộ vào downstream request:
     - `X-User-Id: 1024`
     - `X-User-Role: ROLE_DRIVER`
     - `X-User-Email: user@example.com`
     - `X-Correlation-Id: c8905b63-95fa-4c4f-9e45-f0d014bc08d2`

## 2. Service-to-Service Trust & Network Isolation
- Tất cả microservice nằm trong Docker Private Network (`smart-parking-net`).
- Các cổng service nghiệp vụ (8081 - 8085) **không được expose ra Internet**; chỉ expose cổng Nginx (80/443) và Gateway (8080 nếu cần).
- Các service downstream đọc danh tính người dùng thông qua header `X-User-Id` do Gateway đã xác thực và lọc sạch trước khi gửi vào.

## 3. Secrets Management
- Không bao giờ commit mật khẩu database, JWT secret key, API key PayOS lên Git repository.
- Toàn bộ secret được inject thông qua file `.env` hoặc Docker Compose environment variables trên VPS.
