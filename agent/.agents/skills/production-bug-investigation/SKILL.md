
# Skill: Production Bug Investigation

## Goal
Tìm nguyên nhân có bằng chứng, tạo thay đổi nhỏ, tránh làm tình hình xấu hơn.

## Phase 0 — Safety
- Xác định môi trường và impact.
- Không truy cập production hoặc chạy lệnh có side effect nếu chưa được phép.
- Dùng dữ liệu đã ẩn danh.

## Phase 1 — Reproduce and scope
- Ghi symptom, thời điểm, endpoint/job, expected vs actual.
- Tìm request ID/trace ID; không yêu cầu chia sẻ token/secret.
- Xác định có tái hiện ở local/test/staging không.

## Phase 2 — Trace code
- Controller/consumer/scheduler → validation/security → application/domain → repository/external client → response/event.
- Đọc call sites và tests, không chỉ file báo lỗi.
- Kiểm tra config/version/migration liên quan.

## Phase 3 — Hypotheses
Mỗi giả thuyết cần: evidence ủng hộ, evidence phản bác, cách kiểm tra ít rủi ro.

## Phase 4 — Fix
- Tạo test tái hiện.
- Thay đổi nhỏ nhất.
- Không trộn refactor.
- Cập nhật tài liệu nếu flow hoặc contract đổi.

## Phase 5 — Verify
- Test liên quan, diff review, security review.
- Nếu cần deploy: tạo runbook riêng, nêu phê duyệt và rollback; không tự deploy.

## Final report
Symptom / Impact / Root cause confidence / Evidence / Fix / Files changed / Tests / Rollback / Residual risks.
