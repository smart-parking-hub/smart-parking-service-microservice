# Kế Hoạch Thực Thi Thay Đổi (Implementation Change Plan)

## 1. Mục Tiêu Thay Đổi
- **Mục tiêu:** Tóm tắt ngắn gọn tính năng hoặc lỗi cần xử lý.
- **Microservice liên quan:** [Tên service]
- **Người đề xuất:** @backend_engineer / @devops / @qa

---

## 2. Phạm Vi Tác Động (Scope of Changes)
- **Tệp tin sẽ tạo mới:**
  - `path/to/NewFile.java`
- **Tệp tin sẽ chỉnh sửa:**
  - `path/to/ExistingFile.java` (Dòng X - Y)
- **Tác động Database Schema (nếu có):**
  - Tệp Flyway: `db/migration/V...__description.sql`

---

## 3. Đánh Giá Rủi Ro Tiềm Ẩn (Risk Assessment)
| Rủi ro | Mức độ | Biện pháp giảm thiểu |
| :--- | :--- | :--- |
| Ảnh hưởng hiệu năng / Deadlock | Trung bình | Bổ sung Redis timeout và index DB |
| Mất tính tương thích ngược API | Thấp | Không sửa đổi các field đã có của DTO cũ |

---

## 4. Kế Hoạch Kiểm Thử (Verification Plan)
- **Unit Test:** Liệt kê các test cases sẽ viết.
- **Integration Test:** Mô phỏng kịch bản gọi endpoint thực tế.
- **Script kiểm chứng tự động:** Chạy `scripts/test-all.bat` hoặc `scripts/verify-all.bat`.

---

## 5. Kế Hoạch Khôi Phục Khẩn Cấp (Rollback Plan)
- **Nếu code lỗi:** Revert commit hoặc deploy lại Docker image tag trước đó.
- **Nếu DB migration lỗi:** Script rollback SQL tương ứng.

---

> [!IMPORTANT]
> **Khóa Thao Tác (Execution Lock):** AI Agent dừng lại tại đây và chỉ bắt đầu chỉnh sửa khi nhận được xác nhận *"Đồng ý"* hoặc *"Làm đi"* từ người dùng!
