# Workflow: /db-migration-check - Quy Trình Kiểm Thử Migration Database

## Mục Đích
Bảo đảm mọi file migration Flyway (`V...__*.sql`) đều tương thích ngược, không làm khóa bảng lâu (lock table), và có thể chạy an toàn trên production.

## Các Tiêu Chí Kiểm Tra (SOPs)

1. **Quy Tắc Đặt Tên Tệp:**
   - Định dạng chuẩn: `V<Version>__<Mô_tả_ngắn_gọn>.sql` (Ví dụ: `V2__add_vehicle_type_to_pricing.sql`).
   - Tuyệt đối không sửa đổi nội dung của file migration đã từng được áp dụng trước đó.

2. **Tính Tương Thích Ngược (Backward Compatibility):**
   - Khi thêm cột mới: Bắt buộc có giá trị mặc định (`DEFAULT`) hoặc cho phép `NULL` để code cũ vẫn ghi được dữ liệu trong giai đoạn rolling update.
   - Khi xóa/đổi tên cột: Phải chia làm 2 giai đoạn release (Expand and Contract pattern).

3. **Chạy Thử Nghiệm Kiểm Chứng (Dry Run):**
   - Chạy script kiểm thử: `scripts/db-migrate-dry-run.bat`.
   - Xác nhận Flyway validate thành công và không xung đột checksum.
