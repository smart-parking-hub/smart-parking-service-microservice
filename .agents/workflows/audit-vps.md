# Workflow: /audit-vps - Quy Trình Rà Soát Sức Khỏe VPS Chỉ Đọc

## Mục Đích
Kích hoạt quy trình kiểm tra toàn diện hiện trạng tài nguyên, container và logs trên VPS mà không làm thay đổi cấu hình hay gián đoạn dịch vụ.

## Các Bước Thực Thi Của Agent (SOPs)

1. **Kiểm Tra Trạng Thái Container:**
   - Chạy `docker compose ps` để xác nhận tất cả 6 microservices và infra (MySQL, Redis, Kafka) đều `Up (healthy)`.
   - Ghi nhận container nào bị `Exit` hoặc restart liên tục.

2. **Kiểm Tra Tài Nguyên Máy Chủ:**
   - Dung lượng đĩa (`df -h`): Cảnh báo nếu partition root > 85%.
   - Bộ nhớ RAM (`free -m`): Cảnh báo nếu dung lượng RAM khả dụng < 500MB và swap bị đầy.
   - CPU load (`uptime`, `top`).

3. **Kiểm Tra Log Cảnh Báo Gần Nhất:**
   - Quét 100 dòng log cuối của các container nghiệp vụ:
     `docker compose logs --tail 100 booking-service | grep -E "ERROR|WARN"`
     `docker compose logs --tail 100 payment-service | grep -E "ERROR|WARN"`

4. **Kiểm Tra Kafka Consumer Lag & DB Connection Pool:**
   - Kiểm tra metrics `/actuator/prometheus` về HikariCP active connections.

5. **Xuất Báo Cáo Sức Khỏe Hạ Tầng:**
   - Tóm tắt tổng quan: Xanh (Healthy) / Vàng (Warning) / Đỏ (Critical).
   - Đề xuất giải pháp (tối ưu query, dọn dẹp log cũ, tăng swap...).
   - **Tuyệt đối không tự tiện restart container hay sửa cấu hình.**
