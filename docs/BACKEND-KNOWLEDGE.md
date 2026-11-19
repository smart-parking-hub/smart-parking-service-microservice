# SmartParkingHub Backend — bản đồ source và sổ vấn đề

Đối chiếu ngày 2026-11-19 trên `main` tại `61b8d7fbcfe45b4c33456ad669f0701ba348f035`. Mở [trang đọc nhanh](BACKEND-START-HERE.md) để chọn phần theo triệu chứng.

Các nhận định về code dựa trên source, cấu hình, migration và test có trong checkout. Bằng chứng rollout hoặc test từ phiên trước được ghi riêng ở mục môi trường. Trạng thái của một hệ thống đang chạy cần đối chiếu với image, cấu hình và schema hiện hành.

## Sở hữu dữ liệu và đường đi của request

Gateway chuyển lưu lượng HTTP. Các service dùng API nội bộ cho quyết định cần kết quả tức thì, chẳng hạn Booking phải biết Parking có giữ được chỗ. Kafka chuyển tiếp các sự kiện như đã cọc, đã hủy hoặc xét duyệt. Một luồng có thể có cả bước đồng bộ lẫn sự kiện phục hồi. Dùng `bookingId`, `paymentId` hoặc `eventId` tương ứng để kiểm tra các bước.

| Module | Port mặc định | Dữ liệu và trách nhiệm |
| --- | --- | --- |
| `api-gateway` | 8088 | Routing, JWT, CORS, app key và rate limit Redis theo cấu hình. |
| `identity-service` | 8081 | `smart_parking_identity_db`. `users`, `drivers`, `merchants`, `vehicles`, OTP, kỷ luật, hồ sơ KYC và audit. |
| `parking-lot-service` | 8082 | `smart_parking_lot_db`. Bãi, biểu phí, ảnh, slot theo nhóm, layout và slot vật lý, giữ chỗ, lịch và khả năng sử dụng chỗ. |
| `booking-service` | 8083 | `smart_parking_booking_db`. `bookings`, `queues`, `reviews`, lock chính sách hold, receipt quyết định thanh toán, Kafka outbox. |
| `payment-service` | 8084 | `smart_parking_payment_db`. `payments`, receipt tiền mặt, ví tài xế và merchant, ledger, settlement, refund, withdrawal, tranh chấp. |
| `notification-service` | 8085 | `smart_parking_notification_db`. `notifications`, `processed_events` và bản sao danh sách admin nhận thông báo. Gửi OTP qua SMTP. |
| `ai-service` | 8086 | Không có schema nghiệp vụ riêng. Gemini, kho FAQ trong resources, Parking Feign API. |
| `discovery-server` / `config-server` | 8761 / 8888 | Eureka và Spring Cloud Config là module riêng. Gateway dùng URL cấu hình trực tiếp và Eureka client mặc định tắt ở các app có cấu hình Eureka. |
| `common-library` | — | Java library dùng chung cho envelope, enum, sự kiện, chính sách lịch, thành phần outbox. Đổi enum có thể kéo theo nhiều schema và client. |

Trong nguồn hiện tại, phần lớn service đi theo controller → application → repository/gateway adapter. Có entity và repository interface trong package `domain`, nên dùng cấu trúc thực tế khi tìm file. Mỗi service chịu trách nhiệm cho dữ liệu, transaction và migration của mình. Tham chiếu qua ID/snapshot và API hoặc event, theo [quy tắc ranh giới](../.agents/rules/04-microservices-boundaries.md). Source cho cổng và schema là `<module>/src/main/resources/application.yml`.

## Gateway và xác thực

- Route public khai báo trong [gateway application.yml](../api-gateway/src/main/resources/application.yml). Nhiều nhánh route khai báo cả `/api/v1/…` và `/api/…` với `StripPrefix=0`. Khi gặp 404 cần kiểm tra cả mapping của controller đích, vì path được chuyển nguyên vẹn.
- Mở `ApiKeyValidationFilter`, `JwtAuthenticationFilter` của Gateway, rồi `SecurityConfig` và filter JWT/nội bộ ở service đích. Gateway làm sạch các header nhận dạng được kiểm soát trước khi chuyển request. Controller nghiệp vụ còn kiểm tra role và service/repository kiểm tra quyền trên bản ghi, chẳng hạn merchant của bãi hoặc chủ của booking.
- `X-Internal-Secret` và `X-Internal-Service-Token` phục vụ các lớp kiểm tra khác nhau trong code. Khi RPC lỗi, đọc client lẫn filter/controller đích để xác định header được yêu cầu. Các API vòng đời lịch, xác nhận thanh toán và OTP nội bộ có contract riêng. [Security model](security-model.md) · [Booking schedule contract](api-contracts/booking-schedule.md)
- Khi debug HTTP, xác định thứ tự Edge → Gateway → service → nghiệp vụ. So sánh response envelope, correlation ID và log ở đúng service trước khi phân loại 401/403/404/409. Test bắt đầu từ `JwtAuthenticationFilterTest` ở module tương ứng và test authorization của endpoint.

## Identity

- **Đăng ký và OTP.** `AuthController` dưới `/api/v1/auth` gọi `AuthServiceImpl`. Kiểm tra `RegisterRequest`, `SendOtpRequest`, chuẩn hóa bằng `PhoneNumberUtil` và truy vấn trùng tại repository. `email_otp_challenges` tách mục đích đăng ký và quên mật khẩu. Code hiện đặt 6 chữ số, TTL 300 giây, tối đa 5 lần thử. Identity tạo/kiểm tra challenge, Notification gửi email qua `NotificationOtpClient`. Quên mật khẩu giữ response chung để tránh lộ tài khoản tồn tại. Khi sửa tiếp validation, bảo toàn client OTP cũ và nhánh trả lỗi trùng dữ liệu đã có trên `main`. [AuthServiceImpl](../identity-service/src/main/java/com/smartparking/identity/application/service/impl/AuthServiceImpl.java) · [Contract OTP](api-contracts/email-otp.md)
- **Xe và hồ sơ.** `VehicleController`/`VehicleServiceImpl` sở hữu CRUD và quyền trên xe. Booking đọc xe đã đăng ký qua `OwnedVehicleClient`, lấy biển số từ xe sở hữu và so sánh loại xe với yêu cầu. `MerchantProfileController`/service xử lý hồ sơ và tài khoản nhận tiền. `AdminUserController` xử lý ban/unban, warning và audit.
- **KYC.** Merchant vào `/api/v1/merchant/profile/verification`, Admin vào `/api/v1/admin/merchant-verifications`. Luồng đi từ hồ sơ nháp qua bản nộp có version đến chờ duyệt, yêu cầu bổ sung hoặc xác minh. Mở `MerchantVerificationServiceImpl`, `MerchantVerificationAssessmentWorker` và `MerchantTaxLookupService`. V8–V10 lưu hồ sơ, lease của worker và lưu trữ riêng/nguồn đối chiếu. Admin xem nội dung tệp qua endpoint phân quyền. Lookup Casso là bước đối chiếu và kiểm tra lại version sau phản hồi. Mã đăng ký 12 chữ số được hướng dẫn đối chiếu giấy tờ, dừng trước Casso. Quyết định nghiệp vụ do Admin thực hiện và đi qua outbox. [Contract KYC](api-contracts/merchant-verification.md) · [Runbook KYC](deployment/merchant-verification.md)
- **Test.** Tìm `AuthServiceImplTest`, `AuthServiceTest`, `IdentitySchemaMigrationTest` và `MerchantVerificationIntegrationTest`. Khi có lỗi ghi trùng, tách lỗi validation request, kiểm tra dữ liệu trước khi ghi và lỗi unique constraint khi ghi đồng thời.

## Parking

- **Bãi và cấu hình.** `ParkingLotController` và `ParkingLotServiceImpl` phụ trách CRUD/search, `AdminParkingLotController` xử lý duyệt, `SlotServiceImpl` chỉnh sức chứa và giá theo loại xe. `ParkingLotStatusEnum` là trạng thái duyệt hiện có. Scheduled hold tại main kiểm tra `APPROVED`. Đóng/mở vận hành là tính năng ở worktree riêng. Đối với giá của vé đã tạo, đọc snapshot trong Booking và giá trả từ hold thay vì tính lại từ biểu phí hiện tại.
- **Ba loại dữ liệu về chỗ.** `slots` là nhóm sức chứa theo loại xe. `slot_holds` giữ cơ chế cũ và đường tương thích. `slot_reservations` là lịch theo thời gian. `physical_parking_slots` và `current_slot_allocations` thể hiện chỗ có mã thực và chiếm chỗ hiện tại. Mở `ScheduledSlotService` cho lịch và `SlotServiceImpl` cho hold cũ. Tình trạng chỗ ở thời điểm hiện tại và chỗ còn trống ở khung giờ tương lai đọc từ các đường khác nhau.
- **Public availability.** `GET /api/v1/parking-lots/{id}/availability` đánh giá toàn khoảng kèm thời gian đệm. `…/timeline` trả 48 block của ngày Việt Nam. `remainingCapacity` cấp cao nhất ở timeline luôn là 0 theo contract, đọc từng block. Kết quả tra cứu có thể thay đổi trước khi ghi hold, nơi server dùng transaction/khóa. Chế độ `GROUP` không sinh ID slot giả. [Contract lịch](api-contracts/booking-schedule.md) · [ScheduledSlotService](../parking-lot-service/src/main/java/com/smartparking/parkinglot/application/service/ScheduledSlotService.java)
- **Layout và ảnh.** `ParkingLayoutController`/`ParkingLayoutServiceImpl` quản lý source, draft, publish và activate physical. Public layout đọc bản publish. Việc bật chọn slot cụ thể phụ thuộc cấu hình physical booking. Tách file layout và ảnh bãi khi kiểm tra lỗi mất media. Layout hiện có adapter filesystem riêng và volume bền vững, ảnh bãi đi qua `ParkingLotImageController`/service và storage tương ứng. V8 thêm layout/slot vật lý, V9 thêm lịch, V10 cập nhật dữ liệu URL ảnh, V11 thêm mô tả bãi. [Contract layout](api-contracts/parking-layout.md) · [Runbook filesystem](deployment/parking-layout-filesystem.md)
- **Test.** `ScheduledReservationIntegrationTest`, `SlotHoldPhysicalIntegrationTest`, `SlotHoldReconciliationTest`, `ParkingLayoutServiceTest`, `ParkingLotSchemaMigrationTest`. Khi lưu DB đúng mà ảnh không tải, kiểm tra đường dẫn/volume của đúng container và request content.

## Booking

### Tạo vé, giữ chỗ và chặn trùng

1. `POST /api/v1/bookings` → `BookingController` → `BookingServiceImpl.createBooking`. Replay cùng request ID của cùng driver và intent hợp lệ trả vé cũ. Thay đổi ý định trả 409. Với request mới, validate giờ và xác minh xe sở hữu qua Identity.
2. `BookingCreationTransaction` dùng READ_COMMITTED. `HoldPolicyStore` lấy khóa theo xe để chặn đặt trùng giữa bãi hoặc giữa tài khoản, kể cả lúc tắt quota hold. Quota theo tài khoản/xe và kiểm tra overlap là hai quyết định riêng. Khi Parking trả cửa sổ vào sớm dài hơn kiểm tra ban đầu, Booking kiểm tra overlap lại dưới cùng khóa.
3. Booking ghi vé đang chờ thanh toán và gọi `ParkingLotClient` đến Parking để lấy lịch hold, slot được cấp và snapshot giá. Booking tính cọc bằng 20% số tiền ước tính qua `ParkingCharge.deposit`, làm tròn đến đơn vị VND. Kiểm tra đường bù trừ nếu Parking đã tạo hold nhưng transaction Booking không hoàn tất. Booking V12 sở hữu fields phiên bản lịch. Parking V9 sở hữu reservation.
4. `UnpaidHoldPolicy` và `HoldAbuseRecheckScheduler` quyết định 409 quota, 429 cooldown hoặc 503 khi bằng chứng thanh toán chưa rõ. Mặc định tối đa 3 lượt chưa trả mỗi tài khoản, ngưỡng 2 lượt bỏ tại cùng bãi trong 60 phút, cooldown 30 phút. Cấu hình limiter cũ có thể còn hiện diện nhưng luồng create hiện không gọi limiter đó. Protected/unknown từ Payment có ý nghĩa khác với đã chứng minh chưa trả.

[Luồng lịch và API](api-contracts/booking-schedule.md) · [Xung đột lịch cùng xe](api-contracts/vehicle-booking-schedule.md) · [Unpaid hold](api-contracts/unpaid-hold-policy.md)

### Giờ Việt Nam, QR, vào và ra bãi

- Nguồn quy tắc nằm ở `BookingSchedulePolicy` trong common, `BookingTimePolicy` trong Booking và `ScheduledSlotService` trong Parking. Lịch mới theo mốc 30 phút. Policy 1 cho vào đúng giờ và bảo vệ 30 phút sau ra. Parking mặc định tắt `APP_SCHEDULE_EARLY_ENABLED`. Khi bật, policy 2 cho vào sớm 15 phút và bảo vệ 15 phút sau ra. Policy 0/null là vé cũ. Thời gian gate thực tế giữ độ chính xác riêng. Khi đổi JDBC/timezone, kiểm tra round trip giờ Việt Nam và ranh giới hết hạn.
- Trạng thái có thể đi từ `PENDING_PAYMENT` → `PENDING_CHECKIN` → `PARKING`/`OVERTIME` → `AWAITING_FINAL_PAYMENT` → `COMPLETED` → `REVIEWED`. Hủy và hết hạn đi theo nhánh riêng. QR vào bãi của vé lịch đã trả cọc hết hạn ở thời điểm sớm hơn giữa giờ vào + 60 phút và giờ ra dự kiến. Gate từ chối tại đúng giờ ra dự kiến. Vé chờ cọc dùng hạn giữ chỗ riêng.
- `MerchantCheckInOutController` → `CheckInOutServiceImpl` kiểm tra merchant, vé và bãi. Check-in chiếm chỗ ở Parking theo token của lần thử. Check-out tính tiền trong `ParkingCharge` từ snapshot vé. Phần phí thường ít nhất bằng tiền khung đã đặt. Phần vượt giờ tính riêng theo nửa giờ, áp dụng hệ số và khung giờ đã lưu. Số cần thu bằng tổng phí trừ cọc, tối thiểu bằng 0. Khi đã ghi nhận ra bãi, vòng đời Parking giữ cooldown dựa trên giờ ra thực tế. `AWAITING_FINAL_PAYMENT` vẫn chặn theo quy tắc xe đang sử dụng. Đọc `ScheduledSlotLifecycle`, `ScheduledReservationReconciliation` và `BookingExpirationScheduler` khi trạng thái lệch giữa hai DB.
- `BookingRefundPolicy` quyết định tỷ lệ hoàn ở lúc hủy. Với vé đã cọc, tại hoặc trước mốc 2 giờ trước giờ vào thì hoàn 100%, sau mốc đó và trước giờ vào thì hoàn 50%, đến giờ vào hoặc no-show thì 0%. Payment thực hiện và phát lại trạng thái hoàn. Kiểm tra trạng thái refund để biết đã hoàn tiền. Queue và Review có controller/service riêng, trong cùng schema Booking.

[Business time](business-flows/booking-business-time.md) · [Thanh toán tại cổng](api-contracts/gate-final-payment.md) · [Rollout lịch](deployment/booking-schedule-rollout.md)

Test cần mở theo lỗi gồm `BookingTimePolicyTest`, `CheckInOutServiceImplTest`, `CheckoutBusinessTimeTest`, `ScheduledSlotLifecycleTest`, `UnpaidHoldPolicyIntegrationTest`, `PaymentEventListenerTest` và `ScheduleMigrationMySqlTest`.

## Payment

### PayOS, callback và tạo lại QR

- `PaymentController` → `PaymentServiceImpl`. Theo dõi `bookingId`, loại `DEPOSIT`/`FINAL`, attempt, `orderCode` và `paymentLinkId`. `PaymentAttemptGuard` tuần tự hóa nhóm attempt theo booking và loại thanh toán. Payment V18 lưu cơ chế recovery. QR hủy/hết hạn có thể được thay bằng attempt mới nếu vé vẫn trả được và attempt trước đủ bằng chứng trạng thái cuối. Attempt đang chờ hoặc trạng thái không rõ cần đối soát theo contract.
- Webhook và sync tra PayOS dùng cùng nguyên tắc xác nhận đúng order, trạng thái và số tiền trước khi ghi success. Tiền thiếu, bằng chứng không khớp hoặc kết quả chưa chắc chắn có nhánh review. Đối với 409, phân biệt hạn vé, xung đột attempt khác nguồn và kết quả thực tế ở provider. Không suy ra đã trả chỉ từ redirect return/cancel của trình duyệt.
- Payment có đường xác nhận Booking nội bộ, đối chiếu và event phục hồi. Booking vẫn là owner trạng thái vé. Nếu đã thu tiền mà UI chưa cấp QR, kiểm tra attempt ở Payment, quyết định apply ở Booking và tiến độ outbox/consumer trước khi xem Notification.

[Recovery PayOS](api-contracts/payos-payment-recovery.md) · [Xác thực số tiền](api-contracts/payos-checkout-amount-validation.md) · [Đồng bộ hủy](api-contracts/payos-cancellation-sync.md)

### Tiền mặt, ví và hoàn tiền

- **Tiền mặt tại cổng.** Booking chốt lựa chọn final payment trên vé trước khi Payment điều phối receipt. Mở `CheckInOutServiceImpl`, hai `InternalCashCheckoutController`, `CashCheckoutService` và `CashCheckoutRecoveryScheduler`. Payment V20 sở hữu `cash_checkout_receipts`. Kiểm tra lại flow sau timeout bằng cùng booking và receipt trước khi thu lần nữa. Trạng thái thu tại bãi và giao dịch đi qua PayOS có nguồn bằng chứng riêng. [Gate final payment](api-contracts/gate-final-payment.md)
- **Ví tài xế.** `DriverWalletPaymentService` và `DriverWalletLedger` quản lý available/held. Luồng hold → Booking ghi nhận quyết định → capture hoặc release có thể tạm thời `PENDING`. `GET /api/v1/payments/wallet?bookingId=…` tìm attempt của chính tài xế để tiếp tục sau mất response. Payment V19 và Booking V10 bổ sung dữ liệu liên quan. [Driver wallet](api-contracts/driver-wallet.md)
- **Hoàn và quyết toán.** Booking quyết định chính sách hủy. `RefundServiceImpl` và wallet recovery ở Payment xử lý hoàn theo consent, nguồn tiền và bằng chứng. Refund về ví là ghi có nội bộ. Hoàn ngân hàng có xác minh riêng. `DailySettlementServiceImpl` quyết toán merchant, `WithdrawalServiceImpl` xử lý rút tiền. Đọc `ledger_journals`/`ledger_postings` khi đối soát sổ kép và `wallet_ledger_entries` khi xem hoạt động ví. Deposit statement phân biệt tiền đã thu, đã hoàn, còn phải trả, escrow, phí và phần merchant. [Đối soát ví và tiền cọc](api-contracts/driver-wallet.md) · [Ledger](api-contracts/merchant-revenue-ledger.md)

### Doanh thu merchant

- `GET /api/v1/merchant/revenue` có `parkingLotId`, `startDate`, `endDate`, `granularity`. `MerchantRevenueController` → `MerchantWalletServiceImpl.getRevenueSummary`. Payment V21 thêm `payment_channel` và index. Số tiền cần tách tổng, kỳ, theo bãi và theo chuỗi thời gian. Số gross chính gồm gross đã settlement và gross chờ settlement. Receipt tiền mặt có số riêng, còn saldo ví đọc từ wallet.
- `ONLINE_TRANSFER`, `COUNTER_QR`, `WALLET` và `UNKNOWN` là giá trị cần đọc theo DTO/query hiện tại. Tiền mặt dùng receipt. `resolveChannel` của báo cáo tách riêng `COUNTER_QR` và `ONLINE_TRANSFER`, đưa các giá trị còn lại, kể cả `WALLET`, vào bucket `UNKNOWN`. Giữ UNKNOWN tách khỏi tổng chuyển khoản đã xác định. Kiểm tra cả `PaymentRepository` và `CashCheckoutReceiptRepository` khi sửa báo cáo.
- Route Parking `GET /api/v1/merchant/parking-lots/{id}/revenue` có sẵn nhưng trả hai trường cố định bằng 0 sau khi kiểm tra quyền bãi. Dùng route Payment với `parkingLotId` cho số liệu thực, như ghi ở mục vấn đề bên dưới. [ParkingLotController](../parking-lot-service/src/main/java/com/smartparking/parkinglot/api/controller/ParkingLotController.java#L77) · [MerchantRevenueController](../payment-service/src/main/java/com/smartparking/payment/api/controller/MerchantRevenueController.java)

Test theo lỗi gồm `PaymentRecoveryIntegrationTest`, `PaymentAttemptMigrationTest`, `CashCheckoutIntegrationTest`, `MerchantRevenueChannelRepositoryTest`, `MerchantRevenueSecurityTest`, `MerchantLedgerRepositoryTest` và `LegacyPaymentMigrationResourcesTest`.

## Notification

- `NotificationController` phục vụ inbox và trạng thái đọc. `NotificationServiceImpl` lưu bản ghi cho người nhận. Email OTP đi qua `InternalOtpMailController` và SMTP. Một lỗi nhận OTP hay lỗi trạng thái inbox cần kiểm tra hai đường khác nhau.
- `BookingEventListener` nhận `booking.events` cho tạo/giữ chỗ, check-in, hoàn tất và hủy. `PaymentEventListener` nhận `payment.events`. Với `PAYMENT_COMPLETED` loại `DEPOSIT`, Notification tạo thông báo booking thành công, trỏ đến booking và dedup theo nguồn gắn `paymentId` khi có giá trị. `eventId` còn được dùng ở `processed_events`. Notification V3 có unique index trên cặp nguồn/người nhận. Nếu thấy thông báo lặp, kiểm tra từng lớp dedup và người nhận. Nội dung thông báo giữ chỗ hiện không ghi deadline cố định.
- Admin operations nhận event outbox từ Payment/Parking và dùng snapshot ADMIN ACTIVE từ Identity để fan-out. KYC và quyết định withdrawal có listener riêng. Với sự kiện chưa đến, kiểm tra producer/outbox, topic, group, retry/DLT, `processed_events`, rồi `notifications`. Khi kiểm tra lịch sử, đối chiếu nơi producer ghi cùng transaction thay vì giả định mọi event đều theo cùng một triển khai. [Event catalog](event-catalog.md) · [Admin notifications](deployment/admin-notifications-runbook.md) · [PaymentEventListener](../notification-service/src/main/java/com/smartparking/notification/infrastructure/messaging/PaymentEventListener.java)
- Test bắt đầu từ `BookingEventListenerTest`, `PaymentEventListenerTest`, `NotificationServiceImplTest` và `MerchantVerificationNotificationHandlerTest`.

## AI

- `AiController` ánh xạ `/api/v1/ai/chat`, `/generate-description`, `/generate-mock-data`, `/extract-layout`. `AiParkingAssistantService` dùng ngữ cảnh bãi lấy từ `ParkingLotFeignClient` và FAQ nội bộ. Nếu chat trả sai bãi, xem thứ tự nhận diện bãi cụ thể/tên bãi, lookup từ Parking và nhánh policy trước khi sửa prompt.
- `AiContentGenerationService` xử lý nội dung gợi ý. `AiLayoutExtractionService` xác thực ảnh, gọi Gemini và chuẩn hóa kết quả cho người duyệt. Layout do Parking lưu bản draft và publish, qua endpoint riêng. Một JSON trích xuất thành công chỉ là dữ liệu gợi ý. Kiểm tra `GeminiApiClient` và timeout/fallback khi nhà cung cấp lỗi. [AiController](../ai-service/src/main/java/com/smartparking/ai/api/controller/AiController.java) · [Kịch bản AI](postman/smart-parking-ai-test-scenarios.md)
- Test mở theo luồng gồm `AiParkingAssistantServiceTest`, `AiContentGenerationServiceTest`, `AiLayoutExtractionServiceTest` và `GeminiApiClientTimeoutTest`.

## Môi trường và kiểm chứng

- `settings.gradle`, `build.gradle` và Gradle Wrapper khóa Java 21, Boot 3.3.5, Cloud 2023.0.3, Gradle 8.10.2 tại snapshot. Đã thấy Java 21 và Docker Compose trên máy trong phiên audit. Để test code ở backend root, dùng `.\gradlew.bat :<module>:test --tests '*<TênTest>'` trên Windows hoặc `./gradlew :<module>:test --tests '*<TênTest>'` trên shell Unix. Test tổng dùng task `test`. Đây là lệnh từ cấu hình, phiên ghi tài liệu kiểm tra đường dẫn và source.
- CI chạy build/test và nhóm test MySQL có điều kiện cho Recovery, hold và schedule. H2/test schema thường cấu hình khác production và nhiều bài MySQL cần env/schema riêng. Kiểm tra kết quả có chạy hay bị skip trước khi báo pass. [CI workflow](../.github/workflows/ci-build.yml)
- Compose nằm dưới `docker/compose/local`, `overlays`, `vps-single`, `vps-cluster`. Local mặc định MySQL host 3307 trong cấu hình service. Compose giữa container dùng hostname nội bộ. `scripts/dev/start-p0-local.bat` ghép overlay cho dataset giữ lại và image đã pin. Đọc đúng script và overlay để lấy nguồn dữ liệu. Cluster chia Edge/Data/Core, trong cấu hình hiện tại Payment và Notification nằm ở Data. [Compose](../docker/compose/README.md) · [Overlay](../docker/compose/overlays/README.md) · [CD workflow](../.github/workflows/cd-deploy.yml)
- Payment có hai lineage Flyway. Mặc định `db/migration`. Với dataset local có lịch sử V6/V7, Gradle dựng resources `db/local-v6-v7-compatible` từ [payment build.gradle](../payment-service/build.gradle) và Compose overlay chọn lineage phù hợp. Khi thấy lỗi checksum hoặc version, tra `flyway_schema_history`, image và cấu hình locations trước khi kết luận migration lỗi. [PayOS local lineage](api-contracts/payos-local-lineage.md) · [ADR 0008](adr/0008-retained-local-payment-lineage.md)
- Order thường quan trọng hơn build từng module. Lịch cần Parking V9 trước Booking V12. Nếu bật policy 2, Booking tương thích cần có trước khi bật cờ ở các instance Parking. MySQL 8.4 với default collation mới có thể vướng foreign key lịch sử của Parking V8. Xem [runbook rollout](deployment/booking-schedule-rollout.md) để xác nhận trên schema riêng và giữ lịch sử migration.
- Log, health và metrics dùng để xác minh phiên bản đang chạy. Giữ bằng chứng như SHA, image digest, profile/overlay và version Flyway khi ghi nhận sự cố. Bằng chứng triển khai ngày 2026-11-19 nằm trong [sự cố/khôi phục VPS 2](implementation/vps2-recovery-20261119.md); kiểm tra lại mục 1.9 trước thao tác mới. Test MySQL có điều kiện cần kiểm tra số bài thực sự chạy. Hồi cứu workspace `.agents/WORKING-METHODS-RETRO.md`, nếu có, chỉ là ngữ cảnh bổ sung cho worktree status/TRAILER; source/migration tại checkout mới là căn cứ.

## Các vấn đề đã xác định

| Vấn đề | Trạng thái tại snapshot và chỗ kiểm tra |
| --- | --- |
| Đóng/mở bãi và loại xe TRAILER | **Worktree riêng, chưa ở main.** `../.worktrees/parking-lot-service-sp-s3-rt-dat-02` ở `feat/parking-status-trailer-sync` base `ef42b50`, có thay đổi chưa commit. Main vẫn chỉ có 5 loại xe, đến `CONTAINER`. Nhánh nháp tách operating status khỏi duyệt, chặn hold mới khi đóng, giữ replay và lifecycle đã có. |
| Xung đột Identity migration khi tích hợp TRAILER | **Xác nhận từ hai cây file.** Worktree dùng `V8__add_trailer_vehicle_type.sql`. Main đã có Identity V8 KYC, V9 lease và V10 private storage. Phiên tích hợp cần đối chiếu Flyway history và cấp version còn trống trong Identity, không mang nguyên V8 qua. Parking main hiện tới V11, worktree dùng V12/V13. |
| Doanh thu một bãi trả 0 | **Xác nhận từ code.** Route thuộc Parking trả `totalRevenue=0` và `completedBookings=0` cố định. Route Payment là nơi tổng hợp dữ liệu thực. Chưa xác nhận tuyến gọi của mọi client ngoài repo và không coi đây là sự cố đang xảy ra trên production. |
| Ghi chú revenue còn nói đang chờ merge | **Lịch sử bàn giao**, không dùng làm trạng thái hiện tại. Git main tại snapshot đã chứa merge doanh thu `cc8592b`, KYC `b57f3df` và `cf18dd4`, validation `eaf470b`, thông báo cọc `61b8d7f`. Trạng thái code nằm ở Git, trạng thái rollout cần bằng chứng môi trường. |
| Bản đồ cổng, bảng và tiền bị lỗi thời | [Service catalog](service-catalog.md) ghi Gateway 8080 và thiếu AI. [Data ownership](data-ownership.md) còn tên `driver_profiles`/`merchant_profiles`/`queue_entries`, trong khi entity dùng `drivers`/`merchants`/`queues`. Phần đầu [hợp đồng revenue](api-contracts/merchant-revenue-ledger.md) giữ mô tả lịch sử. Đối chiếu DTO, code và migration mới khi tính số hiện hành. |
| Khả năng cập nhật tài liệu khi checkout thay đổi | Phần tóm tắt gắn snapshot cụ thể. Khi tiếp tục task, cập nhật mục liên quan nếu tìm thấy khác biệt ở SHA đang làm. Ghi triệu chứng, nguyên nhân có dẫn chứng, nhánh và kiểm thử. Để các giả thuyết hoặc tình trạng chưa quan sát ở trạng thái chờ xác minh. |

Khi nhận một lỗi mới, dùng trang đọc nhanh để chọn service owner. Tìm request hoặc ID cụ thể, đối chiếu controller/DTO, theo transaction đến repository hoặc client, rồi kiểm tra event nếu luồng có xử lý nền. Nếu cần lưu kết quả để phiên khác tiếp tục, ghi SHA, môi trường, bước đã quan sát được và bước còn thiếu tại mục nghiệp vụ tương ứng.
