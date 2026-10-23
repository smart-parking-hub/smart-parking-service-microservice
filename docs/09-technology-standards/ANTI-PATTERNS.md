# Danh Sách Anti-Patterns Cần Tránh (Architecture & Operational Anti-Patterns)

AI Agent bắt buộc phải nhận diện và ngăn chặn các anti-pattern nguy hiểm sau trong suốt vòng đời dự án SmartParkingHub:

---

## 1. Anti-Patterns Kiến Trúc & Dữ Liệu

### 1.1. Distributed Monolith (Nguyên khối phân tán)
- **Dấu hiệu:** Các microservice chia nhỏ nhưng dùng chung 1 database schema; một service thay đổi schema làm các service khác lỗi; hoặc để release 1 tính năng phải deploy đồng loạt 5 service cùng lúc.
- **Cách khắc phục:** Triệt để áp dụng *Database-per-Service*, xác lập Bounded Contexts rõ ràng và versioning API Contracts.

### 1.2. Chia Service Theo Bảng SQL (Nano-Services)
- **Dấu hiệu:** Tạo service `orders-service`, `order-items-service`, `order-status-service`.
- **Cách khắc phục:** Gộp các thực thể liên quan chặt chẽ vào cùng 1 Aggregate Root theo chuẩn DDD (`Booking` chứa `BookingItem` và `BookingStatus`).

### 1.3. Dual-Write Problem (Ghi đồng thời không an toàn)
- **Dấu hiệu:** Trong cùng 1 hàm: `bookingRepository.save(booking)` xong gọi ngay `kafkaTemplate.send("booking-events", event)`. Nếu Kafka lỗi, DB đã lưu nhưng event bị mất; nếu DB rollback, event ma đã bị phát đi.
- **Cách khắc phục:** Sử dụng **Transactional Outbox Pattern**.

---

## 2. Anti-Patterns Giao Tiếp & Xử Lý Sự Cố

### 2.1. Blind Retry (Thử lại mù quáng)
- **Dấu hiệu:** Gọi API thanh toán PayOS bị timeout -> Tự động retry thêm 3 lần mà không có Idempotency Key -> Tài khoản khách hàng bị trừ tiền 4 lần.
- **Cách khắc phục:** Kiểm tra trạng thái giao dịch, sử dụng Idempotency Key, thiết lập Circuit Breaker và chuyển sang cơ chế kiểm tra bất đồng bộ.

### 2.2. Distributed Transaction SQL (Tưởng tượng 2PC)
- **Dấu hiệu:** Giữ một transaction SQL kéo dài qua mạng xuyên qua nhiều service.
- **Cách khắc phục:** Sử dụng **Saga Pattern** với các local transactions và Compensating Actions (hành động bù trừ).

### 2.3. Cuộc Gọi Đồng Bộ Dây Chuyền (Sync Chain)
- **Dấu hiệu:** Service A gọi B -> B gọi C -> C gọi D -> D gọi E. Bất kỳ service nào chậm sẽ làm nghẽn toàn bộ chuỗi.
- **Cách khắc phục:** Tách các bước không cần kết quả tức thời sang Kafka event.

---

## 3. Anti-Patterns Vận Hành & Bảo Mật

### 3.1. Dùng `:latest` Cho Production
- **Dấu hiệu:** Docker compose trên VPS chạy `image: smartparking/booking:latest`.
- **Cách khắc phục:** Luôn ghim cố định bằng commit hash hoặc immutable release tag (ví dụ: `v1.2.0-rc1` hoặc SHA digest).

### 3.2. Kubernetes Cho 1 Máy Chủ Đơn Lẻ (Over-Engineering)
- **Dấu hiệu:** Dựng K8s, Ingress controller, Helm chart chỉ để chạy vài container trên 1 VPS 4GB RAM.
- **Cách khắc phục:** Thành thạo Docker Compose, Nginx, Systemd, backup/restore trước. Chỉ lên K8s khi quy mô đội ngũ và hạ tầng nhiều node thực sự đòi hỏi.

### 3.3. Xem OpenTelemetry Là Tất Cả Observability
- **Dấu hiệu:** Cài OTel SDK nhưng không cấu hình Collector, không có Prometheus lưu trữ, Grafana không có dashboard và không có alert rules.
- **Cách khắc phục:** Hoàn thiện đủ 3 trụ cột: Log cấu trúc có Trace ID + Metrics với ngưỡng cảnh báo + Tracing truy vết luồng chậm.
