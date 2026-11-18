# Email OTP — SP-S3-SEC-01

Đối chiếu source ngày 14/11/2026. Repository thực tế là
`smart-parking-service-microservice`. Luồng SMTP và response không chứa OTP đã có
trước task này; đợt rà soát bổ sung kiểm thử HTTP và chốt hợp đồng hiện hữu.

## Phân định trách nhiệm

- Identity sinh mã ngẫu nhiên 6 chữ số, lưu bản băm trong `email_otp_challenges`,
  kiểm tra thời hạn/mục đích/số lần thử và tiêu thụ mã trong transaction nghiệp vụ.
- Identity gọi `POST /api/v1/internal/mail/otp` của Notification với
  `X-Internal-Service-Token`; connect timeout 2 giây, read timeout 5 giây và circuit breaker.
- Notification gửi email bằng Spring Mail/SMTP. Không lưu OTP vào DB Notification.
  API Gateway hiện không định tuyến endpoint mail nội bộ.
- Chỉ trả thành công sau khi lệnh gửi SMTP thành công. SMTP chấp nhận thư chưa
  bảo đảm thư đã vào inbox. Lỗi gửi làm rollback thay đổi challenge của Identity.

## Hợp đồng công khai

| Endpoint POST | Request | Kết quả |
| --- | --- | --- |
| `/api/v1/auth/otp/send` | `{ "email": "driver@example.test" }`; `phone` tùy chọn để kiểm tra trùng | 200, metadata; 409 khi email/phone đã tồn tại hoặc gửi lại trong cooldown |
| `/api/v1/auth/forgot-password/otp` | `{ "email": "driver@example.test" }` | 200 cùng metadata/thông báo cho email tồn tại, không tồn tại và trong cooldown; chỉ gửi thư khi có tài khoản và hết cooldown |
| `/api/v1/auth/otp/verify` | `{ "email": "driver@example.test", "otpCode": "001234" }` | 200 khi hợp lệ; kiểm tra REGISTER, chưa tiêu thụ mã |
| `/api/v1/auth/register` | Các trường đăng ký hiện hữu cùng `email`, `otpCode` | 201 khi đăng ký thành công; kiểm tra lại và tiêu thụ mã REGISTER |
| `/api/v1/auth/forgot-password/reset` | `{ "email": "driver@example.test", "otpCode": "001234", "newPassword": "..." }` | 200 khi thành công; kiểm tra và tiêu thụ mã RESET_PASSWORD |

Hai API gửi mã trả envelope `ApiResponse` với `data` **chỉ gồm**:

```json
{ "expiresInSeconds": 300, "resendAfterSeconds": 60 }
```

Không trả `otpCode`, mã thô hoặc bản băm. Không xóa `otpCode` khỏi các **request
xác minh**: backend cần trường này để chứng minh người dùng sở hữu email. Không
thêm fallback trả OTP cho client cũ; client từng đọc OTP từ response phải chuyển
sang đọc email. Giữ nguyên tên endpoint, envelope và các trường metadata hiện tại.

Mã hết hạn sau 300 giây, gửi lại sau 60 giây, tối đa 5 lần nhập sai, dùng một lần.
Mã gắn với email được chuẩn hóa và purpose; mã đăng ký không dùng được cho reset.
Đầu vào không hợp lệ và mã sai/hết hạn/đã dùng trả 400. Lỗi SMTP hoặc thiếu cấu
hình gửi mail trả 500 với thông báo an toàn theo error contract hiện tại; không
trả chi tiết lỗi nhà cung cấp. Task này không đổi mã HTTP hiện hữu.

## Hợp đồng gửi mail nội bộ

`POST /api/v1/internal/mail/otp` nhận
`{ "email": "driver@example.test", "code": "001234", "purpose": "REGISTER" }`;
purpose còn lại là `RESET_PASSWORD`. Bắt buộc token nội bộ khớp cấu hình, không
được để trống. Thành công trả 204 với body rỗng; thiếu/sai token trả 403; payload
không hợp lệ trả 400; lỗi SMTP trả 500. Token gateway/JWT người dùng không thay thế
token gửi mail. Không expose service nội bộ ra Internet.

## Cấu hình

- Identity và Notification: `APP_INTERNAL_SERVICE_TOKEN` dùng chung, nhận từ môi trường.
- Identity: `NOTIFICATION_SERVICE_URL` trỏ đến Notification trong mạng nội bộ.
- Notification: `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`,
  `MAIL_FROM`, `MAIL_SMTP_AUTH`, `MAIL_STARTTLS_ENABLE`.
- SMTP có connect/read/write timeout trong `notification-service/src/main/resources/application.yml`.
  Mặc định bật STARTTLS và yêu cầu STARTTLS. Không lưu credentials trong frontend/source.
  Với Mailpit local mới tắt auth/TLS theo cấu hình lab.

Nếu lab trả 200 nhưng Gmail không nhận OTP, kiểm tra SMTP có đang trỏ đến
Mailpit hay không. Xem [hướng dẫn override SMTP local](../implementation/local-smtp-delivery.md)
để nạp credentials và áp dụng `docker/compose/overlays/compose.smtp.local.yaml` sau cấu hình lab hiện tại.

Không cần migration mới, thay RBAC/JWT hay chuyển dữ liệu giữa các service cho
task này. Giữ migration lịch sử và dữ liệu hiện hữu.

## Kiểm chứng

Chạy trên Java 21 từ root backend:

```powershell
.\gradlew.bat :identity-service:test --tests '*EmailOtpIntegrationTest'
.\gradlew.bat :notification-service:test --tests '*OtpEmailTest' --tests '*InternalOtpMailIntegrationTest'
.\gradlew.bat test
```

- `EmailOtpIntegrationTest`: HTTP response của cả hai API gửi chỉ chứa metadata;
  email không tồn tại/cooldown không gửi thêm; đầu vào sai và request thiếu OTP
  bị chặn. Kiểm tra thêm hash, thời hạn, số lần thử, dùng lại, mục đích, rollback,
  gửi đồng thời và các luồng đăng ký/reset.
- `OtpEmailTest`: MIME email, mã có số 0 đầu, hai mục đích, nội dung/thời hạn,
  token nội bộ và lỗi SMTP an toàn.
- `InternalOtpMailIntegrationTest`: HTTP qua security filter/controller/service;
  chặn thiếu/sai token, validation, 204 không có body, lỗi SMTP không lộ mã/email
  hoặc chi tiết nhà cung cấp và lần gửi tiếp theo hoạt động.

Các test dùng H2 và mock `OtpMailSender`/`JavaMailSender`; không chứng nhận giao
thư đến hộp thư thật. Kiểm thử thủ công với SMTP đã cấu hình: gửi mã, đọc email,
đăng ký/reset, kiểm tra response không có OTP, thử gửi lại trong 60 giây, nhập sai,
dùng lại và mã quá 5 phút. Không đưa mã thực hoặc credentials vào bằng chứng.

Đánh giá task và kết quả chạy hiện tại: xem
[báo cáo nghiệm thu](../implementation/SP-S3-SEC-01-otp-review-20261114.md).
