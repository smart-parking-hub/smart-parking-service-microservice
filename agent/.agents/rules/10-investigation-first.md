# 10 — Investigation Before Fix

Không sửa theo triệu chứng trước khi tìm nguyên nhân có bằng chứng.

## Quy trình
1. Viết lại lỗi thành một câu có thể kiểm chứng.
2. Phân loại: build, startup, API, business logic, data, security, performance, infrastructure, integration.
3. Xác định phạm vi: một request, một service, nhiều service, một môi trường hay toàn hệ thống.
4. Thu thập bằng chứng không phá hủy: source, config, tests, logs, metrics, traces, git history.
5. Dựng flow thực tế: entry point → validation/auth → application/business → persistence/external call → response/event.
6. Nêu tối đa 3 giả thuyết, xếp theo bằng chứng và xác suất; với mỗi giả thuyết ghi cách kiểm tra an toàn.
7. Xác minh giả thuyết trước khi chỉnh code.
8. Viết test tái hiện lỗi nếu khả thi.
9. Sửa nhỏ nhất.
10. Chạy test và kiểm tra diff.

## Cấm
- Không đổi nhiều thứ một lúc rồi suy đoán cái nào sửa được lỗi.
- Không chữa bằng cách tắt security/validation/transaction hoặc nuốt exception.
- Không thêm retry nếu chưa xác định tính idempotent.
- Không tăng timeout vô hạn để che nghẽn.
- Không chỉ dựa vào tên class/tên file; phải đọc code và call sites.
- Không khẳng định nguyên nhân gốc nếu mới có tương quan.

## Báo cáo điều tra
- Symptom:
- Impact:
- Evidence:
- Request/data flow:
- Root cause: VERIFIED / INFERRED / UNKNOWN
- Alternatives rejected:
- Minimal fix:
- Tests:
- Risks:
- Next safe step:
