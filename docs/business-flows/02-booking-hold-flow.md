# Luồng nghiệp vụ: Đặt chỗ & Giữ chỗ đỗ xe (Booking & Hold Flow)

## Sơ đồ tuần tự (Sequence Diagram)

```mermaid
sequenceDiagram
    autonumber
    actor Driver as Tài xế (Driver)
    participant Gateway as API Gateway
    participant Booking as Booking Service
    participant ParkingLot as Parking Lot Service
    participant Redis as Redis Cache
    participant Payment as Payment Service
    participant Kafka as Apache Kafka

    Driver->>Gateway: POST /api/bookings (lotId, slotId, vehiclePlate, time)
    Gateway->>Booking: Chuyển tiếp request (Kèm X-User-Id, X-Correlation-Id)
    
    Note over Booking,Redis: Bước 1: Khóa phân tán (Distributed Lock)
    Booking->>Redis: SETNX lock:slot:{slotId} (TTL 5s)
    
    alt Slot đang bị khóa bởi luồng khác
        Redis-->>Booking: Lock failed
        Booking-->>Gateway: 409 Conflict: "Chỗ đỗ đang được xử lý, vui lòng thử lại"
        Gateway-->>Driver: Hiển thị thông báo
    else Lấy khóa thành công
        Booking->>ParkingLot: GET /api/slots/{id} (Kiểm tra slot có AVAILABLE không)
        ParkingLot-->>Booking: Slot status = AVAILABLE
        
        Note over Booking: Bước 2: Tạo Booking tạm thời (Status = PENDING, Hold 10 phút)
        Booking->>Booking: INSERT INTO bookings (status='PENDING', hold_expired_at=NOW()+10m)
        Booking->>Booking: INSERT INTO outbox_events (event='BookingCreated')
        
        Booking->>ParkingLot: PATCH /api/slots/{id}/status (status='HOLD')
        
        Booking->>Payment: POST /api/payments/checkout-link (bookingId, amount)
        Payment-->>Booking: paymentUrl / QR code PayOS
        
        Booking-->>Gateway: 201 Created (bookingId, paymentUrl, holdExpiredAt)
        Gateway-->>Driver: Chuyển sang màn hình quét mã thanh toán
    end
```

## Nhánh thất bại và Xử lý ngoại lệ
1. **Quá 10 phút không thanh toán (`hold_expired_at`)**:
   - `BookingService` chạy Scheduled Job (mỗi 1 phút) quét các booking `PENDING` quá hạn.
   - Chuyển `status = EXPIRED`, ghi Outbox phát sự kiện `BookingCancelledEvent`.
   - `ParkingLotService` nhận event, cập nhật slot về lại `AVAILABLE`.
2. **Khách hàng chủ động hủy**:
   - Chuyển `status = CANCELLED`, nhả slot ngay lập tức.
