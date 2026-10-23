# Rule 08: Đồng Bộ Hóa Tài Liệu (Documentation Sync)

## 1. Triết Lý
Tài liệu không phải đồ trang trí; tài liệu là **Bản đồ sống (Living Documentation)** của hệ thống giúp con người và các AI Agent khác hiểu rõ hiện trạng mà không cần phải đọc dịch ngược từng dòng bytecode.

## 2. Quy Định Đồng Bộ Bắt Buộc
Khi có bất kỳ thay đổi nào thuộc các trường hợp sau, Agent **bắt buộc phải cập nhật tệp tài liệu tương ứng**:
1. **Thay đổi API Endpoint / Request DTO / Response DTO:**
   - Cập nhật tài liệu API Contract trong `docs/api-contracts/` hoặc Swagger/OpenAPI.
2. **Thay đổi Cấu Trúc Database / Migration Flyway:**
   - Cập nhật `docs/data-ownership.md` và sơ đồ thực thể tương ứng.
3. **Thêm hoặc Sửa Kafka Event / Topic:**
   - Cập nhật `docs/event-catalog.md` (Topic, Payload schema, Producer, Consumer).
4. **Quyết Định Kiến Trúc Quan Trọng (Đổi DB, Thêm Broker, Tách Service):**
   - Viết một bản ghi Architecture Decision Record (ADR) mới trong `docs/adr/` theo mẫu `templates/adr-template.md`.
