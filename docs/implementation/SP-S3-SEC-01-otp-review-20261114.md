# SP-S3-SEC-01 — rà soát OTP, 14/11/2026

## Kết luận đối chiếu

Mục tiêu chính đã tồn tại trong source trước đợt này:

| Bằng chứng | Hiện trạng |
| --- | --- |
| `identity-service/.../application/dto/SendOtpResponse.java:12` | Chỉ có `expiresInSeconds`, `resendAfterSeconds`; không có `otpCode` |
| `identity-service/.../application/service/impl/AuthServiceImpl.java:124` | Sinh mã backend, lưu hash, flush trước gửi, không đưa mã vào response |
| `identity-service/.../infrastructure/client/NotificationOtpClient.java` | Gọi Notification nội bộ, token bắt buộc, timeout và circuit breaker |
| `notification-service/.../application/service/OtpEmailService.java:40` | Dùng `JavaMailSender`, chuyển lỗi SMTP thành thông báo an toàn |
| `notification-service/src/main/resources/application.yml` | SMTP nhận cấu hình từ môi trường, timeout và STARTTLS |

Các đường dẫn `...` ở trên tương ứng package `src/main/java/com/smartparking/identity`
hoặc `src/main/java/com/smartparking/notification` trong service tương ứng.

Điều chỉnh task theo thực tế:

- Giữ `otpCode` trong RegisterRequest, VerifyRegisterOtpRequest và
  ForgotPasswordResetRequest: đây là dữ liệu người dùng nhập để xác minh, không
  phải dữ liệu backend trả về. Không xóa cơ chế xác minh để làm đúng nghĩa đen tiêu đề.
- Không triển khai SMTP lần hai, thêm service/thư viện hoặc migration không cần thiết.
- Không sửa RBAC/JWT, các nghiệp vụ thanh toán/booking, frontend hoặc monolith.
- Tương thích giữ nguyên API hiện tại; không tái đưa OTP vào response cho client cũ.

## Kế hoạch và thay đổi thực hiện

Mục tiêu: khóa hành vi hiện tại bằng kiểm thử tại ranh giới HTTP và đưa hợp đồng
OTP vào tài liệu backend. Phạm vi Identity, Notification và tài liệu; không đổi
production source/config/schema. Người dùng đã yêu cầu tiếp tục thực hiện task.

- Bổ sung 4 test HTTP trong `EmailOtpIntegrationTest`, chạy controller, validation,
  security và service thật trên H2; SMTP được mock qua application port.
- Tăng độ chặt kiểm tra hash: đối chiếu với chính mã đã gửi và `PasswordEncoder.matches`.
- Thêm `InternalOtpMailIntegrationTest` với 4 test qua HTTP/security/service thật,
  mock transport SMTP; xác minh token, validation, response rỗng và lỗi an toàn.
- Thêm [API contract](../api-contracts/email-otp.md) cùng hướng dẫn cấu hình và nghiệm thu.

Rủi ro thấp: chỉ bổ sung test và tài liệu. Các test không gửi email ra ngoài.
Rollback: hoàn tác đúng các thay đổi test/tài liệu của task; không rollback DB hay
thay đổi dịch vụ đang chạy. Working tree có nhiều thay đổi từ task khác; không
reset/revert toàn bộ repo.

## Kết quả kiểm chứng tại checkout ban đầu

Java 21.0.11, Gradle Wrapper 8.10.2, chạy local với `--offline --no-daemon --console=plain`.

| Kiểm tra | Kết quả |
| --- | --- |
| Identity test suite | 145/145 đạt, gồm 16 test `EmailOtpIntegrationTest` |
| Notification test suite | 60/60 đạt, gồm 4 `OtpEmailTest` và 4 `InternalOtpMailIntegrationTest` |
| Tổng nhóm OTP | 24/24 đạt, gồm 8 test HTTP mới |
| `gradlew.bat test` toàn backend | `BUILD SUCCESSFUL`, 1m53s; 591 test được khai báo, 585 đạt, 6 skipped, 0 failures/errors |
| Tự review diff / `git diff --check` | Đạt; thay đổi task chỉ gồm 2 file test và 2 file tài liệu |

Lệnh toàn backend sử dụng kết quả `UP-TO-DATE` cho các task không đổi, gồm Identity
đã chạy thành công trong lần kiểm chứng ngay trước đó; không ép chạy lại mọi test.
Sáu test skipped ngoài OTP: bốn MySQL migration IT của Booking/Payment chưa cấu
hình URL database kiểm thử, hai filesystem test của Parking cần quyền tạo symlink
mà môi trường Windows hiện tại không cho phép.

Bằng chứng local:

- [Identity HTML report](../../identity-service/build/reports/tests/test/index.html)
- [Notification HTML report](../../notification-service/build/reports/tests/test/index.html)
- [Log regression](../../otp-backend-regression.log)
- XML từng suite ở `<service>/build/test-results/test/TEST-*.xml`.

Các thư mục build/log được Git bỏ qua, báo cáo này lưu kết quả đọc từ XML và lệnh
Gradle đã hoàn tất. Test dùng H2 và mock transport mail; không chứng nhận gửi/nhận
qua SMTP thật trong đợt này. Không thay đổi cấu hình hay triển khai VPS.

Việc tạo nhánh, commit và PR thực hiện theo yêu cầu tiếp theo của người dùng. Chưa có sign-off độc lập của reviewer; không đánh dấu hoàn tất toàn bộ card thay cho người duyệt.

## Kiểm chứng nhánh PR từ main mới nhất

Nhánh `test/identity-email-otp-contract` được tạo từ `origin/main` tại `8c2ced3`,
chỉ mang 4 file của task. Checkout ban đầu và các task khác được giữ nguyên.

- Lệnh `gradlew.bat :identity-service:test :notification-service:test` thất bại
  ở Identity: 143/145 đạt, hai test MySQL trong `IdentitySchemaMigrationTest`
  không kết nối được `localhost:3306`; Notification chưa được chạy trong lệnh
  này vì Gradle dừng ở task thất bại. Các test MySQL này đã có trên main và không
  được thay đổi trong PR. Docker engine local chưa chạy.
- Chạy lại đúng phạm vi với
  `gradlew.bat :identity-service:test --tests '*EmailOtpIntegrationTest' :notification-service:test --offline --no-daemon --console=plain`:
  `BUILD SUCCESSFUL` trong 3m09s. Identity OTP 16/16, toàn bộ Notification 60/60;
  không failure/error. Trong đó nhóm OTP gồm 24/24 test.
- Log local của PR: `otp-pr-validation.log` lưu lần chạy có lỗi môi trường,
  `otp-pr-focused-validation.log` lưu lần chạy thành công. Các log/build report
  không được commit. HTML/XML trong checkout PR phản ánh lần chạy gần nhất,
  không phải toàn bộ số liệu lịch sử của checkout ban đầu ở trên.
- CI hiện cấp MySQL 8.4 cho test; cần xem CI và sign-off trước khi merge.
  Không bỏ qua hoặc chỉnh test migration chỉ để làm xanh kết quả local.
