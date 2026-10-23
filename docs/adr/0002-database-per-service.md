# ADR 0002: Áp dụng Pattern Database-per-Service

## Trạng thái (Status)
Đã chấp thuận (Accepted) - 2026-10-23

## Bối cảnh (Context)
Nếu các microservice dùng chung 1 database (Shared Database), các service sẽ bị phụ thuộc chặt chẽ vào schema của nhau (Coupling). Khi sửa cấu trúc 1 bảng, nhiều service có thể bị lỗi, dẫn đến tình trạng "Distributed Monolith".

## Quyết định (Decision)
Mỗi microservice sở hữu một database/schema hoàn toàn riêng biệt:
- `smart_parking_identity_db`
- `smart_parking_lot_db`
- `smart_parking_booking_db`
- `smart_parking_payment_db`
- `smart_parking_notification_db`

Ở giai đoạn chạy trên VPS đơn lẻ, các schema này có thể cùng nằm trên 1 instance MySQL Server nhưng có user/password riêng và cấm quyền truy vấn chéo (No cross-database queries). Khi lên Production lớn, mỗi DB có thể tách sang các cụm MySQL riêng biệt.

## Hệ quả (Consequences)
- **Tích cực**: Cô lập schema và dữ liệu; các service thoải mái migrate schema mà không sợ ảnh hưởng service khác.
- **Tiêu cực**: Không thể thực hiện SQL JOIN giữa các bảng của các service khác nhau; phải dùng API Composition hoặc event streaming.
