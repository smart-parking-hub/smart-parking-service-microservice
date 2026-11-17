# Review trước/sau — feat(payment): Coordinate cash and PayOS gate settlement

Ngày review/publish: 2026-11-17 (Asia/Bangkok). Các tên fixture và tài liệu 20261118 được giữ nguyên từ lượt triển khai nguồn.
Owner: SmartParkingHub. Phạm vi: `feat/gate-final-payment`; base `d004fa4f78cf`. Chưa deploy.

## Trước và sau

| Trước | Sau |
|---|---|
| Số tiền mặt thiếu/sai vẫn có thể xác nhận | Bắt buộc số tiền dương và khớp khoản nợ server đã chốt |
| Cash và PayOS chưa có quyết định bền vững dùng chung | Khóa FINAL_PAYMENT, receipt PENDING trước callback, recovery và quyết định phương thức trên Booking |
| Replay final payment chưa phân biệt payment đã được chấp nhận | Lưu payment reference; chỉ replay đúng quyết định và không lặp release/outbox |
| PayOS đến sau quyết định cash có thể được xử lý như thanh toán bình thường | Chuyển REQUIRES_REVIEW, không ghi nhận success/escrow lần nữa |

## Review trước push

- Tách đúng phạm vi bằng worktree từ origin/main đã fetch; không gộp thay đổi tính năng khác.
- Đối chiếu contract, ownership, trạng thái, xử lý lỗi và test; không stage secrets, dependency/build output hoặc config kiểm thử local.
- `git diff --check`: đạt. Kết quả kiểm tra sau push (SHA, file scope, CI) được cập nhật trong mô tả PR và báo cáo publish.

## Kiểm chứng

- booking-service: **182 đạt, 5 bỏ qua theo môi trường, 0 lỗi** (187 ca trong JUnit).
- payment-service: **212 đạt, 5 bỏ qua theo môi trường, 0 lỗi** (217 ca trong JUnit).
- JDK 21; Gradle offline, no-daemon; Kafka listener tắt và broker test trỏ 127.0.0.1:1. Không dùng DB dự án hoặc broker thật.
- Vòng `gradlew test --continue` toàn dự án không đạt: hai IdentitySchemaMigrationTest không kết nối được MySQL localhost:3306; lượt chạy cũng bị ngắt trước khi kết thúc. Source Identity không đổi trong PR. Không tuyên bố toàn backend fresh PASS. Vòng Booking/Payment riêng ở trên đã hoàn tất.
- Bằng chứng MySQL 8.4 của lượt triển khai trước: 12/12 ca schema/concurrency/recovery đạt trong audit-reports/checkout-a-20261118; đã đọc lại, không chạy lại MySQL trong lượt publish này. Docker Desktop local hiện không chạy. CI có MySQL service riêng.

## Giới hạn và rollout

Có migration cộng thêm Booking V11 và Payment V20. Thứ tự rollout: schema → Booking → Payment coordinator/recovery → FE. Client cũ thiếu amount bị từ chối. Giữ recovery và receipt khi rollback; không xóa sổ cái. HTTP releaseSlot và commit Booking vẫn không phải transaction chung. Không đổi chính sách giá/hoàn cọc checkout; cash không giả thành PayOS receipt hoặc tiền vào ví chủ bãi.

Không merge/deploy hoặc chuyển tiền thật trong lượt publish này. Working tree nguồn được giữ nguyên; bản sửa review của vé nằm trên nhánh PR riêng.
