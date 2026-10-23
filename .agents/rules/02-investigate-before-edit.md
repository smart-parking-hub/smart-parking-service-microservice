---
trigger: model_decision
---

# Rule 02: Điều Tra Có Bằng Chứng Trước Khi Sửa (Investigate Before Edit)

## 1. Triết Lý
**Không có bằng chứng = Không có quyền sửa code.** Mọi hành vi sửa code dựa trên suy đoán hoặc phỏng đoán mò mẫm đều bị coi là vi phạm nghiêm trọng.

## 2. Quy Tắc Bắt Buộc Khi Điều Tra Lỗi
Trước khi mở bất kỳ tệp tin nào để chỉnh sửa logic, Agent phải:
1. **Tìm bằng chứng cụ thể:**
   - Trích xuất Trace ID / Correlation ID từ log hoặc payload lỗi.
   - Định vị chính xác tệp tin và dòng code phát sinh ngoại lệ (Stack Trace).
   - Định dạng trích dẫn chuẩn: `[TênFile](file:///đường_dẫn#dòng)` kèm đoạn trích code ngắn gọn.
2. **Đối chiếu với Source Code Đang Chạy:**
   - Không được giả định logic hoạt động như thế nào qua tài liệu cũ; phải đọc trực tiếp tệp mã nguồn hiện hữu trong repository.
3. **Phân tích Nguyên nhân Gốc rễ (Root Cause Analysis):**
   - Giải thích cơ chế vì sao lỗi xảy ra (do dữ liệu null, do race condition, do hết hạn JWT, hay do lỗi định dạng chuỗi).
4. **Sửa đúng phạm vi (Minimal Scope):**
   - Chỉ chỉnh sửa đúng vị trí phát sinh lỗi. Tuyệt đối không tiện tay format lại toàn bộ file, không sửa các hàm không liên quan gây nhiễu loạn git diff.
