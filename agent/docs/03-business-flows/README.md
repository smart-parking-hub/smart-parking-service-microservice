# Business Flows

Mỗi nghiệp vụ quan trọng có một file riêng, ví dụ:
- `login-refresh-token.md`
- `create-order.md`
- `reserve-inventory.md`
- `payment-success-failure.md`
- `cancel-order-refund.md`

## Bắt buộc cho mỗi flow
1. Mục đích và actor.
2. Trigger/API/event.
3. Preconditions và authorization.
4. Happy path từng bước.
5. Alternative/failure paths.
6. State transitions.
7. Service nào đọc/ghi dữ liệu nào.
8. Transaction boundary.
9. Timeout/retry/idempotency.
10. Events, producer/consumer và ordering assumptions.
11. Source paths/symbols + tests.
12. Logs/metrics/traces để debug.
13. Cách tái hiện lỗi an toàn.
14. Confidence và ngày xác minh.

Không viết nghiệp vụ dựa vào phỏng đoán tên endpoint. Đọc source và test trước.
