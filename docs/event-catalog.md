# Event Catalog - Danh mục Sự kiện Phân tán (Kafka Topics)

## Bổ sung SP-S3-UI-16 — Admin operations (2026-11-12)

Status: implemented in working tree, runtime verification pending. Owner: SPH. [ADR-0005](./adr/0005-admin-operations-notification-delivery.md), [runbook/replay](./deployment/admin-notifications-runbook.md).

| Topic | Producer / Consumer | Envelope / Hành vi |
| --- | --- | --- |
| `admin-operations.events` | Payment/Parking -> Notification group `notification-admin-operations` | DomainEvent eventId/eventType/timestamp/aggregateId + schemaVersion=1, targetId, actorId; amount/currency cho withdrawal, submissionVersion cho hồ sơ bãi. |
| `admin-recipient-snapshots.events` | Identity -> Notification group `notification-admin-recipients` | ADMIN_RECIPIENT_SNAPSHOT; DomainEvent + schemaVersion=1, snapshotVersion tăng, recipientIds chỉ ADMIN ACTIVE. Key admin-recipients, một partition. |
| Các topic trên + `.DLT` | Notification error handler -> operator | Retry transient 3 lần, 1s/lần; invalid envelope đi DLT; recoverer kiểm tra send result. Replay nguyên payload/key/eventId sau xử lý lỗi. |

Các event nghiệp vụ mới: `WITHDRAWAL_REQUESTED`, `DISPUTE_OPENED`, `PARKING_LOT_REVIEW_REQUESTED`. Không đưa chúng vào relay payment.events cũ. Mỗi producer dùng bảng admin_event_outbox của DB mình, phát String JSON; transaction ghi outbox cùng nghiệp vụ, relay giữ FAILED sau 5 lần để replay có kiểm soát. Snapshot reconciliation quét 10s khi danh sách đổi và heartbeat 60s; Notification từ chối snapshot quá 180s hoặc rỗng khi phân phối tin. Không có truy vấn DB Identity từ Notification.

Fan-out và processed marker cùng transaction, unique `(source_event_id,user_id)` chống trùng. Mỗi Admin đọc độc lập. Các listener cũ không được thay đổi và không tự nhận chính sách retry mới chỉ vì tài liệu này có mục bổ sung.

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

## Additive wallet fields — 2026-11-17

BOOKING_CANCELLED adds optional refundChannel (WALLET/BANK_MANUAL). Existing/missing values preserve manual bank processing. PAYMENT_REFUND_UPDATED adds refundChannel alongside the existing refund status/reference fields. Wallet COMPLETED means posted credit to the internal driver liability, not a bank transfer. PAYMENT_COMPLETED may carry paymentMethod=WALLET; existing paymentType=DEPOSIT and booking semantics remain unchanged. Both producers retain transactional outboxes and consumers retain event idempotency.

A cancellation racing wallet capture persists a REQUESTED WALLET refund for recovery; it does not rely only on Kafka retry count. See ADR 0009.
