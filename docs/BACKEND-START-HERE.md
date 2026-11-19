# SmartParkingHub Backend — đọc trước khi làm việc

Đọc trang này khi bắt đầu hoặc tiếp tục công việc BE; sau đó mở đúng mục trong
[bản đồ source và vấn đề](BACKEND-KNOWLEDGE.md). Luôn kiểm tra `git status --short --branch`,
`git log -1 --oneline` và `git worktree list` trước khi dựa vào một snapshot.

Bản đồ nghiệp vụ được đối chiếu tại `61b8d7f`; main `2f56c82` bổ sung sửa deploy mà
không đổi nghiệp vụ. Lần xác minh VPS ngày 2026-11-19: CD `44564988649`, 7/7 CURRENT,
pending=0, exit 0; xem [sự cố và khôi phục VPS 2](implementation/vps2-recovery-20261119.md).
Đây là bằng chứng tại thời điểm kiểm tra, không thay thế mục 1.9 cho trạng thái mới.

Nhánh `docs/repo-backend-handoff-ignore` chọn lọc phần Compose/Casso/tài liệu còn lại;
xem [phạm vi push, file local và kết quả kiểm tra](implementation/backend-publish-selection-20261119.md).
Push nhánh không phải merge main hoặc triển khai VPS. Env/credential thật và báo cáo
chạy thử chỉ giữ local; các runbook dưới đây đủ dùng trong checkout mới.

## Nắm hệ thống trong hai phút

- Repo Git nằm trong `smart-parking-microservice/`. Workspace cha chứa frontend, tài liệu và các worktree riêng. Kiểm tra `git status --short --branch`, `git log -1 --oneline` và `git worktree list` trong backend để xác định checkout.
- Backend dùng Java 21, Spring Boot 3.3.5, Gradle 8.10.2, MySQL, Kafka và Redis. Gateway mặc định chạy ở **8088**. Identity, Parking, Booking, Payment, Notification và AI lần lượt ở **8081–8086**. Compose có thể đổi địa chỉ và cách công bố port.
- **Identity** sở hữu tài khoản, xe, OTP và hồ sơ KYC. **Parking** sở hữu bãi, giá, layout, sức chứa và lịch slot. **Booking** sở hữu vé, trạng thái, chính sách check-in/out và quyết định hoàn. **Payment** sở hữu giao dịch, PayOS, receipt tiền mặt, ví và ledger. **Notification** sở hữu inbox và gửi email. **AI** tạo gợi ý và đọc dữ liệu bãi qua API.
- Một lượt đặt có dữ liệu ở ba nơi. Booking giữ mã vé và snapshot. Parking giữ quyền sử dụng chỗ theo giờ và ghi nhận chiếm chỗ. Payment giữ bằng chứng thu tiền. Để kiểm tra sai lệch, dùng cùng `bookingId` qua các API, log và dữ liệu của từng service. Mỗi schema có một owner.
- Lịch đặt dùng giờ Việt Nam, mốc 30 phút. Policy 1 mặc định có 30 phút đệm sau giờ ra. Policy 2 bật từ Parking và chia đệm thành 15 phút vào sớm, 15 phút sau giờ ra. Vé đã tạo giữ policy đã lưu. Vé có cọc và vé đang chờ cọc có deadline khác nhau.
- Thanh toán có nhiều trạng thái độc lập. QR PayOS có thể hết hạn khi vé vẫn còn hiệu lực. Một lần thanh toán chưa rõ kết quả cần được đối soát trước khi cho phép nguồn khác. Tiền mặt tại cổng có receipt riêng. Gross chờ quyết toán và số dư ví là hai số khác nhau.
- Mã chính đã có KYC, doanh thu theo kênh, chỉnh validation đăng ký và thông báo cọc. **Đóng/mở bãi và `TRAILER` còn trong worktree riêng ở snapshot này.** Identity V8 tại main đã dùng cho KYC, trùng số với migration TRAILER trong worktree.

## Chọn điểm vào theo triệu chứng

| Gặp vấn đề | Mở mục này |
| --- | --- |
| 401/403/404 qua gateway, gọi nội bộ không qua | [Gateway và xác thực](BACKEND-KNOWLEDGE.md#gateway-và-xác-thực) |
| Đăng ký trùng, OTP, xe, KYC, Casso | [Identity](BACKEND-KNOWLEDGE.md#identity) |
| Tìm bãi, thiếu ảnh, sơ đồ, giá theo xe, status bãi | [Parking](BACKEND-KNOWLEDGE.md#parking) |
| Đặt trùng xe, availability lệch, chưa cọc, QR, check-in/out | [Booking](BACKEND-KNOWLEDGE.md#booking) |
| PayOS 409, callback trễ, thanh toán ví, hoàn tiền, lệch số doanh thu | [Payment](BACKEND-KNOWLEDGE.md#payment) |
| Đặt/cọc xong thiếu hoặc trùng thông báo | [Notification](BACKEND-KNOWLEDGE.md#notification) |
| Chat trả sai bãi, trích sơ đồ hoặc tạo mô tả | [AI](BACKEND-KNOWLEDGE.md#ai) |
| Chạy local, Flyway, MySQL, Compose, kiểm thử | [Môi trường và kiểm chứng](BACKEND-KNOWLEDGE.md#môi-trường-và-kiểm-chứng) |
| Trạng thái nhánh, việc còn dở, điểm dễ nhầm trong tài liệu | [Các vấn đề đã xác định](BACKEND-KNOWLEDGE.md#các-vấn-đề-đã-xác-định) |

Controller, cấu hình thực tế của môi trường, implementation, migration và test của luồng đang xét là bằng chứng chính. Các tài liệu contract và ADR bổ sung lý do thiết kế. Trạng thái triển khai cần xác nhận riêng bằng phiên bản image, cấu hình thực tế và Flyway history. Quy định sửa backend và thao tác production nằm trong [AGENTS.md](../AGENTS.md).

## Chạy local, kiểm tra và deploy

- Xem [Compose guide](../docker/compose/README.md): capability MySQL/Redis, Kafka,
  monitoring, discovery/config và quality tách riêng; preset cũ vẫn được giữ.
  `menu.bat` mục 1.8 là integration opt-in, mục 2.5 chỉ render/kiểm cấu hình bằng env giả.
- [Audit Compose](BACKEND-COMPOSE-AUDIT.md) là bằng chứng trước bảo trì, có chú thích
  phần đã chọn sửa và phần còn giữ lại. Không dùng đề xuất lịch sử thay cho guide hiện hành.
- Mục 1.7 chọn Local build hoặc GitHub CI; mục 1.9 kiểm SHA/artifact/digest và health
  thực tế. [Hướng dẫn deploy](../scripts/deploy/README.md) giải thích guard checkout sạch,
  token GHCR, trạng thái/pending và rollback. Re-run commit cũ vẫn dùng workflow cũ.
- Casso là tra MST hỗ trợ Admin, không tự phê duyệt KYC. [Hợp đồng và cấu hình](api-contracts/merchant-verification.md)
  mô tả năm biến Identity-only; mặc định tắt, template không chứa key. Test live chỉ
  bật có chủ đích bằng `CASSO_LIVE_TEST=true`; test thường không gọi provider.
- Kiểm tra migration readability không xác nhận SQL/Flyway. Test MySQL cần DB tách
  biệt; không sửa checksum hoặc reset dataset giữ lại để làm test xanh.
