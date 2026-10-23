# Quy Chuẩn Git Workflow & Commit Chuẩn Doanh Nghiệp (09-git-commit-and-branching-standards.md)

> **⚠️ BẮT BUỘC TUÂN THỦ:** Mỗi khi Agent hoặc lập trình viên thực hiện thao tác tạo nhánh, commit hoặc push mã nguồn, phải luôn đối chiếu quy chuẩn này.

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

### Các Scope chuẩn trong hệ sinh thái SmartParkingHub:
- `repo` / `infra`: Cấu hình toàn bộ repo, Gradle root, Docker compose, CI/CD
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

---

## 4. Kế Hoạch 10 Đợt Commit & Nhánh Chuẩn Bị Sẵn

- **Đợt 1 - Dọn dẹp & Git Hygiene:**
  - Nhánh: `chore/clean-build-artifacts-and-gitignore`
  - Scope: Cập nhật `.gitignore`, xóa sạch các tệp build nhị phân bị cache (`.gradle`, `build/`, `*.class`, `*.bin`).
- **Đợt 2 - Hạ tầng & Cấu hình Docker / Gradle:**
  - Nhánh: `chore/infra-docker-compose-setup`
  - Scope: `build.gradle`, `compose.yaml`, `compose.local-full.yaml`, `docker/docker-compose.apps.yml`, dependencies.
- **Đợt 3 - Thư viện dùng chung (Common Library):**
  - Nhánh: `feat/common-library-dto-events`
  - Scope: `common-library` (ApiResponse, PageResponse, ParkingLotEvent, Enums, Utils).
- **Đợt 4 - API Gateway & JWT Security Filter:**
  - Nhánh: `feat/api-gateway-jwt-filter`
  - Scope: `api-gateway` (JwtAuthenticationFilter, application.yml, gateway test).
- **Đợt 5 - Identity Service (Xác thực & Người dùng):**
  - Nhánh: `feat/identity-service-auth-domain`
  - Scope: `identity-service` (V1 migration, domain, application, api, security cleanup, tests).
- **Đợt 6 - Parking Lot Service (Quản lý bãi xe):**
  - Nhánh: `feat/parking-lot-service-management`
  - Scope: `parking-lot-service` (V1 migration, domain, slot tracking, api, tests).
- **Đợt 7 - Booking Service (Đặt chỗ & Vòng đời đặt):**
  - Nhánh: `feat/booking-service-reservation`
  - Scope: `booking-service` (V1 migration, booking entities, saga event producer, tests).
- **Đợt 8 - Payment Service (Thanh toán & PayOS):**
  - Nhánh: `feat/payment-service-integration`
  - Scope: `payment-service` (V1 migration, PayOS webhook, payment verification, tests).
- **Đợt 9 - Notification Service (Xử lý thông báo Kafka):**
  - Nhánh: `feat/notification-service-consumer`
  - Scope: `notification-service` (V1 migration, kafka event listener, notification templates, tests).
- **Đợt 10 - Discovery & Config Servers Testing:**
  - Nhánh: `test/infra-discovery-config-tests`
  - Scope: `discovery-server/src/test/`, `config-server/src/test/`.
