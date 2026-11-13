# ADR-0005 — Thông báo điều hành Admin qua outbox và recipient snapshot

- Status: Implemented in working tree; build/integration verification pending.
- Date / last_verified: 2026-11-12.
- Owner: SPH; reviewer chưa được gán.
- Related: SP-S3-UI-16, `/admin/notifications`.

## Context

FE đã có inbox dùng bốn API notification hiện có. Backend chưa phát thông báo Admin tại create withdrawal/dispute/parking lot. Identity sở hữu role/status; không service nào được JOIN DB Identity. Payment đã có outbox cho payment/refund, với relay gắn cố định topic; thay đổi relay đó làm tăng rủi ro regression tài chính.

## Decision

1. Mọi Admin ACTIVE trong snapshot Identity nhận một bản notification. Read state theo user, không phải trạng thái xử lý hồ sơ chung.
2. Ba trigger: tạo yêu cầu rút tiền thành công, mở tranh chấp thành công, tạo hồ sơ bãi hoặc nộp lại từ REJECTED/CHANGES_REQUESTED sang PENDING_APPROVAL. Submission version của bãi tăng trong transaction có row lock; request sửa tiếp khi đã PENDING bị chặn như trước.
3. Producer ghi `admin_event_outbox` trong cùng DB/transaction với nghiệp vụ. Common-library cung cấp implementation dùng JdbcTemplate/Kafka nhưng được import tường minh ở Identity/Payment/Parking; không có datasource chung và không thay relay payment/refund cũ. Kafka producer String riêng tránh thay serializer của các event cũ.
4. Topic `admin-operations.events` chứa ba loại nghiệp vụ, partition key theo loại và ID đối tượng. EventId giữ nguyên qua retry/replay; không kèm thông tin ngân hàng/chứng cứ tranh chấp.
5. Identity chụp full snapshot ID Admin ACTIVE mỗi 10 giây khi danh sách thay đổi và heartbeat tối đa 60 giây khi không đổi, lưu snapshot version + outbox trong cùng transaction. Notification projection chỉ có user ID và state version/source timestamp. Snapshot topic dùng một partition và key cố định; version cũ bị bỏ qua.
6. Đây là lựa chọn triển khai cụ thể thay cho mô tả eligibility event-per-mutation trong plan: snapshot reconciliation còn bắt được tài khoản seed hoặc thay đổi quản trị hợp lệ ngoài API hiện có. Không cần tạo API nội bộ đọc users hay hardcode ID. Có eventual consistency: thay đổi eligibility không có hiệu lực tức thời ở notification-service; giới hạn snapshot stale mặc định 180 giây, không được quảng cáo là quyền nhận đồng bộ tức thì.
7. Consumer claim processed_events, fan-out và notification save cùng transaction. `UNIQUE(source_event_id,user_id)` là lớp bảo vệ thêm. Lỗi làm rollback cả fan-out; duplicate retry không tạo tin mới. Tối đa 1000 recipient/snapshot mặc định; lớn hơn phải thiết kế delivery journal/batch riêng, không chia transaction ngầm.
8. Consumer mới tách khỏi listener booking/payment/user hiện có. Retry 3 lần, cách nhau 1 giây; invalid JSON/envelope đi DLT, recoverer chỉ coi hoàn tất khi gửi DLT thành công. Không nuốt exception.
9. Outbox relay poll 5 giây, tối đa 20 hàng/lần, retry 5 lần với backoff; FAILED giữ nguyên payload/eventId để operator replay có kiểm soát. Snapshot chưa sẵn sàng/stale/không có recipient -> retry rồi DLT, không đánh dấu event đã xử lý.
10. Giữ API GET list/count, POST/PUT read/read-all và ownership. Sort `createdAt DESC,id DESC`, page default 20, size >100 được sanitizer chung trả về 20. Đọc lặp giữ readAt ban đầu; unknown/foreign ID vẫn success no-op. Trả 401 JSON cho request không được xác thực.

## Consequences

- FE không cần endpoint mới; khi Admin vào trang hoặc bấm Cập nhật, notification mới được lấy qua GET.
- Không có push realtime/broadcast/SMS, không thêm trạng thái xử lý chung, không tự backfill lịch sử cho Admin mới.
- Recipient là snapshot tại thời điểm phân phối; replay tin lỗi có thể gửi cho tập Admin hiện tại. Đây là chính sách hiện tại, không phải snapshot người nhận tại thời điểm nghiệp vụ gốc.
- Hai consumer group và topic DLT phải được giám sát. Quyền produce snapshot chỉ cấp Identity; operations chỉ cấp Payment/Parking; notification cần read và write DLT. Cấu hình broker ACL/TLS thuộc môi trường, chưa được thay đổi trong task.
- Snapshot ID và outbox tăng dữ liệu; cần chính sách retention/archive vận hành. Chưa tự xóa lịch sử outbox hay processed_events.
- Migration additive: Identity V7, Payment V16, Parking V7, Notification V3. Giữ nguyên migrations lịch sử và thay đổi parking-layout có sẵn.

## Verification status

Đã chạy test đỏ tái hiện readAt bị ghi đè trước khi sửa. Sau sửa, biên dịch trực tiếp main source của năm module và test source liên quan bằng JDK21/dependency cache: PASS; JUnit trực tiếp **49 test nghiệp vụ + 9 test API/DB = 58 PASS**. Bao gồm ownership/JWT, readAt, migration, fan-out/rollback/concurrency, outbox rollback/retry/replay, transport validation và eligibility. Kafka/DLT integration test đã viết/biên dịch nhưng chưa chạy broker.

Cập nhật kiểm chứng 2026-11-13: Gradle đã compile cả năm module và tạo bốn bootJar; 395 test PASS, 2 test MySQL FAIL do không kết nối localhost:3306, 2 test Payment SKIPPED. Kafka nhúng fan-out/dedup/DLT PASS. Chưa xác minh MySQL/FE–BE live hoặc CI; xem báo cáo cập nhật. Các kết quả trực tiếp phía trên là lịch sử, không cộng vào tổng test Gradle.
