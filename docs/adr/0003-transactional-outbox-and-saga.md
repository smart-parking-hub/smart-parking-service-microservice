# ADR 0003: Áp dụng Transactional Outbox Pattern & Saga Pattern

## Trạng thái (Status)
Đã chấp thuận (Accepted) - 2026-10-23

## Bối cảnh (Context)
Trong kiến trúc phân tán, bài toán "Dual Write Problem" xảy ra khi service phải vừa commit dữ liệu vào Database, vừa publish event lên Kafka:
- Nếu DB commit thành công nhưng mạng chập chờn dẫn tới bắn event Kafka thất bại -> Mất event.
- Nếu bắn event Kafka trước nhưng DB commit thất bại và rollback -> Consumer xử lý event rác (Phantom event).

Đồng thời, quy trình Đặt chỗ -> Giữ chỗ -> Thanh toán -> Xác nhận liên quan đến nhiều DB khác nhau, không thể dùng 2PC (Two-Phase Commit) vì gây tắc nghẽn hiệu năng.

## Quyết định (Decision)
1. Sử dụng **Transactional Outbox Pattern**:
   - Khi `BookingService` hoặc `PaymentService` lưu dữ liệu nghiệp vụ, bản ghi sự kiện cũng được INSERT vào bảng `outbox_events` trong CÙNG 1 local transaction ACID.
   - Một background worker (hoặc Debezium CDC) sẽ định kỳ quét bảng `outbox_events` và publish lên Kafka một cách đáng tin cậy. Sau khi broker xác nhận ACK, status đổi thành `PROCESSED`.
2. Sử dụng **Choreography Saga**:
   - Các service phối hợp thông qua các sự kiện phân tán (`BookingCreated`, `PaymentCompleted`, `PaymentFailed`, `BookingCancelled`).
   - Có cơ chế bù trừ (Compensating Transaction): nếu thanh toán thất bại, bắn event hủy đặt chỗ và nhả slot cho người khác.

## Hệ quả (Consequences)
- **Tích cực**: Đảm bảo At-Least-Once Delivery; loại bỏ hoàn toàn rủi ro mất mát event; hệ thống có độ chịu lỗi cao.
- **Tiêu cực**: Cần cài đặt bảng Outbox và tiến trình scheduler; các consumer bắt buộc phải idempotent (chống nhận duplicate).
