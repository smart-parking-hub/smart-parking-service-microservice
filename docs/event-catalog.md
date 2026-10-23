# Event Catalog - Danh mục Sự kiện Phân tán (Kafka Topics)

Danh mục tất cả các Domain Event được xuất bản và tiêu thụ trong kiến trúc SmartParkingHub.

## 1. Topic: `booking-events`
- **Partitions**: 3 | **Key**: `bookingId` | **Retention**: 7 ngày
- **Producer**: `booking-service` (thông qua Transactional Outbox)
- **Consumers**: `parking-lot-service`, `payment-service`, `notification-service`

| Event Type | Mô tả | Schema Payload tóm tắt |
| :--- | :--- | :--- |
| `BookingCreatedEvent` | Được phát khi tài xế tạo yêu cầu đặt chỗ tạm thời | `{ eventId, bookingId, userId, parkingLotId, slotId, vehiclePlate, totalAmount }` |
| `BookingConfirmedEvent` | Được phát khi thanh toán thành công, hoàn tất giữ chỗ | `{ eventId, bookingId, slotId, paymentRef, confirmedAt }` |
| `BookingCancelledEvent` | Phát khi hết hạn 10m hoặc tài xế hủy đơn | `{ eventId, bookingId, slotId, reason }` |

## 2. Topic: `payment-events`
- **Partitions**: 3 | **Key**: `bookingId` hoặc `userId` | **Retention**: 14 ngày
- **Producer**: `payment-service` (thông qua Transactional Outbox)
- **Consumers**: `booking-service`, `notification-service`

| Event Type | Mô tả | Schema Payload tóm tắt |
| :--- | :--- | :--- |
| `PaymentCompletedEvent` | Nhận webhook thành công từ cổng thanh toán PayOS | `{ eventId, paymentId, bookingId, userId, amount, transactionRef }` |
| `PaymentFailedEvent` | Thanh toán thất bại hoặc timeout | `{ eventId, paymentId, bookingId, userId, reason }` |

## 3. Chiến lược Idempotent Consumer & Error Retry
1. **Deduplication**: Consumer lưu `eventId` vào bảng `processed_events`. Nếu `eventId` đã tồn tại, lập tức bỏ qua.
2. **Dead Letter Queue (DLQ)**: Khi consumer gặp lỗi không thể khắc phục sau 3 lần retry với Exponential Backoff (1s, 2s, 4s), message được gửi vào topic DLQ (VD: `booking-events-dlq`) để đội vận hành kiểm tra thủ công.
