# 60 — Tests, Diff and Completion Criteria

## Trước khi kết thúc
- `git status --short`
- `git diff --check`
- `git diff --stat`
- đọc toàn bộ diff để tìm thay đổi ngoài phạm vi
- xác minh không có secret, log debug, file build, generated file không mong muốn
- chạy test phù hợp với thay đổi.

## Test reporting
Báo từng lệnh đã chạy, kết quả và giới hạn. Nếu không chạy, ghi rõ tại sao; không viết “all tests passed” nếu chỉ chạy compile hoặc không chạy gì.

## Mức test tối thiểu theo thay đổi
- Business rule: unit test happy path + boundary + failure.
- API: validation, status code, authorization, error response.
- Repository/query: integration test với DB tương thích.
- Concurrency/stock/payment: test race/idempotency khi phù hợp.
- Event: contract, duplicate, retry, failure handling khi phù hợp.
- Config/deploy: syntax check và smoke test an toàn trước khi reload/deploy.

## Hoàn tất khi
- Acceptance criteria đã được kiểm tra.
- Root cause và thay đổi được giải thích.
- Diff nằm trong phạm vi.
- Test report trung thực.
- Rủi ro chưa giải quyết được nêu rõ.
- Không tự triển khai production nếu chưa được cho phép.
