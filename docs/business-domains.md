# Bounded Context & Business Domains - SmartParkingHub

Dựa theo nguyên lý Domain-Driven Design (DDD), hệ thống phân định rõ ranh giới nghiệp vụ:

## 1. Identity & Access Context (`identity-service`)
- **Domain Entities**: `User`, `Role`, `DriverProfile`, `MerchantProfile`, `RefreshToken`, `OtpChallenge`.
- **Invariants (Bất biến)**:
  - Một email hoặc username chỉ thuộc về duy nhất 1 User.
  - Driver phải có số điện thoại hợp lệ để nhận OTP.
  - Merchant phải có `tax_code` hoặc giấy phép kinh doanh được Admin phê duyệt.

## 2. Parking Lot & Slot Catalog Context (`parking-lot-service`)
- **Domain Entities**: `ParkingLot`, `Slot`, `PricingRule`, `ParkingLotImage`.
- **Invariants**:
  - Mã chỗ (`slot_code`) là duy nhất trong cùng 1 bãi xe (`parking_lot_id`).
  - Trạng thái slot: `AVAILABLE`, `HOLD`, `OCCUPIED`, `MAINTENANCE`.
  - Không được xóa bãi xe hoặc slot nếu đang có booking hoạt động.

## 3. Booking & Check-in Context (`booking-service`)
- **Domain Entities**: `Booking`, `QueueEntry`, `OutboxEvent`.
- **Invariants**:
  - Một slot tại một thời điểm chỉ cho phép 1 xe giữ chỗ (`HOLD`) hoặc đỗ (`OCCUPIED`).
  - Lượt giữ chỗ (`HOLD`) tự động hết hạn sau 10 phút nếu chưa hoàn tất thanh toán.
  - Check-in chỉ hợp lệ trong khoảng thời gian cho phép (+/- 15 phút so với `start_time`).
  - Check-out quá giờ (`overtime`) tự động kích hoạt tính phí phụ trội.

## 4. Payment & Financial Ledger Context (`payment-service`)
- **Domain Entities**: `Payment`, `Wallet`, `WalletLedgerEntry`, `PaymentIdempotencyKey`.
- **Invariants**:
  - Mỗi giao dịch thanh toán bắt buộc có `idempotency_key` để chống trừ tiền lặp.
  - Số dư ví (`balance`) luôn được kiểm soát bằng Optimistic Locking và Double-Entry Bookkeeping (bảng `wallet_ledger_entries` ghi nhận mọi dòng tiền Debit/Credit).
  - Không bao giờ có số dư âm đối với ví tài xế.

## 5. Notification Context (`notification-service`)
- **Domain Entities**: `Notification`, `ProcessedEvent`.
- **Invariants**:
  - Xử lý bất đồng bộ, bảo đảm *At-least-once delivery* kết hợp *Idempotent Consumer* (bảng `processed_events`) để không gửi email/tin nhắn trùng lặp tới người dùng.
