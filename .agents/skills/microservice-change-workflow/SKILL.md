---
name: microservice-change-workflow
description: >-
  Use this skill when implementing a new feature, modifying existing business logic, or applying
  an approved bugfix in any SmartParkingHub microservice. Enforces the strict 8-step engineering lifecycle.
---

# Microservice Change Workflow Skill (Quy Trình 8 Bước Sửa Đổi Dịch Vụ)

Quy trình chuẩn mực được kích hoạt khi Agent tiến hành viết hoặc sửa mã nguồn trong hệ thống Microservices:

## 8 Bước Bắt Buộc

### Bước 1: Tiếp Nhận & Xác Định Bounded Context
- Xác định rõ tính năng/lỗi thuộc Microservice nào (`identity`, `parking-lot`, `booking`, `payment`, hay `notification`).
- Cấm sửa đổi lan sang các microservice khác mà không có lý do kiến trúc.

### Bước 2: Đọc Tài Liệu & Source Code Thực Tế
- Đọc `docs/data-ownership.md` để nắm rõ bảng dữ liệu thuộc quyền sở hữu của service đó.
- Đọc code hiện tại trong package `domain/`, `application/`, `infrastructure/`.

### Bước 3: Thu Thập Bằng Chứng & Lên Kế Hoạch (Change Plan)
- Viết bản Implementation Plan theo mẫu [`.agents/templates/change-plan-template.md`](file:///d:/Documents/JAVA%20WEB%20SERVICE/SmartParkingHub/.agents/templates/change-plan-template.md).

### Bước 4: Chờ Phê Duyệt Của Người Dùng (Approval Gate)
- Dừng lại, trình bày kế hoạch và chỉ hành động tiếp khi người dùng xác nhận "Đồng ý".

### Bước 5: Chỉnh Sửa Mã Nguồn Đúng Phạm Vi (Minimal Scope)
- Sửa code tuân thủ Java 21 và Spring Boot 3.x.
- Giữ nguyên toàn bộ các comment không liên quan.

### Bước 6: Viết Test & Kiểm Chứng Tự Động
- Viết Unit Test và Integration Test.
- Chạy lệnh `./gradlew test` hoặc `scripts/test-all.bat`.

### Bước 7: Tự Review Toàn Bộ Diff
- Kiểm tra `git diff` để đảm bảo:
  - Không có file rác hoặc debug log thừa (`System.out.println`).
  - Không có secrets bị lộ.
  - Diff sạch sẽ, chỉ chứa đúng nội dung cần thay đổi.

### Bước 8: Đồng Bộ Hóa Tài Liệu (Docs Sync) & Báo Cáo
- Cập nhật tài liệu API, Data ownership hoặc ADR nếu có thay đổi.
- Báo cáo kết quả hoàn thành kèm đường link file đã chỉnh sửa.
