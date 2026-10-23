---
name: repo-discovery
description: >-
  Use this skill when exploring, mapping, or analyzing the SmartParkingHub repository codebase
  without making any code modifications. Provides a structured, read-only approach to identify modules,
  services, dependencies, database schemas, and endpoints.
---

# Repository Discovery Skill (Khám Phá Mã Nguồn Chỉ Đọc)

Kỹ năng này hướng dẫn Agent cách rà soát toàn diện repository để lập bản đồ kiến trúc AS-IS mà **không chỉnh sửa bất kỳ dòng code nào**.

## Các Bước Thực Hiện

### Bước 1: Quét Bản Đồ Module & Cấu Hình Build
1. Kiểm tra file cấu hình gốc (`settings.gradle`, `build.gradle`, `pom.xml`, hoặc `package.json`).
2. Xác định danh sách các module / service đang hoạt động.
3. Ghi nhận các cổng (ports), database connections trong `application.properties` hoặc `application.yml`.

### Bước 2: Liệt Kê Endpoints & Entities
1. Tìm kiếm các Controller (`@RestController`, `@Controller`) để thống kê các REST APIs công khai.
2. Tìm kiếm các Entity JPA (`@Entity`) để vẽ sơ đồ dữ liệu và quan hệ giữa các bảng.

### Bước 3: Đối Chiếu Với Tài Liệu Hiện Hữu
1. Đọc `docs/` để so sánh giữa tài liệu thiết kế và mã nguồn thực tế.
2. Đánh dấu các điểm sai khác (nếu tài liệu nói cổng 8080 nhưng code chạy cổng 8081).

### Bước 4: Xuất Báo Cáo Khám Phá
In ra bản tóm tắt kiến trúc gồm:
- Danh sách dịch vụ & Cổng phục vụ.
- Danh sách Database & Schema sở hữu.
- Các công nghệ tích hợp (Kafka, Redis, PayOS...).
- Các rủi ro tiềm ẩn phát hiện trong code (Hardcoded secrets, thiếu timeout...).
