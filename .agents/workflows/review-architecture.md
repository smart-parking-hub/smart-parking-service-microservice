# Workflow: /review-architecture - Quy Trình Rà Soát Kiến Trúc

## Mục Đích
Đánh giá độ tuân thủ kiến trúc Microservices, Clean Architecture, và tính bảo mật của mã nguồn trước khi hợp nhất (merge) hoặc release.

## Các Tiêu Chí Kiểm Tra (Checklist)

1. **Ranh Giới Dữ Liệu (Data Boundary):**
   - Không có class nào trong Service A tham chiếu đến Entity JPA của Service B.
   - Không có repository nào query vào bảng không thuộc quyền sở hữu của service.
2. **Quản Lý Giao Tiếp (Communication):**
   - Mọi Feign Client đều có timeout và fallback circuit breaker.
   - Các nghiệp vụ bất đồng bộ đều có Transactional Outbox hoặc Idempotent Consumer.
3. **Bảo Mật (Security):**
   - Không có token/password/API keys nào bị hardcode.
   - Các API nhạy cảm đều kiểm tra quyền hạn (`@PreAuthorize` hoặc filter).
4. **Hiệu Năng (Performance):**
   - Không có lỗi N+1 query trong JPA (kiểm tra `@EntityGraph` hoặc `JOIN FETCH`).
   - Các câu query tìm kiếm nhiều bản ghi đều có phân trang (`Pageable`).
