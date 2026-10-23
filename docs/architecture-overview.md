# SmartParkingHub - Tổng quan Kiến trúc Microservices (Architecture Overview)

## 1. Bức tranh tổng thể Hệ thống

Hệ thống **SmartParkingHub** được thiết kế theo mô hình Microservices 6 lớp chuẩn Production nhằm phục vụ nền tảng đặt và quản lý bãi đỗ xe thông minh thời gian thực.

```mermaid
graph TD
    Client["Clients: Web React / Mobile Driver / Admin"] --> Nginx["Nginx (Reverse Proxy & TLS Termination)"]
    Nginx --> Gateway["API Gateway (Spring Cloud Gateway :8080)"]

    subgraph "Edge & Cross-Cutting Platform"
        Gateway
        Prometheus["Prometheus :9090"]
        Grafana["Grafana :3001"]
        Redis[("Redis Cluster / Standalone :6379\n(Slot Cache, Rate Limit, Distributed Lock)")]
        Kafka[("Apache Kafka :9092\n(Event Streaming)")]
    end

    subgraph "Core Business Microservices"
        Gateway -->|/api/auth/**, /api/users/**| Identity["Identity Service :8081"]
        Gateway -->|/api/parking-lots/**, /api/slots/**| ParkingLot["Parking Lot Service :8082"]
        Gateway -->|/api/bookings/**| Booking["Booking Service :8083"]
        Gateway -->|/api/payments/**, /api/wallets/**| Payment["Payment Service :8084"]
        Gateway -->|/api/notifications/**| Notification["Notification Service :8085"]
    end

    subgraph "Data Layer (Database-per-Service)"
        Identity --> DB_Identity[("MySQL\nsmart_parking_identity_db")]
        ParkingLot --> DB_ParkingLot[("MySQL\nsmart_parking_lot_db")]
        Booking --> DB_Booking[("MySQL\nsmart_parking_booking_db")]
        Payment --> DB_Payment[("MySQL\nsmart_parking_payment_db")]
        Notification --> DB_Notification[("MySQL\nsmart_parking_notification_db")]
    end

    subgraph "Event-Driven & Messaging"
        Booking -.->|Publish BookingCreated/Confirmed| Kafka
        Payment -.->|Publish PaymentCompleted/Failed| Kafka
        Kafka -.->|Consume Events| Notification
        Kafka -.->|Consume PaymentCompleted| Booking
        Kafka -.->|Update Slot Availability| ParkingLot
    end
```

## 2. Phân chia 6 Lớp (6-Layer Architecture)
1. **Edge & Access**: Nginx kết thúc TLS, chuyển tiếp request đến API Gateway. Gateway lọc JWT, rate limiting bằng Redis, inject `X-User-Id`, `X-User-Role`, `X-Correlation-Id`.
2. **Business Services**: Các service nghiệp vụ độc lập (`identity-service`, `parking-lot-service`, `booking-service`, `payment-service`, `notification-service`).
3. **Communication**:
   - *Synchronous (REST / Feign)*: Truy vấn dữ liệu tức thời (VD: Booking gọi ParkingLot lấy thông tin slot).
   - *Asynchronous (Kafka)*: Xử lý quy trình dài, phát sự kiện và thông báo không chặn luồng chính.
4. **Data Layer**: Áp dụng triệt để *Database-per-Service*. Không service nào được truy cập DB của service khác. Đồng bộ dữ liệu qua Eventual Consistency (Kafka) và Transactional Outbox.
5. **Platform & Security**: JWT stateless, secrets quản lý qua Environment Variables, Docker isolated network.
6. **Operations & Observability**: Logs tập trung với Correlation ID, Prometheus thu thập Actuator metrics, Grafana dashboard theo dõi SLO/SLA.
