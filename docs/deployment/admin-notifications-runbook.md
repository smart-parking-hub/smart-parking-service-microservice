# Vận hành Admin notifications — local/test và rollout có kiểm soát

- last_verified: 2026-11-13; owner: SPH.
- Trạng thái: Code/migration đã viết; Gradle compile/bootJar và Kafka nhúng đã kiểm chứng; MySQL/FE–BE live chưa nghiệm thu. Không dùng tài liệu này như xác nhận được phép deploy VPS.
- API: [admin-notifications](../api-contracts/admin-notifications.md); quyết định: [ADR-0005](../adr/0005-admin-operations-notification-delivery.md).

## Cấu hình và thứ tự

1. Chạy JDK 21, Gradle test/build và kiểm tra full migration chain trên DB test. Migration mới: Identity V7, Payment V16, Parking V7 (sau V6 parking-layout đang có), Notification V3. Giữ mọi migration lịch sử.
2. Provision topic và ACL trước khi bật producer. KafkaAdmin code khai báo operations 3 partitions, snapshots 1 partition, và DLT tương ứng; replicas mặc định 1 phù hợp local, cấu hình theo broker thực tế khi rollout. Đây không thay thế cấu hình bảo mật broker.
3. Triển khai schema/consumer trước và chờ Identity snapshot mới tới notification-service. Chỉ sau đó kích hoạt producer nghiệp vụ. Không tạo dữ liệu trên production bằng fixture test.
4. `APP_ADMIN_OPERATIONS_ENABLED` là công tắc relay/consumer/snapshot scheduler (mặc định true trong code). Đặt false khi cần tạm dừng phần mới. Nghiệp vụ vẫn ghi outbox để không mất sự kiện trong lúc relay dừng; đây không phải công tắc bỏ qua việc ghi outbox hay bỏ qua migration.
5. Quyền Kafka: Identity produce `admin-recipient-snapshots.events`; Payment/Parking produce `admin-operations.events`; Notification consume cả hai và produce DLT. Không cho client FE publish vào broker.

Các property khác: `app.admin-operations.relay-delay-ms=5000`, `recipient-scan-ms=10000`, `recipient-max-age-seconds=180`, `max-recipients=1000`, `partitions=3`, `replicas=1`. Dùng binding env/config của Spring; không ghi secret vào tài liệu/source.

## Quan sát và xử lý lỗi

Outbox nằm **riêng trong DB từng producer**, không truy vấn JOIN giữa service:

```sql
SELECT status, COUNT(*) FROM admin_event_outbox GROUP BY status;
SELECT event_id, topic_name, attempts, created_at, next_attempt_at
FROM admin_event_outbox WHERE status = 'FAILED' ORDER BY created_at;
```

Snapshot state nằm trong DB Notification:

```sql
SELECT snapshot_version, source_created_at FROM admin_recipient_state WHERE id = 1;
SELECT COUNT(*) FROM admin_notification_recipients;
```

Kiểm tra Kafka consumer lag và hai topic `.DLT` bằng công cụ quản trị đã được xác thực cho môi trường. Source timestamp quá 180 giây, snapshot chưa có hoặc zero recipient đều khiến operation retry rồi đi DLT. Lỗi fan-out rollback cả processed_events và notification, không đánh dấu hoàn tất nửa chừng.

### Replay outbox FAILED

- Người vận hành có thẩm quyền xác định đúng producer DB/eventId, khắc phục nguyên nhân, kiểm tra payload/target và tác động gửi lại trước khi replay.
- Reset **một event đã chọn**, giữ eventId, topic, key và payload. Ví dụ prepared SQL (bind `:event_id` bằng công cụ DB; không chạy toàn bảng):

```sql
UPDATE admin_event_outbox
SET status = 'PENDING', attempts = 0, next_attempt_at = CURRENT_TIMESTAMP
WHERE event_id = :event_id AND status = 'FAILED';
```

- Xác minh affected row = 1, relay chuyển PUBLISHED và Notification không tạo duplicate. Không reset processed_events hoặc đổi eventId để “sửa” replay.

### Replay DLT

- Dùng công cụ Kafka được cấp quyền đọc đúng record DLT theo topic/partition/offset, kiểm tra nguyên nhân và khắc phục trước.
- Gửi lại **nguyên JSON payload và partition key gốc** vào topic nguồn (`admin-operations.events` hoặc `admin-recipient-snapshots.events`); không sửa eventId, không replay hàng loạt toàn DLT. Payload invalid cần sửa đúng nghiệp vụ/hợp đồng và review riêng; không tự tạo nội dung thiếu.
- Kiểm tra processed marker + số notification theo eventId sau replay. Recipient áp dụng tại lúc phân phối; nếu danh sách Admin thay đổi giữa lần đầu và replay thì người nhận có thể khác.
- Đây là quy trình vận hành có người thực hiện, chưa có API/nút replay công khai trong sản phẩm.

## Kiểm chứng FE–BE

FE giữ endpoint cũ qua gateway; đăng nhập test thật rồi tạo nghiệp vụ thật. Đối chiếu eventId từ outbox tới `notifications.source_event_id`, GET list/count và giao diện `/admin/notifications` sau bấm Cập nhật. Hai Admin phải đọc độc lập. Kiểm tra lỗi mạng/401, lịch sử >100 mục và reload. Test mock FE trước đây không thay thế được luồng này.

## Rollback

Tắt riêng relay/consumer/snapshot mới, quay về image tương thích trước theo quy trình triển khai đã duyệt; giữ các schema additive, outbox và DLT để sửa/replay sau. Không DROP/TRUNCATE, không reset consumer offset cả group hoặc xóa marker hàng loạt. Không đổi listener payment/booking/user cũ khi dừng luồng mới. Thay đổi nguồn ParkingLot thêm field phụ thuộc V7, nên image/schema phải đi đúng thứ tự.

## Giới hạn

- Eligibility là eventual snapshot, không phải tra cứu role/status đồng bộ mỗi event. Chưa có backfill lịch sử cho Admin mới.
- Chưa có cleanup tự động cho published outbox/processed_events. Cần chốt retention/archive theo môi trường trước vận hành dài hạn.
- Test H2 không chứng minh MySQL locking/migration, test Kafka nhúng không chứng minh cấu hình ACL/TLS/cluster thật. Phải ghi riêng kết quả các kiểm chứng này.
