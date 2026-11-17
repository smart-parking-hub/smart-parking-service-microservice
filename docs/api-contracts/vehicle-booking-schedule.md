# Không đặt trùng lịch cho cùng phương tiện

Ngày kiểm chứng: 2026-11-18. Phạm vi: booking-service, `POST /api/v1/bookings`.

## Hành vi

- Một `vehicleId` không được có hai booking còn hiệu lực trùng khoảng thời gian, ở cùng bãi hoặc khác bãi. Kiểm tra không phụ thuộc `driverId` của booking cũ.
- Khoảng đặt chỗ là `[expectedCheckInTime, expectedCheckOutTime)`: lượt mới bắt đầu đúng lúc lượt trước kết thúc được phép.
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
| Lượt trước kết thúc 12:00, lượt mới bắt đầu 12:00 | Cho phép |
| Booking cũ đã hoàn tất/đánh giá/hủy/hết hạn | Cho phép |
| Xe khác cùng khung giờ | Cho phép theo chỗ trống của bãi |
| Xe đang đỗ chưa quá giờ, đặt lượt sau giờ ra dự kiến | Cho phép |
| Gửi lại cùng requestId hợp lệ | Trả booking cũ, không giữ chỗ lần nữa |
| Bốn yêu cầu đồng thời cùng xe, tắt giới hạn giữ chỗ | Một booking, ba xung đột |

`VehicleBookingConflictIntegrationTest`: 24 tình huống, H2 MySQL mode. Trước sửa: 15 lỗi tái hiện; sau sửa: đạt. Khi tách PR độc lập từ main, toàn bộ `:booking-service:test`: 200 tình huống, 196 đạt, 0 lỗi, 4 bỏ qua theo cấu hình môi trường. Kết quả 211 ca của lượt triển khai trước còn gồm thay đổi checkout được tách sang PR riêng. Chưa chạy các ca xung đột lịch mới trên MySQL thật.

## Rủi ro và khôi phục

Thay đổi chặn các yêu cầu mới; không sửa booking lịch sử hay tiền ví. Khóa theo phương tiện dùng chung thứ tự với khóa hạn mức hiện tại. Nếu cần rollback, bỏ phần kiểm tra mới trong `BookingServiceImpl` và `HoldPolicyStore.hasScheduleConflict`; không có migration hoặc dữ liệu cần khôi phục. Chưa triển khai/restart dịch vụ đang chạy.
