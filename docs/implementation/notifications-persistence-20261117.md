# Kiểm chứng lưu trữ thông báo Admin / Merchant / Driver

last_verified: 2026-11-17; owner: SPH. Phạm vi: source hiện tại và Docker Desktop local; chưa triển khai VPS.

## Kết luận

Nhận xét “thao tác mới không lưu DB” không đúng nếu dùng để kết luận chung. Nghiệp vụ và thông báo là hai bước lưu riêng. Đã xác minh lỗi nhận sự kiện khiến một số thông báo không được tạo mặc dù nghiệp vụ đã có trong DB.

- Log local trước sửa: `BookingCreatedEvent` / `PaymentCompletedEvent` không chuyển được thành `String` để gọi listener. JsonDeserializer giải mã theo type header trong khi listener đọc JSON text.
- Booking/Payment/User listener còn bắt exception rồi trả về, làm lỗi không tới cơ chế retry. Nay exception được truyền tới error handler và transaction rollback.
- Sự kiện tạo booking thiếu `merchantId` dù consumer có nhánh gửi merchant. Nay producer lấy trường này từ booking đã lưu; hợp đồng thêm trường tùy chọn, không đổi endpoint hoặc schema.
- Luồng merchant tạo bãi → admin hiện hoạt động: không cần tạo thông báo giả trên FE. Trước test, outbox Parking local rỗng; riêng điều đó không chứng minh các bãi lịch sử không được lưu.

## Lưu ở đâu

| Dữ liệu | Service / DB local đang dùng | Bảng |
| --- | --- | --- |
| Hồ sơ bãi và trạng thái kiểm duyệt | Parking / `smart_parking_lot_p0_20261106` | `parking_lots` |
| Sự kiện yêu cầu admin duyệt bãi | Parking / cùng DB | `admin_event_outbox` |
| Booking / sự kiện booking | Booking / `smart_parking_booking_p0_20261106` | `bookings`, `outbox_events` |
| Thanh toán / sự kiện thanh toán | Payment / `smart_parking_payment_p0_20261106` | `payments`, `outbox_events` |
| Thông báo cả ba role / trạng thái đã đọc | Notification / `smart_parking_notification_db` | `notifications`, cột `user_id`, `read_at` |
| Chống xử lý lặp / người nhận admin | Notification / cùng DB | `processed_events`, `admin_recipient_state`, `admin_notification_recipients` |

Frontend lấy `GET /api/v1/notifications` theo user trong JWT. Đọc tin dùng POST/PUT `/{id}/read` hoặc `/read-all`, cập nhật DB. Cache TanStack Query chỉ phục vụ hiển thị. Bộ lọc, trang hiện tại và toast là UI state, không thay thế bản ghi DB. Audit đọc riêng từng DB; service không JOIN hoặc truy cập DB của service khác.

## Thay đổi và rủi ro

Tái sử dụng factory String JSON và chính sách DLT sẵn có cho ba listener booking/payment/user; giữ group ID và topic gốc. Thêm topic `.DLT` tương ứng. JSON lỗi đi DLT; lỗi lưu được thử lại ba lần cách một giây. Cần giữ quyền produce DLT khi triển khai môi trường khác. Chưa kiểm chứng outage broker/DLT thật trong lần này.

FE: merchant/driver tải lại danh sách mỗi 15 giây khi tab hiển thị, cập nhật khi focus theo chính sách stale của Query; đọc trên admin đồng thời cập nhật chuông. Không thay đổi thiết kế màn hình, phân quyền hay nghiệp vụ đặt chỗ/thanh toán.

## Kiểm thử

| Lệnh / môi trường | Kết quả |
| --- | --- |
| `gradlew.bat :notification-service:test --tests '*Business*' --offline` trước sửa | 2 FAIL, tái hiện lỗi giao nhận và lỗi bị nuốt |
| `gradlew.bat :notification-service:test --offline --console=plain` sau sửa | 62 PASS |
| `gradlew.bat :booking-service:test :booking-service:classes --offline --console=plain` | 237 PASS, gồm kiểm tra merchantId trong outbox |
| `gradlew.bat :common-library:test :parking-lot-service:compileJava :payment-service:compileJava :identity-service:compileJava --offline` | 8 PASS; các consumer biên dịch thành công |
| FE: notifications + AdminNotificationsPage + WithdrawalNotificationSorting | 40 PASS; hai test refresh từng FAIL trước sửa |
| FE typecheck / lint / production build | PASS |
| Playwright `e2e/admin-notifications.spec.ts` | 13 PASS, gồm mobile, keyboard, đọc tin, phân trang, lỗi API, role guards, merchant portal |

Test Kafka nhúng gửi typed event có type header lẫn JSON thô, kiểm tra persisted recipients và dedup. Test listener xác minh exception lưu phải được ném lên. Test API/repository hiện có xác minh ownership và readAt bền vững. Test 401 E2E cũ đã được cập nhật để tính riêng một request list và hai request count của chuông/hộp thư; không bỏ assertion lỗi.

## Bằng chứng live local

- Tạo hồ sơ test `CODEX notification verification ...` qua Merchant API: bãi **42**, `PENDING_APPROVAL`. Admin **1/2/3** nhận lần lượt notification **260/261/262**, mỗi người đúng một bản. Fixture này còn được giữ để truy vết; không xóa dữ liệu người dùng.
- Chọn đúng một retained event booking đã commit nhưng chưa xử lý, booking **251**, eventId `bca9b141-a9fc-441a-9a81-80bad5f45898`. Sau replay nguyên payload, Driver **14** nhận tin **263**, Merchant **59** nhận tin **264**.
- Một retained payment event cho booking **260**, eventId `c9a1c427-35bb-4ee2-9b4e-c0f314a60986`: Driver **14** nhận tin **265**.
- Cả ba tin trên có trong MySQL, GET trả lại đúng tài khoản, POST đọc lưu `read_at`, GET tiếp theo giữ trạng thái. Gửi lại cùng eventId không tăng số tin. Không phát sinh giao dịch tiền mới từ kiểm chứng replay này.
- Payload không khớp role được bỏ qua khi chọn; không thay role hoặc chủ sở hữu để ép test thành công. Không replay toàn bộ lịch sử.

Artifacts tại `D:/DuAnOjt/audit-reports/notifications-20261117/`: `local-before.json`, `admin-live.json`, `business-live.json`, logs đỏ/xanh, `overlay-manifest.json`, `local-runtime.json` và script tái kiểm chứng.

## Runtime và rollback

Local notification/booking đều UP. Để giữ các bản vá local có trước task, chỉ overlay các class đã sửa vào đúng JAR đang chạy; kiểm chứng mọi entry khác, đặc biệt migrations/config, giữ nguyên nội dung byte. Không thêm/chạy migration mới. Giữ nguyên environment, mạng, mount và port; image gốc và JAR gốc được giữ. Manifest ghi SHA-256 và danh sách class đổi. Source trong repository là bản bàn giao chính; overlay là cách áp dụng riêng trên local đang có lineage.

Nếu cần khôi phục runtime local, chạy `python D:/DuAnOjt/audit-reports/notifications-20261117/update-local.py --rollback`; script chỉ chấp nhận đúng hai image vừa cập nhật, khôi phục image gốc với cấu hình hiện có và kiểm tra health. Nó không xóa notification đã tạo hoặc đổi dữ liệu nghiệp vụ. Chưa thực thi rollback vì hai service khỏe.

Không kết luận mọi thao tác từng test đều được lưu từ vài screenshot hoặc kiểm chứng mẫu. Muốn kiểm tra thao tác cũ phải có ID/thời điểm, đối chiếu bản ghi nghiệp vụ → outbox → processed marker → notification. Sự kiện cũ không còn payload hoặc đã bị đánh dấu sai cần điều tra riêng; không tự tạo lịch sử thay thế.

## Cách diễn đạt phản hồi chính xác hơn

“Dữ liệu nghiệp vụ có lưu vào DB, nhưng một số luồng tạo và cập nhật thông báo bị lỗi nên màn hình Admin/Merchant/Driver chưa phản ánh đầy đủ thao tác mới. Cần kiểm tra riêng từng bước: lưu nghiệp vụ, outbox, xử lý sự kiện, lưu thông báo và tải lại giao diện. Việc không thấy thông báo chưa đủ để kết luận thao tác không được lưu.”
