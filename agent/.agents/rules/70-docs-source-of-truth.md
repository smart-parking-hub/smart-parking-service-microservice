# 70 — Documentation as a Verified Map

- Trước khi sửa code, tìm tài liệu liên quan trong `docs/`.
- Sau khi sửa kiến trúc, API, event, data ownership, config hoặc runbook, cập nhật tài liệu liên quan trong cùng thay đổi nếu cần.
- Tài liệu phải trỏ tới source path/symbol và ngày xác minh.
- Không tạo sơ đồ giả định rồi gọi là AS-IS.
- Phân biệt AS-IS (đang có), TO-BE (mục tiêu), DECISION (đã quyết định) và TODO.
- Nếu chưa kiểm tra runtime, không tuyên bố port/container/service đang chạy.
- Không nhân bản cùng một sự thật trong nhiều file; chọn một source of truth và link sang nó.
