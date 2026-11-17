# SP-PAY-01 — sửa CI lệch ngày khi khởi đầu UTC

## 1. Thông tin chung

18/11/2026, fix/payment, cùng PR #55 và nhánh feat/pay01-driver-wallet. Run lỗi: [43924895769](https://github.com/smart-parking-hub/smart-parking-service-microservice/actions/runs/43924895769), head c48e928. [Evidence local](pay01-recovery-evidence/timezone-verification.json).

## 2. Bối cảnh và nguyên nhân

CI báo 4 test DriverWalletIntegrationTest lỗi: hai case capture muộn nhận businessDate D+1 thay vì D, một case thiếu batch ngày gốc và một case count 1 thay vì 2. Các dòng drop table/Hikari shutdown cuối log là cleanup schema H2 test, không phải nguồn lỗi.

PaymentServiceApplication đặt Asia/Ho_Chi_Minh trong PostConstruct. Datasource/Hibernate có thể khởi tạo trước và ghi nhớ timezone UTC của runner; sau đó default timezone đổi thành UTC+7. Các đường bind/read timestamp và DATE không còn nhất quán. Máy local khởi đầu UTC+7 không tái hiện sự lệch này.

## 3. Before / After

| Trường hợp | Before | After |
| --- | --- | --- |
| JVM bắt đầu UTC | 4/20 test ví gốc thất bại, lệch ngày cận 23:59 | 20/20 test ví gốc qua, giữ nguyên timestamp/assertions |
| Cấu hình timezone | PostConstruct, có thể sau JPA/datasource | Static BeanFactoryPostProcessor, trước singleton persistence |
| JVM UTC+7 | Qua trên local | Vẫn qua |
| Thứ tự tạo singleton | Chưa có kiểm tra riêng | Hai test UTC/America-Los_Angeles đăng ký observer trước processor, observer vẫn nhận business timezone |

## 4. Chi tiết thay đổi

Chỉ sửa thứ tự khởi tạo trong PaymentServiceApplication và thêm PaymentTimeZoneBootstrapTest. Timezone nghiệp vụ giữ Asia/Ho_Chi_Minh. DriverWalletIntegrationTest, logic quyết toán/phí/hoàn, CI workflow, schema và frontend giữ nguyên.

Thử nghiệm chỉ đổi fixture sang native JPA giảm còn 1 lỗi DATE/batch duplicate nên đã bỏ; file test ví được trả nguyên về head trước sửa. Giải pháp cuối xử lý thứ tự bootstrap, không ép runner đổi timezone để che lỗi.

## 5. Bằng chứng kiểm thử

- Red: khởi đầu UTC trên code cũ → 20 test ví, 4 lỗi đúng nhóm CI.
- Green UTC và UTC+7: mỗi lượt 22/22 qua, gồm 20 test ví nguyên gốc + 2 test bootstrap.
- Gradle build toàn repo với các JVM test bắt đầu UTC → exit 0, **766 total: 756 passed, 10 conditional skips, 0 failures/errors**. Payment: 206 total, 202 passed, 4 skipped. Packaging qua; 74 task, 34 executed/40 up-to-date.
- Init script ngoài repo chỉ đặt systemProperty user.timezone cho JVM test để tái hiện; không đưa thay đổi timezone runner vào workflow.
- Test dùng H2 in-memory, Kafka listener tắt, loại override datasource/profile/DB ngoài khỏi process. Không kết nối production.
- Kết quả trên là local. CI remote cần đọc check của commit mới được push; không suy ra CI PASS từ build local.

## 6. Triển khai và rollback

Không migration hoặc thay đổi contract API. Chưa deploy/restart service trong phần sửa này. Startup payment giữ cùng múi giờ nhưng thiết lập sớm hơn. Có thể rollback commit nếu cần, không sửa dữ liệu/journal. Các kiểm chứng MySQL tùy chọn chưa kích hoạt trong build local vẫn được tính skipped, không tính PASS.

## 7. Checklist

- [x] Tái hiện đúng 4 lỗi trước sửa với UTC.
- [x] Giữ nguyên các assertion ngày, tiền, journal và cạnh tranh của test ví gốc.
- [x] UTC/UTC+7 và build toàn repo đã qua.
- [x] Diff hẹp, không secret/config cá nhân, không tắt test hay sửa workflow.
- [ ] Java linter riêng: N/A, không có gate riêng trong Gradle đã đọc.
- [ ] CI remote: xem Checks trên PR ở commit mới; evidence local không thay thế kết quả remote.
