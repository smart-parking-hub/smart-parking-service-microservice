# Kiến Trúc Mục Tiêu (TO-BE Microservices Architecture)

## 1. Mô Tả Tổng Quan
Hệ thống chuyển đổi sang mô hình Microservices 6 lớp chuẩn Enterprise đặt tại `smart-parking-microservice`:
- **Edge Layer:** Nginx reverse proxy + Spring Cloud Gateway (Port 8080) lọc JWT, rate limiting bằng Redis và gắn correlation ID.
- **5 Core Microservices:**
  - `identity-service` (Port 8081) - Auth, User, Driver, Merchant
  - `parking-lot-service` (Port 8082) - Catalog bãi xe, sơ đồ slot, biểu phí, cache Redis thời gian thực
  - `booking-service` (Port 8083) - Giữ chỗ (Hold 10m), Check-in/out, Transactional Outbox
  - `payment-service` (Port 8084) - Thanh toán PayOS, Ví, Sổ cái kế toán kép (Ledger)
  - `notification-service` (Port 8085) - Kafka Consumer đa kênh (Email, Push, SMS), Idempotent Consumer
- **Data Layer:** Database-per-service (5 schema MySQL riêng biệt).
- **Communication:** Synchronous (OpenFeign) cho truy vấn tức thời + Asynchronous (Kafka) cho xử lý sự kiện phân tán.
- **Observability:** Prometheus + Grafana + OpenTelemetry trace ID.
