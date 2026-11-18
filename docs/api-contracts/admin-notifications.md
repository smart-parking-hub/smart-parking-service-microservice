# API và luồng Admin notifications — SP-S3-UI-16

Update 2026-11-17 (owner: SPH): local live verification PASS for newly submitted lot #42 → three ACTIVE Admin inboxes; endpoint/ownership unchanged. Current Admin feed polls every 15 seconds; Merchant/Driver feeds now also refresh every 15 seconds while visible. CreatedAt ASC/DESC are supported with matching id tie-breaker. This supersedes the older fixed-sort/no-polling statements below. See [persistence verification](../implementation/notifications-persistence-20261117.md) for DB ownership, root cause of missing booking/payment notifications, exact tests and selective local replay evidence. No claim of VPS rollout or complete historical backfill.

last_verified: 2026-11-12; owner: SPH. Code triển khai trong working tree, chờ build/test và nghiệm thu runtime. [ADR](../adr/0005-admin-operations-notification-delivery.md).

| Tác vụ | Method/path | Dữ liệu trả về |
| --- | --- | --- |
| List theo người đăng nhập | GET `/api/v1/notifications?page=0&size=20` | `ApiResponse<PageResponse<NotificationDto>>`; `content/currentPage/totalPages/totalElements`. |
| Tổng chưa đọc toàn inbox | GET `/api/v1/notifications/unread-count` | `ApiResponse<Integer>`. |
| Đọc một mục | PUT hoặc POST `/api/v1/notifications/{id}/read` | Success; no-op với ID không thuộc người đăng nhập hoặc không tồn tại. |
| Đọc tất cả của tài khoản | PUT hoặc POST `/api/v1/notifications/read-all` | Success. Không ảnh hưởng inbox người khác. |

JWT/principal là nguồn user ID. Endpoint dùng chung Driver/Merchant/Admin; không đổi sang ADMIN-only. Request không xác thực trả 401 JSON. Sort cố định mới nhất theo createdAt/id giảm dần; 0-based, default 20, size vượt 100 được chuẩn hóa về 20 theo Pageables hiện có. Đọc lặp giữ readAt đầu tiên.

DTO giữ nguyên; cột nội bộ `source_event_id` không đưa ra public API. Trường dùng ở FE: id/title/content/readAt/createdAt. readAt null hoặc không có giá trị là chưa đọc. Không lấy userId từ UI để truy vấn hộp thư người khác.

## Luồng phát sinh tin

| Trigger | Event | Notification type / target |
| --- | --- | --- |
| Tạo yêu cầu rút tiền thành công | WITHDRAWAL_REQUESTED | PAYMENT / WITHDRAWAL |
| Tạo tranh chấp thành công | DISPUTE_OPENED | SYSTEM / DISPUTE |
| Tạo hoặc nộp lại hồ sơ bãi hợp lệ | PARKING_LOT_REVIEW_REQUESTED | SYSTEM / PARKING_LOT |

Không thêm public API tạo broadcast. Chỉ service sở hữu nghiệp vụ phát event qua outbox. Người nhận lấy từ snapshot Admin ACTIVE của Identity; read state riêng. FE hiện tại chỉ đọc feed và đánh dấu đọc, không xử lý quyết định tài chính/kiểm duyệt và không tự polling.

## Chạy nghiệm thu FE–BE thật

1. Hoàn tất build/test Java và áp dụng migrations trên **local/test** theo thứ tự rollout trong runbook; không dùng thông báo seed để thay thế test producer.
2. Khởi động Identity, Payment, Parking, Notification, gateway và hạ tầng MySQL/Kafka/Redis được cấu hình. Chờ recipient snapshot mới sẵn sàng trước khi tạo nghiệp vụ.
3. Chạy FE `smart-parking-app-v2` bằng cấu hình gateway local, ví dụ `VITE_API_BASE_URL=http://127.0.0.1:8088`; giữ app-key/JWT theo cấu hình môi trường hiện có, không đưa secret vào tài liệu.
4. Đăng nhập Admin test thật; từ tài khoản nghiệp vụ khác tạo withdrawal/dispute/hồ sơ bãi thật. Theo dõi eventId ở outbox -> consumer -> notifications. Mở `/admin/notifications`, bấm Cập nhật, đối chiếu title/content/count với response GET.
5. Hai Admin nhận cùng event, nhưng đọc của A không thay readAt của B. Kiểm tra read-all qua nhiều trang, reload, đổi account và JWT sai/hết hạn.
6. Dừng Kafka trong stack test riêng để xác minh PENDING/retry/FAILED/replay; không thực hiện trên VPS/production theo hướng dẫn này.

**Chưa có bằng chứng bước 1–6 đạt trong lần triển khai này.** Test FE với mock trước đây và source BE mới không đủ để tuyên bố FE–BE thật đã hoàn thành.
