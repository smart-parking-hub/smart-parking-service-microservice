# Luồng nghiệp vụ: Thanh toán & Đối soát (Payment & Settlement Flow)

## Sơ đồ tuần tự (Sequence Diagram)

```mermaid
sequenceDiagram
    autonumber
    actor Driver as Tài xế (Driver)
    participant PayOS as Cổng thanh toán (PayOS Gateway)
    participant Payment as Payment Service
    participant Kafka as Apache Kafka
    participant Booking as Booking Service
    participant ParkingLot as Parking Lot Service
    participant Notification as Notification Service

    Driver->>PayOS: Quét mã VietQR thanh toán thành công
    PayOS->>Payment: POST /api/payments/webhook (Payload kèm chữ ký HMAC)
    
    Note over Payment: Bước 1: Xác thực chữ ký webhook (HMAC-SHA256)
    alt Chữ ký không hợp lệ
        Payment-->>PayOS: 400 Bad Request
    else Chữ ký hợp lệ
        Note over Payment: Bước 2: Kiểm tra Idempotency Key
        alt Webhook đã xử lý trước đó
            Payment-->>PayOS: 200 OK (Bỏ qua xử lý lặp)
        else Webhook mới
            Payment->>Payment: UPDATE payments SET status='COMPLETED'
            Payment->>Payment: INSERT INTO wallet_ledger_entries (ghi nhận doanh thu)
            Payment->>Payment: INSERT INTO outbox_events (PaymentCompletedEvent)
            Payment-->>PayOS: 200 OK
            
            Note over Payment,Kafka: Outbox Publisher bắn event lên Kafka
            Payment->>Kafka: Publish topic "payment-events" (PaymentCompletedEvent)
        end
    end

    par Booking Service Consume
        Kafka->>Booking: Consume PaymentCompletedEvent
        Booking->>Booking: UPDATE bookings SET status='CONFIRMED'
    and Parking Lot Service Consume
        Kafka->>ParkingLot: Consume PaymentCompletedEvent
        ParkingLot->>ParkingLot: UPDATE slots SET status='RESERVED'
    and Notification Service Consume
        Kafka->>Notification: Consume PaymentCompletedEvent
        Notification->>Notification: Gửi Email / In-app notification vé đặt chỗ thành công
    end
```

## Bảo đảm tính toàn vẹn tài chính
1. **Idempotency**: Tránh duplicate callback từ cổng thanh toán.
2. **Double-entry Bookkeeping**: Mọi khoản thu vào đều có bút toán đối ứng vào ví hoặc tài khoản ký quỹ.
3. **Outbox Pattern**: Ngăn ngừa trường hợp DB cập nhật thanh toán thành công nhưng broker Kafka gặp trục trặc gây mất sự kiện.
