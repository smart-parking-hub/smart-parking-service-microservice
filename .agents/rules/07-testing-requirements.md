# Rule 07: Yêu Cầu Kiểm Thử Bắt Buộc (Testing Requirements)

## 1. Nguyên Tắc Sống Còn
**Mã nguồn chưa qua kiểm thử = Mã nguồn chưa hoàn thành.** Không được phép bàn giao một task sửa lỗi hoặc thêm tính năng mà không có bài kiểm thử tự động đi kèm.

## 2. Quy Chuẩn Cho Từng Tình Huống
1. **Sửa Lỗi (Bugfix):**
   - Bước 1: Viết Unit/Integration Test tái hiện chính xác lỗi (Test phải FAIL khi chưa sửa code).
   - Bước 2: Chỉnh sửa mã nguồn tối thiểu để khắc phục lỗi.
   - Bước 3: Chạy lại test (Test phải PASS).
   - Bằng chứng này phải được đưa vào báo cáo nghiệm thu.
2. **Thêm Tính Năng Mới (Feature):**
   - Bắt buộc kiểm thử các trường hợp biên (Boundary Cases):
     - Giá trị null, rỗng, số âm.
     - Xung đột đồng thời (Race Condition khi 2 tài xế cùng chọn 1 vị trí đỗ xe).
     - Hết hạn phiên giữ chỗ (`hold_expired_at`).
3. **Chạy Vòng Lặp Kiểm Chứng:**
   - Trước khi báo cáo hoàn thành, agent phải chạy script kiểm thử tổng thể qua script `scripts/test-all.bat` hoặc lệnh `./gradlew test`.
