# Gửi OTP qua SMTP thật ở local

Last verified: 2026-11-18. Scope: local notification-service configuration.

## Nguyên nhân API thành công nhưng Gmail không nhận thư

`compose.restore-p0.local.yaml` đặt SMTP cố định thành `mailpit:1025`, tắt
authentication và STARTTLS. Compose lab được tạo từ cấu hình này cũng có thể giữ
các giá trị đó. Mailpit nhận thư trong máy nên API gửi OTP vẫn trả 200; thư không
được chuyển đến Gmail. Có `.env.email.local` trên đĩa chưa đủ: file phải được nạp
và các giá trị phải được tham chiếu trong Compose thay cho cấu hình cố định.

`compose.smtp.local.yaml` ghi đè riêng bảy biến mail của notification-service.
Áp dụng overlay này **cuối cùng** để các cấu hình lab trước đó không ghi đè SMTP.
Không thay image, database, network, port hay cấu hình của dịch vụ khác.

## Cấu hình và áp dụng

Tại thư mục gốc backend, tạo `.env.email.local` (Git đã bỏ qua file này):

```dotenv
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-sender@gmail.com
MAIL_FROM=your-sender@gmail.com
MAIL_PASSWORD=your-google-app-password
MAIL_SMTP_AUTH=true
MAIL_STARTTLS_ENABLE=true
```

Dùng mật khẩu ứng dụng của tài khoản gửi, không dùng mật khẩu đăng nhập Gmail
thông thường. Không commit file này hoặc ghi credentials vào log.

Giữ nguyên các file Compose, tên project và env file của lab đang chạy, thêm
`--env-file .env.email.local` sau các env file hiện tại và thêm
`-f docker/compose/overlays/compose.smtp.local.yaml` sau tất cả các Compose file hiện tại. Ví dụ với lab
P0 khôi phục (hạ tầng đã chạy):

```powershell
docker compose --project-name docker --env-file .env --env-file .env.email.local -f docker/compose/local/compose.yaml -f docker/compose/overlays/compose.restore-p0.local.yaml -f docker/compose/overlays/compose.smtp.local.yaml config --quiet
docker compose --project-name docker --env-file .env --env-file .env.email.local -f docker/compose/local/compose.yaml -f docker/compose/overlays/compose.restore-p0.local.yaml -f docker/compose/overlays/compose.smtp.local.yaml up -d --no-deps --pull never notification-service
```

Với lab có file Compose sinh riêng, thay hai file Compose đầu bằng **đúng file
đang được lab sử dụng**; không dùng ví dụ P0 để thay cấu hình lab khác. Xem đường
dẫn tại label `com.docker.compose.project.config_files` của container hiện tại.
Không cần build lại image. Dịch vụ gửi mail gián đoạn ngắn khi tạo lại container.

Overlay yêu cầu host, port, địa chỉ gửi, username và password không được rỗng;
auth và STARTTLS mặc định bật. Overlay này dành cho SMTP có xác thực. Lab dùng
Mailpit tiếp tục chạy cấu hình cũ, không cần overlay SMTP thật.

## Kiểm chứng và hoàn tác

- Chạy `config --quiet` để kiểm tra mà không in credentials. Không chia sẻ đầu ra
  `docker compose config` đầy đủ vì có thể chứa bí mật.
- Kiểm tra notification-service healthy và mail component tại
  `/actuator/health` có status `UP`, location là SMTP thật thay cho Mailpit.
- Bấm gửi lại mã ở trang đăng ký sau thời gian cooldown; kiểm tra đúng email
  nhận và mục Spam. SMTP chấp nhận thư chưa chứng minh thư đã vào hộp thư.
- Luôn giữ overlay khi tạo lại notification-service; dùng lại cấu hình Mailpit
  cố định mà bỏ overlay sẽ đưa thư về hộp thư thử.
- Hoàn tác: chạy lại lệnh `up` với cấu hình lab và env file ban đầu, bỏ overlay
  `compose.smtp.local.yaml` và env file mail bổ sung. Chỉ tạo lại
  notification-service, không xóa volume hay reset database.

Điều tra local ngày 18/11/2026 xác nhận OTP cũ nằm trong Mailpit; cấu hình Gmail
sẵn có đăng nhập SMTP qua STARTTLS thành công (235). Sau khi áp dụng override,
notification-service healthy và mail health check `UP` tại `smtp.gmail.com:587`.
Chưa xác minh thư mới đã vào Gmail; quá trình kiểm tra không tự gửi email ngoài.

Kiểm chứng PR trên worktree sạch từ `main`: Compose merge chỉ thay bảy biến mail;
thiếu từng biến trong năm biến bắt buộc đều bị từ chối; mặc định auth/TLS bật.
Lệnh `gradlew.bat test --no-daemon --max-workers=2 --continue` với JDK 21 và MySQL
test local đúng cổng hoàn tất thành công: 859 test được báo cáo, 846 đạt, 13 bỏ
qua, không có failure/error. Các test identity và notification đạt đủ 205/205.
