# Project Profile - SmartParkingHub Ecosystem

## 1. Thông Tin Tổng Quan (Project Metadata)
- **Tên dự án:** SmartParkingHub (Nền tảng Quản lý & Đặt chỗ Bãi Đỗ Xe Thông Minh Thời Gian Thực)
- **Trạng thái:** Đang trong giai đoạn chuyển đổi (Modernization) từ Monolith sang Microservices chuẩn Enterprise.
- **Môi trường hoạt động:**
  - Local Dev (Windows 11, Java 21, Node.js v20, MySQL 8.4, Redis 7.2)
  - Staging / Production Target (Ubuntu VPS, Docker Compose, Nginx, PayOS Gateway)

---

## 2. Công Nghệ & Khung Ứng Dụng (Technology Stack)

| Thành phần | Công nghệ chính | Trạng thái hiện tại | Thư mục mã nguồn |
| :--- | :--- | :--- | :--- |
| **Frontend App** | React, Vite, Tailwind/Vanilla CSS | Đang chạy port 5173 | [`smart-parking-app/`](file:///d:/Documents/JAVA%20WEB%20SERVICE/SmartParkingHub/smart-parking-app) |
| **Monolith Backend** | Spring Boot, Java, MySQL, JPA | Đang chạy port 8080 | [`smart-parking-service/`](file:///d:/Documents/JAVA%20WEB%20SERVICE/SmartParkingHub/smart-parking-service) |
| **Microservices Target**| Spring Boot 3.3.5, Java 21, Gradle, Kafka, Redis, Outbox | Đã scaffold 6 services | [`smart-parking-microservice/`](file:///d:/Documents/JAVA%20WEB%20SERVICE/SmartParkingHub/smart-parking-microservice) |
| **Multi-Agent Kit** | Antigravity AI, MCP, SOP Workflows | Đã cấu hình tại root microservice | [`AGENTS.md`](file:///d:/Documents/JAVA%20WEB%20SERVICE/SmartParkingHub/smart-parking-microservice/AGENTS.md), [`.agents/`](file:///d:/Documents/JAVA%20WEB%20SERVICE/SmartParkingHub/smart-parking-microservice/.agents) |

---

## 3. Quy Trình Phê Duyệt & Lệnh Bị Cấm (Guardrails)
- **Quy trình bắt buộc:** Tuân thủ [Cổng Phê Duyệt Tối Cao](file:///d:/Documents/JAVA%20WEB%20SERVICE/SmartParkingHub/smart-parking-microservice/AGENTS.md#4-cổng-phê-duyệt-tối-cao-prompt-approval-gate) trong `AGENTS.md`. Bắt buộc dừng lại và xuất Implementation Plan trước khi sửa mã nguồn hoặc can thiệp database.
- **Lệnh cấm tuyệt đối:** `rm -rf`, `DROP DATABASE`, `TRUNCATE`, `git push --force`, `kill -9` ngoài danh mục, thay đổi firewall máy chủ.
