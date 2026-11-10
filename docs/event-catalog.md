# Event Catalog - Danh mục Sự kiện Phân tán (Kafka Topics)

Danh mục tất cả các Domain Event được xuất bản và tiêu thụ trong kiến trúc SmartParkingHub.

## 1. Topic: `booking.events`
- **Partitions**: 3 | **Key**: `bookingId` | **Retention**: 7 ngày
- **Producer**: `booking-service` (thông qua Transactional Outbox — rollback nguyên tử nếu lưu/tuần tự hóa Outbox thất bại)
- **Consumers**: `parking-lot-service`, `payment-service`, `notification-service`

| Event Type | Mô tả | Schema Payload tóm tắt |
| :--- | :--- | :--- |
| `BOOKING_CREATED` (`BookingCreatedEvent`) | Được phát khi tài xế tạo yêu cầu đặt chỗ tạm thời | `{ eventId, eventType, bookingId, userId, parkingLotId, licensePlate, totalAmount }` |
| `BOOKING_CHECKED_IN` / `BOOKING_COMPLETED` (`BookingConfirmedEvent`) | Được phát khi xe check-in tại cổng hoặc hoàn tất lượt đỗ | `{ eventId, eventType, bookingId, userId, merchantId, parkingLotId, totalAmount, advanceDeposit }` |
| `BOOKING_CANCELLED` (`BookingCancelledEvent`) | Phát khi hết hạn giữ chỗ/no-show hoặc tài xế hủy đơn kèm quyết định hoàn tiền | `{ eventId, eventType, bookingId, userId, merchantId, parkingLotId, reason, advanceDeposit, refundAmount, refundPercentage, refundRequested, refundStatus, refundPolicy, currency, cancelledAt, expectedCheckInTime }` |

## 2. Topic: `payment.events`
- **Partitions**: 3 | **Key**: `bookingId` hoặc `paymentId` | **Retention**: 14 ngày
- **Producer**: `payment-service` (thông qua Transactional Outbox)
- **Consumers**: `booking-service`, `notification-service`

| Event Type | Mô tả | Schema Payload tóm tắt |
| :--- | :--- | :--- |
| `PAYMENT_COMPLETED` (`PaymentCompletedEvent`) | Nhận webhook thành công từ cổng thanh toán PayOS | `{ eventId, eventType, paymentId, bookingId, userId, amount, paymentType, transactionRef }` |
| `PAYMENT_FAILED` (`PaymentFailedEvent`) | Thanh toán thất bại hoặc timeout | `{ eventId, eventType, paymentId, bookingId, userId, amount, paymentType, reason }` |
| `PAYMENT_REFUND_UPDATED` (`RefundStatusChangedEvent`) | Cập nhật trạng thái hoàn tiền thủ công (`REQUESTED`, `PROCESSING`, `COMPLETED`, `REQUIRES_RECONCILIATION`) | `{ eventId, eventType, refundRequestId, bookingId, paymentId, driverId, merchantId, refundAmount, refundPercentage, refundStatus, policyCode, transactionReference, evidenceUrl, reason, verifiedBy, verifiedAt }` |

## 3. Chiến lược Idempotent Consumer & Error Retry
1. **Deduplication**: Consumer trong `payment-service`, `booking-service` và `notification-service` lưu `eventId` vào bảng `processed_events` (ngoài khóa nghiệp vụ `refund:booking:{bookingId}` hoặc `SETTLE:PAYMENT:{paymentId}`). Nếu `eventId` đã tồn tại, lập tức bỏ qua.
2. **Dead Letter Queue (DLQ)**: Khi consumer gặp lỗi không thể khắc phục sau 3 lần retry với Exponential Backoff (1s, 2s, 4s), message được gửi vào topic DLQ để đội vận hành kiểm tra thủ công.
