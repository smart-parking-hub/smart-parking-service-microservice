# Service Catalog - Danh mục Dịch vụ SmartParkingHub

| Service Name | Port Nội bộ | Port Public | Database / Storage | Dependencies | Trách nhiệm chính |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **api-gateway** | 8080 | 8080 / 80 | Redis (Rate limit) | All services | Cửa ngõ API duy nhất, routing, CORS, kiểm tra JWT, inject Trace ID |
| **identity-service** | 8081 | Không | `smart_parking_identity_db` | MySQL | Đăng ký, đăng nhập, cấp refresh token, quản lý hồ sơ User, Driver, Merchant |
| **parking-lot-service**| 8082 | Không | `smart_parking_lot_db`, Redis, private layout filesystem volume | MySQL, Redis, persistent filesystem volume | Quản lý danh mục bãi xe, sơ đồ mặt bằng (layout), chỗ đỗ vật lý (physical slots), biểu phí, cache chỗ trống |
| **booking-service** | 8083 | Không | `smart_parking_booking_db` | MySQL, Redis, Kafka, ParkingLot, Payment | Tạo đặt chỗ, giữ chỗ tạm thời (TTL 10m), Check-in / Check-out, Transactional Outbox |
| **payment-service** | 8084 | Không | `smart_parking_payment_db` | MySQL, Kafka, PayOS Gateway | Xử lý thanh toán PayOS/VNPay, quản lý số dư Ví, sổ cái kế toán kép (Ledger), Webhook |
| **notification-service**| 8085 | Không | `smart_parking_notification_db`| MySQL, Kafka, SMTP | Tiêu thụ event từ Kafka, gửi email thông báo, push notification, SMS, chống nhận trùng event |
| **discovery-server** | 8761 | 8761 (Local) | In-Memory Registry | Eureka Server | Đăng ký & phát hiện dịch vụ (Service Discovery) trên môi trường Local Lab |
| **config-server** | 8888 | 8888 (Local) | Git / Classpath Native | Spring Cloud Config | Quản lý cấu hình tập trung phân tán trên môi trường Local Lab |
