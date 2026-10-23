# Skill: Microservice Change

## Trước khi sửa
1. Xác định service sở hữu nghiệp vụ và dữ liệu.
2. Tìm API/event contract, caller, consumer và downstream.
3. Kiểm tra version compatibility và deploy order.
4. Đánh giá backward compatibility.
5. Đánh giá transaction, retry, timeout, idempotency, duplicate event và observability.
6. Xác định test và rollback.

## Khi thay API
- Không đổi field/status semantics âm thầm.
- Ưu tiên additive change; đánh dấu deprecation nếu cần.
- Cập nhật OpenAPI và contract tests.
- Tìm toàn bộ caller/consumer.

## Khi thay event
- Schema/version rõ.
- Xác định producer, consumer, key/partition/order assumptions.
- Xử lý duplicate và poison message.
- Đánh giá Outbox và retry/DLQ theo broker thực tế.
- Không xóa event field trước khi consumer tương thích.

## Khi thay database
- Migration tương thích rollout.
- Không giả định deploy đồng thời tất cả service.
- Xem index/lock/long-running migration.
- Có kế hoạch backup/restore cho thay đổi rủi ro.

## Kết thúc
Báo impact map, contract changes, tests, deploy order, rollback limitations và monitoring signals.
