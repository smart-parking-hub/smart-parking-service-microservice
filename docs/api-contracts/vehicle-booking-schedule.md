# Không đặt trùng lịch cho cùng phương tiện

Ngày kiểm chứng: 2026-11-18. Phạm vi: booking-service, `POST /api/v1/bookings`.

## Hành vi

- Một `vehicleId` không được có hai booking còn hiệu lực trùng khoảng thời gian, ở cùng bãi hoặc khác bãi. Kiểm tra không phụ thuộc `driverId` của booking cũ.
- Booking policy 2 giữ khoảng có thể sử dụng xe `[expectedCheckInTime - 15 phút, expectedCheckOutTime)`. Booking cũ đang chờ vẫn dùng giờ vào đã lưu. Hai khoảng có thể chạm biên khi xe đã sẵn sàng cho lượt sau.
- `PENDING_PAYMENT`, `PENDING_CHECKIN` chiếm khoảng thời gian dự kiến. Chính sách giới hạn vé chưa thanh toán hiện có vẫn áp dụng riêng.
- `PARKING`, `OVERTIME`, `AWAITING_FINAL_PAYMENT` chiếm khoảng từ `checkInTime` thực tế (nếu có) đến giờ ra dự kiến. Nếu đã đến/quá giờ ra mà chưa hoàn tất lượt đỗ, xe tiếp tục bị chặn cho đến khi lượt cũ kết thúc. Điều này tránh cho phép đặt xe đang quá giờ chỉ vì lịch dự kiến đã hết.
- `COMPLETED`, `REVIEWED`, `CANCELLED`, `EXPIRED` không chặn lịch mới.
- Không suy diễn xe rời bãi chỉ từ trường `checkOutTime` khi booking vẫn `AWAITING_FINAL_PAYMENT`: trạng thái này còn giữ chỗ để xử lý tại cổng.
- Xe đang đỗ chưa quá giờ vẫn có thể đặt một lượt tương lai sau giờ ra dự kiến.

Khi xung đột, trả HTTP **409**, mã `CONFLICT`, thông báo:

> Xe này đã có booking trùng thời gian. Vui lòng chọn thời gian khác hoặc hoàn tất lượt đỗ hiện tại.

Frontend giữ form để tài xế đổi xe hoặc giờ. Không tạo booking mới, không giữ thêm vị trí, không bắt đầu thanh toán ví/PayOS. Không trả thông tin booking của người khác.

## Đồng thời và tính lặp an toàn

`BookingServiceImpl` kiểm tra trong `BookingCreationTransaction` với isolation `READ_COMMITTED`, dưới khóa `VEHICLE:<vehicleId>` của bảng `booking_hold_policy_locks`. Khóa luôn có hiệu lực, kể cả khi tắt giới hạn giữ chỗ chưa thanh toán. Kiểm tra xảy ra trước `saveAndFlush` và trước `holdSlot`. Replay hợp lệ của cùng `requestId` vẫn trả booking cũ.

Dữ liệu chỉ đọc từ database booking-service. Không đổi DTO, endpoint, event hay schema; dùng bảng khóa hiện có.

## Trường hợp kiểm thử

| Trường hợp | Kết quả |
|---|---|
| Xe đã cọc 10:00–12:00, đặt tiếp 11:00–13:00 tại cùng/khác bãi | 409 |
| Khoảng mới nằm trong, bao trùm, trùng hoàn toàn hoặc giao đầu/cuối khoảng cũ | 409 |
| Xe đang đỗ và giờ mới giao khoảng đỗ | 409 |
| Xe check-in sớm, giờ mới giao thời gian check-in thực tế | 409 |
| Xe vẫn PARKING/OVERTIME/chờ quyết toán sau giờ ra dự kiến | 409 cho đến khi kết thúc lượt cũ |
| Lượt trước kết thúc 12:00, lượt mới đặt 12:00 với quyền vào từ 11:45 | 409 |
| Lượt trước kết thúc 12:00, lượt mới đặt 12:30 với quyền vào từ 12:15 | Cho phép |
| Booking cũ đã hoàn tất/đánh giá/hủy/hết hạn | Cho phép |
| Xe khác cùng khung giờ | Cho phép theo chỗ trống của bãi |
| Xe đang đỗ chưa quá giờ, đặt lượt sau giờ ra dự kiến | Cho phép |
| Gửi lại cùng requestId hợp lệ | Trả booking cũ, không giữ chỗ lần nữa |
| Bốn yêu cầu đồng thời cùng xe, tắt giới hạn giữ chỗ | Một booking, ba xung đột |

`VehicleBookingConflictIntegrationTest`: 25 tình huống, H2 MySQL mode (gồm round-trip JPA/JDBC). Trước sửa: 15 lỗi tái hiện; sau sửa: đạt. Khi tách PR độc lập từ main, toàn bộ `:booking-service:test`: 201 tình huống, 197 đạt, 0 lỗi, 4 bỏ qua theo cấu hình môi trường. Kết quả 211 ca của lượt triển khai trước còn gồm thay đổi checkout được tách sang PR riêng. Chưa chạy các ca xung đột lịch mới trên MySQL thật.

## Rủi ro và khôi phục

Thay đổi chặn các yêu cầu mới; không sửa booking lịch sử hay tiền ví. Khóa theo phương tiện dùng chung thứ tự với khóa hạn mức hiện tại. Nếu cần rollback, bỏ phần kiểm tra mới trong `BookingServiceImpl` và `HoldPolicyStore.hasScheduleConflict`; không có migration hoặc dữ liệu cần khôi phục. Chưa triển khai/restart dịch vụ đang chạy.

Host UTC được kiểm chứng bằng JAVA_TOOL_OPTIONS=-Duser.timezone=UTC. Booking khởi tạo business timezone trước datasource/Hibernate để JPA và JDBC so sánh cùng giá trị wall-clock. Xem review PR để đối chiếu 9 lỗi trước sửa và kết quả sau sửa.


## Kiểm chứng bổ sung sau CI: binding thời gian giữa Hibernate và JDBC

Commit đổi thứ tự khởi tạo timezone đơn thuần đã đạt local nhưng CI vẫn lỗi 10 ca, gồm test round-trip mới. Vì vậy, kết quả local đó không đủ để kết luận lỗi đã được xử lý hoàn toàn.

Hibernate 6.5.3 mặc định có thể chuyển LocalDateTime qua java.sql.Timestamp, phụ thuộc timezone của phiên database. Booking bật `hibernate.type.java_time_use_direct_jdbc` qua HibernatePropertiesCustomizer để JDBC 4.2 bind/read giá trị LocalDateTime trực tiếp. Setting đã được xác minh trong Hibernate runtime đang dùng; không thêm dependency hoặc đổi schema.

Fixture lịch xe còn ép `SET TIME ZONE 'UTC'` trên mọi Hikari connection để không phụ thuộc timezone đã được H2 cache từ một test khác. Giữ nguyên assertion đối chiếu JPA → JDBC và các ca đồng thời/biên lịch.

Sau sửa: toàn Booking Service với JVM UTC và fixture DB session UTC có **201 ca: 197 đạt, 4 bỏ qua, 0 lỗi** (`pr-vehicle-direct-jdbc.log`). Không sửa dữ liệu lịch sử, không thay đổi giá/HTTP contract, không deploy. Kết quả CI cuối cùng trên SHA mới được ghi trong Post-push review của PR.
