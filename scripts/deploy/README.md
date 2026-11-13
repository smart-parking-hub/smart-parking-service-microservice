# Hướng Dẫn Tự Động Hóa Triển Khai Microservices Lên Cụm VPS (Deploy to VPS)

Hệ thống script này cho phép bạn đứng từ máy **Local (Windows)**, chỉ cần chạy 1 lệnh là tự động:

1. Biên dịch toàn bộ mã nguồn Java Spring Boot thành file `.jar` (`gradlew bootJar -x test`).
2. Tự động build Docker Image với tag mới nhất (`latest`).
3. Đẩy (Push) Docker Image lên GitHub Container Registry (`ghcr.io/smart-parking-hub/...`).
4. Kết nối an toàn qua **SSH** tới các VPS, kích hoạt Docker Compose pull image mới nhất về và khởi động lại container (Zero-Downtime Rolling Update).
5. Tự động dọn dẹp các Docker Image cũ trên VPS để không bị đầy dung lượng ổ đĩa.

---

## Danh Mục Tệp Tin Trong Thư Mục `scripts/deploy/`

| Tệp                  | Mô tả                                                          |
| :------------------- | :------------------------------------------------------------- |
| `deploy-vps.bat`     | Menu tương tác tiện lợi cho người dùng (Double click để chạy). |
| `deploy-vps.ps1`     | Script xử lý chính bằng PowerShell với đầy đủ cờ tham số.      |
| `deploy.config.json` | Cấu hình IP, cổng SSH, SSH Key, phân bổ Service vào từng VPS.  |

---

## Cấu Hình Thông Tin VPS (`deploy.config.json`)

Mặc định, cấu hình sử dụng SSH Key có sẵn tại `~/.ssh/github_actions_vps`:

- **VPS 1 (Edge & Gateway):** IP `103.20.102.190`, Port `2222`, User `tungdeptrai`. Quản lý `api-gateway`, `identity-service`, `nginx`.
- **VPS 2 (Data & Async):** IP `116.118.6.107`, Port `22`, User `tungdeptrai`. Quản lý `payment-service`, `notification-service`, `ai-service`, `kafka`, `mysql`.
- **VPS 3 (Core Services):** IP `103.20.102.137`, Port `22`, User `tungdeptrai`. Quản lý `parking-lot-service`, `booking-service`.

> [!TIP]
> Bạn có thể chỉnh sửa `deploy.config.json` bất kỳ lúc nào nếu thay đổi IP, Port hoặc SSH Key của VPS.

---

## Hướng Dẫn Sử Dụng

### Cách 1: Qua Menu Điều Khiển Tập Trung `menu.bat`

1. Mở terminal tại thư mục `smart-parking-microservice`:
   ```cmd
   menu.bat
   ```
2. Chọn **`1.6`** để mở công cụ triển khai VPS.

### Cách 2: Chạy Giao Diện Tương Tác `deploy-vps.bat`

Click đúp chuột vào `scripts\deploy\deploy-vps.bat` hoặc gõ:

```cmd
.\scripts\deploy\deploy-vps.bat
```

Menu sẽ hiển thị 6 lựa chọn:

- `[1]` Cập nhật **TOÀN BỘ 3 VPS**.
- `[2]` Cập nhật riêng **VPS 1** (Edge).
- `[3]` Cập nhật riêng **VPS 2** (Data & Async).
- `[4]` Cập nhật riêng **VPS 3** (Core Services).
- `[5]` Cập nhật riêng **1 Service cụ thể** (ví dụ: `booking-service`).
- `[6]` Kiểm tra kết nối SSH tiền trạm (**Dry-Run Test**).

### Cách 3: Chạy Lệnh PowerShell Nâng Cao

```powershell
# Kiểm tra kết nối tới các VPS mà không build thật
.\scripts\deploy\deploy-vps.ps1 -DryRun

# Cập nhật toàn bộ 3 VPS
.\scripts\deploy\deploy-vps.ps1 -Target all

# Chỉ cập nhật 1 VPS cụ thể
.\scripts\deploy\deploy-vps.ps1 -Target vps1

# Chỉ cập nhật 1 service khi vừa fix bug xong (tiết kiệm thời gian)
.\scripts\deploy\deploy-vps.ps1 -Service identity-service

# Bỏ qua bước Gradle build nếu đã build trước đó
.\scripts\deploy\deploy-vps.ps1 -Target vps1 -SkipBuild
```

---

## Lưu Ý Về Xác Thực Docker Registry (GHCR)

Để Local có quyền đẩy Docker Image lên GHCR, bạn chỉ cần thực hiện đăng nhập 1 lần duy nhất trên máy cá nhân:

```cmd
echo YOUR_GITHUB_PERSONAL_ACCESS_TOKEN | docker login ghcr.io -u YOUR_GITHUB_USERNAME --password-stdin
```

Token cần có quyền `write:packages` và `read:packages`.
