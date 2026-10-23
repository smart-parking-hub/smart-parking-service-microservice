# Workflow: /tech-review - Quy Trình Đánh Giá Công Nghệ & Môi Trường

## Mục Đích
Kích hoạt quy trình khi cần đánh giá stack hiện tại, đối chiếu khoảng cách (gap analysis) so với mục tiêu, và đề xuất phương án tối ưu cho 3 môi trường: **Local**, **VPS R4**, và **VPS Lớn**.

## Các Bước Thực Thi Của Agent (SOPs)

1. **Khảo Sát Hiện Trạng (Read-only Survey):**
   - Đọc các tệp `build.gradle`, `settings.gradle` và `application.yml` trong toàn bộ microservices.
   - Thống kê:
     - Phiên bản Java runtime và toolchain.
     - Phiên bản Spring Boot, Spring Cloud, Hibernate, Flyway.
     - Danh sách các container và tài nguyên sử dụng trong thư mục `docker/`.

2. **So Sánh Khoảng Cách (Gap Analysis):**
   - So sánh với [TECHNOLOGY-BASELINE.md](file:///d:/Documents/JAVA%20WEB%20SERVICE/SmartParkingHub/smart-parking-microservice/docs/09-technology-standards/TECHNOLOGY-BASELINE.md) và [DECISION-MATRIX.md](file:///d:/Documents/JAVA%20WEB%20SERVICE/SmartParkingHub/smart-parking-microservice/docs/09-technology-standards/DECISION-MATRIX.md).
   - Xác định:
     - Những thư viện nào đã lỗi thời hoặc có nguy cơ bảo mật.
     - Những công nghệ nào đang bị "thừa thãi / over-engineering" gây lãng phí RAM trên VPS R4.
     - Những thiếu sót cốt lõi (ví dụ: thiếu timeout, thiếu idempotency, thiếu health check).

3. **Phân Bổ Kế Hoạch Theo 3 Môi Trường:**
   - **Local Lab:** Cách chạy đầy đủ với Docker Compose + Kafka + Redis để dev/test toàn diện.
   - **VPS R4 (Resource-limited):** Cấu hình giới hạn memory limit 512MB mỗi container, log rotation, dọn dẹp các service phụ trợ không cần thiết.
   - **VPS Lớn (High-capacity):** Kế hoạch scale-out bản ghi và monitoring nâng cao.

4. **Xuất Bản Báo Cáo Đánh Giá & ADR:**
   - Soạn thảo đề xuất và trình bày cho người dùng theo đúng [Cổng Phê Duyệt Tối Cao](file:///d:/Documents/JAVA%20WEB%20SERVICE/SmartParkingHub/smart-parking-microservice/AGENTS.md#4-cổng-phê-duyệt-tối-cao-prompt-approval-gate).
