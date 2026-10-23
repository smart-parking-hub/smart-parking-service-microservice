# 40 — Java/Spring Quality Guardrails

- Tuân thủ phiên bản Java, Spring Boot, Spring Cloud và Gradle đang có trong repository; không tự nâng version.
- Đọc `build.gradle`/`settings.gradle`, wrapper, dependency constraints và pipeline trước khi thêm dependency.
- Controller xử lý HTTP mapping/validation, không chứa business workflow dài.
- Business rules đặt tại application/domain layer phù hợp với cấu trúc hiện tại.
- DTO/API contract tách khỏi JPA entity khi cần bảo vệ contract và tránh lộ dữ liệu.
- Không expose entity trực tiếp chỉ để tiết kiệm mapping nếu gây rò rỉ field hoặc coupling.
- Xem xét transaction boundary và lazy loading trước khi thay repository/service.
- Tránh N+1 bằng evidence từ query/test/profiler; không thêm fetch join bừa bãi gây duplicate rows/pagination lỗi.
- Không nuốt exception hoặc trả HTTP 200 cho lỗi.
- Validation ở server là bắt buộc; frontend validation không thay thế.
- Với HTTP client: timeout bắt buộc được cân nhắc; retry chỉ cho lỗi phù hợp và operation an toàn.
- Không log password, token, PII nhạy cảm hoặc toàn bộ payload không cần thiết.
- Test theo rủi ro: unit, slice, integration, contract, concurrency/security khi cần.
- Giữ style và kiến trúc hiện có nếu không có lý do được duyệt để thay đổi.
