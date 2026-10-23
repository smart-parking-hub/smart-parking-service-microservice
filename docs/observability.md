# Observability - Giám sát, Logs, Metrics & Tracing

Hệ thống cung cấp đủ 3 trụ cột Observability theo chuẩn vận hành Microservices thực tế:

## 1. Structured Logging với Correlation ID
- Mọi log ghi ra console đều có format JSON chứa các metadata:
  - `timestamp`: Thời gian chuẩn ISO-8601.
  - `service`: Tên microservice (`booking-service`, `payment-service`, ...).
  - `correlationId`: Mã định danh duy nhất xuyên suốt các chặng (Gateway -> Service A -> Service B).
  - `userId`: Mã người dùng nếu đã đăng nhập.
  - `level`: INFO, WARN, ERROR.
  - `message` & `stackTrace`.

## 2. Metrics & Dashboard (Prometheus + Grafana)
- Tất cả microservice tích hợp `spring-boot-starter-actuator` và `micrometer-registry-prometheus`.
- Prometheus tự động scrape `/actuator/prometheus` mỗi 15 giây.
- Chỉ số cốt lõi được giám sát:
  - **Throughput**: Số lượng request/giây (RPS) theo endpoint và HTTP status.
  - **Latency**: P95 và P99 response time (mili-giây).
  - **Error Rate**: Tỷ lệ mã lỗi 5xx trên tổng số request (> 1% sẽ cảnh báo).
  - **JVM Health**: Heap memory, Garbage Collection pauses, Active thread pool.
  - **Connection Pools**: HikariCP active/idle connections.
  - **Kafka Lag**: Độ trễ tiêu thụ message của các consumer group.

## 3. Distributed Tracing (Truy vết phân tán)
- Khi khách hàng báo "Bấm đặt chỗ nhưng bị quay tròn hoặc lỗi":
  1. Lấy `correlationId` từ phản hồi của Gateway hoặc log trình duyệt.
  2. Tìm kiếm trong Grafana / Loki bằng câu lệnh: `{service=~".+"} |= "correlationId"`.
  3. Xác định ngay request dừng ở Gateway, Booking, ParkingLot hay Payment.
