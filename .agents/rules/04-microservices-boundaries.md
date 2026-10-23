# Rule 04: Bảo Toàn Ranh Giới Microservices & Dữ Liệu (Microservices Boundaries)

## 1. Nguyên Tắc Database-per-Service
- Mỗi Microservice là chủ nhân độc quyền của cơ sở dữ liệu/schema mà nó quản lý.
- **Cấm Tuyệt Đối:**
  - Microservice A kết nối trực tiếp vào Database của Microservice B.
  - Viết câu lệnh SQL có mệnh đề `JOIN` xuyên giữa 2 database khác nhau.
  - Sử dụng chung một bảng SQL cho nhiều microservice.

## 2. Giao Tiếp Nội Bộ (Inter-Service Communication)
1. **Giao tiếp Đồng bộ (Sync):**
   - Chỉ sử dụng OpenFeign Client hoặc Spring RestClient khi cần kết quả trả về ngay lập tức để hoàn thành yêu cầu người dùng (ví dụ: `BookingService` xác minh thông tin `Slot` với `ParkingLotService`).
   - Bắt buộc cấu hình Timeout: `connectTimeout <= 3000ms`, `readTimeout <= 5000ms`.
   - Bắt buộc bọc bởi Circuit Breaker (Resilience4j).
2. **Giao tiếp Bất đồng bộ (Async):**
   - Sử dụng Kafka cho các nghiệp vụ dài (thanh toán webhook, thông báo, hoàn tiền).
   - Khi ghi dữ liệu nghiệp vụ và phát sinh sự kiện, bắt buộc áp dụng **Transactional Outbox Pattern** để chống mất mát event (Dual-write problem).
   - Phía nhận event bắt buộc triển khai **Idempotent Consumer** (bảng `processed_events`) để loại trừ duplicate event.
