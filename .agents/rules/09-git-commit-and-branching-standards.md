# Quy Chuẩn Git Workflow & Commit — smart-parking-microservice (09-git-commit-and-branching-standards.md)

> **⚠️ BẮT BUỘC TUÂN THỦ:** Mỗi khi Agent hoặc lập trình viên thực hiện thao tác tạo nhánh, commit hoặc push mã nguồn trong repository `smart-parking-microservice`, phải luôn đối chiếu quy chuẩn này.
> Đối với quy chuẩn hợp nhất toàn bộ workspace SmartParkingHub, tham khảo master rule: `.agents/rules/01-git-commit-and-branching-standards.md`.

---

## 1. Quy Tắc Đặt Tên Nhánh (Branch Naming Convention)

Cấu trúc chuẩn: `<type>/<scope>-<short-description>`

| Type | Mục đích sử dụng | Ví dụ |
| :--- | :--- | :--- |
| `feat/` | Phát triển tính năng mới, API mới, DTO mới | `feat/identity-jwt-auth`, `feat/booking-lifecycle` |
| `fix/` | Sửa lỗi nghiệp vụ, bảo mật, routing, config | `fix/payment-webhook-checksum`, `fix/gateway-cors` |
| `chore/` | Cấu hình build, dọn dẹp gitignore, update docker | `chore/clean-build-artifacts-and-gitignore`, `chore/docker-compose-vps` |
| `refactor/` | Tái cấu trúc mã nguồn mà không thay đổi logic | `refactor/common-dto-page-response` |
| `test/` | Viết thêm Unit Test, Integration Test | `test/booking-integration-tests` |
| `docs/` | Cập nhật tài liệu kỹ thuật, SRS, README | `docs/srs-parking-contract-update` |

---

## 2. Tiêu Chuẩn Commit Message (Conventional Commits + Detailed Body)

Mỗi commit **BẮT BUỘC** phải có cấu trúc 3 phần rõ ràng:

```gitcommit
<type>(<scope>): <Mô tả ngắn gọn hành động ở thể chủ động (dưới 72 ký tự)>

<Đoạn văn ngắn giải thích bối cảnh/nguyên nhân và mục tiêu của thay đổi>

Chi tiết các thay đổi:
- <Chi tiết 1: Các class / interface / migration file được tạo hoặc sửa>
- <Chi tiết 2: Logic nghiệp vụ xử lý chính hoặc cấu hình bổ sung>
- <Chi tiết 3: Tác động đến database, API contract hoặc tính tương thích>

Ghi chú kiểm thử / Lưu ý triển khai (nếu có):
- <Test case hoặc lưu ý môi trường (Local / VPS)>
```

### Các Scope chuẩn trong Microservice Backend:
- `repo` / `infra`: Cấu hình toàn bộ repo, Gradle root, Docker compose, CI/CD, scripts
- `common`: `common-library` (DTO, Event, Enum, Exceptions, Util)
- `gateway`: `api-gateway` (Spring Cloud Gateway, Routing, JWT Filter)
- `identity`: `identity-service` (Tài khoản, phân quyền, authentication, JWT)
- `parkinglot`: `parking-lot-service` (Bãi đỗ, vị trí đỗ, slot tracking)
- `booking`: `booking-service` (Đặt chỗ xe, trạng thái vòng đời booking)
- `payment`: `payment-service` (Thanh toán, PayOS, webhook, xác thực giao dịch)
- `notification`: `notification-service` (Thông báo Kafka consumer, SMS/Email)
- `config`: `config-server` (Cấu hình tập trung Spring Cloud Config)
- `discovery`: `discovery-server` (Eureka Discovery)

---

## 3. Quy Trình 5 Bước Khi Nhận Lệnh Push Tự Động

Khi người dùng yêu cầu push hoặc thực hiện một đợt commit:

1. **Kiểm tra trạng thái repo:** Chạy `git status -s` để rà soát chính xác các file đang thay đổi, đảm bảo không có file secret (`.env`, private keys) hay file build (`.gradle`, `build/`).
2. **Tạo và chuyển nhánh:** `git checkout -b <tên-nhánh-chuẩn>`.
3. **Stage đúng phạm vi:** Chỉ `git add` các file thuộc phạm vi của đợt đó.
4. **Tạo commit chuẩn:** Viết commit message đầy đủ Header + Body giải thích chi tiết theo mục 2.
5. **Push lên Remote:** Chạy `git push -u origin <tên-nhánh>` và báo cáo tóm tắt commit hash cùng danh sách file đã push cho người dùng.
