# Workflow: /start-feature - Quy Trình Phát Triển Tính Năng Mới

## Mục Đích
Kích hoạt quy trình tuần tự chuẩn khi kỹ sư phát triển một tính năng mới trong SmartParkingHub.

## Các Bước Thực Thi Của Agent (SOPs)

1. **Xác Định Service & Bounded Context:**
   - Xác định tính năng thuộc về Service nào (`identity-service`, `parking-lot-service`, `booking-service`, `payment-service`, hay `notification-service`).
   - Đọc kỹ `docs/business-domains.md` và `docs/data-ownership.md`.

2. **Thiết Kế Hợp Đồng (Contract First):**
   - Định nghĩa DTO Request / Response trong package `api/`.
   - Nếu có giao tiếp liên service: định nghĩa Kafka Event trong `common-library/src/main/java/com/smartparking/common/event/`.

3. **Lập Bản Kế Hoạch Thay Đổi (Change Plan):**
   - Soạn thảo kế hoạch theo template [`.agents/templates/change-plan-template.md`](file:///d:/Documents/JAVA%20WEB%20SERVICE/SmartParkingHub/.agents/templates/change-plan-template.md).
   - Trình bày kế hoạch và **dừng lại chờ người dùng phê duyệt**.

4. **Hiện Thực Hóa Mã Nguồn (Sau khi được duyệt):**
   - Viết Domain Model & Invariants trong `domain/`.
   - Viết Use Case trong `application/`.
   - Viết Controller & Validation trong `api/`.
   - Viết Repository / Outbox Publisher trong `infrastructure/`.
   - Viết Flyway Migration SQL (nếu có bảng mới).

5. **Kiểm Thử & Đánh Giá:**
   - Viết Unit Test và Integration Test cho Use Case.
   - Chạy lệnh kiểm chứng: `scripts/test-all.bat`.

6. **Đồng Bộ Tài Liệu & Review Diff:**
   - Cập nhật `docs/api-contracts/` và `docs/event-catalog.md`.
   - Rà soát diff, đảm bảo không có file rác hoặc secrets.
