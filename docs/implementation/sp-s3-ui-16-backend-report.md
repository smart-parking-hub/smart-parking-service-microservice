# SP-S3-UI-16 — Cập nhật kiểm chứng BE 2026-11-13

- Owner: SPH; last_verified: 2026-11-13; self-review, chưa có maintainer sign-off.
- Base: origin/main `12821754ef96343570b04b1e46ac0250a2f12938`. Thay đổi chỉ thuộc notifications; không gộp booking/physical-slot/Firebase layout của checkout cũ.
- Đã sửa migration: Identity **V7**, Payment **V16**, Parking **V7**, Notification **V3**. Giữ nguyên migration lịch sử. Event dispute ghi trong transaction `DisputeWriteService`; giữ `DisputeServiceImpl` mới trên main và regression tests evidence/history, bổ sung mock outbox cho constructor mới.
- Nội dung cập nhật này thay thế trạng thái hạn mức/Gradle chưa chạy trong báo cáo lịch sử phía dưới. Chưa deploy hoặc sửa production.

## Kết quả Gradle/JDK21 (offline, dependency graph thực)

| Module | Tổng test | PASS | FAIL | SKIPPED |
| --- | ---: | ---: | ---: | ---: |
| common-library | 8 | 8 | 0 | 0 |
| identity-service | 140 | 138 | 2 | 0 |
| payment-service | 125 | 123 | 0 | 2 |
| parking-lot-service | 71 | 71 | 0 | 0 |
| notification-service | 55 | 55 | 0 | 0 |
| Tổng | 399 | **395** | **2** | **2** |

- `compileJava`/`compileTestJava` của năm module: PASS. Lần đầu Payment test compile phát hiện hai constructor cũ trong test main; đã sửa và chạy lại Payment test PASS, bao gồm 3 test WriteService mới.
- Bốn task `identity-service:bootJar`, `payment-service:bootJar`, `parking-lot-service:bootJar`, `notification-service:bootJar`: PASS, có file jar.
- `AdminOperationsKafkaIntegrationTest`: **1/1 PASS** với broker nhúng: snapshot -> fan-out hai Admin, duplicate không tăng bản ghi, JSON lỗi được đưa vào DLT. Không tương đương broker production hoặc luồng FE–BE thật.
- Hai FAIL thuộc `IdentitySchemaMigrationTest` có sẵn trên main, thông báo không kết nối MySQL localhost:3306 bằng cấu hình test hiện tại. Không sửa/skip test để làm xanh. Các migration mới vẫn cần kiểm chứng full chain trên MySQL test.
- Hai SKIPPED của Payment được ghi nhận trong XML, không tính PASS. Gradle toàn bộ nhóm service **chưa xanh** vì hai MySQL test; các service khác đạt sau lượt sửa Payment.
- Tooling còn warning JVM class sharing và deprecated API ở test compile; không tuyên bố zero-warning toàn backend.

## Bằng chứng và giới hạn

Logs artifact `admin-notifications-pr/BE`: `gradle-verification.log`, `gradle-remaining.log`, `gradle-payment-final.log`, `test-summary.json`; XML và HTML nằm dưới `verification/<module>/build/`. Bản kiểm chứng là archive main cộng file task, không có Git metadata, không phải Git worktree. Các kết quả JUnit trực tiếp lần trước là lịch sử, không cộng vào 399 test Gradle.

Chưa kiểm thử MySQL migration/locking/duplicate thật, ACL broker, stack gateway/producer/consumer và FE–BE live. Chưa có reviewer độc lập hoặc nghiệm thu người dùng. Giữ PR draft, hoàn thành các kiểm tra này trước merge/deploy.


---

## Báo cáo lần triển khai trước (lịch sử)

# SP-S3-UI-16 — Báo cáo triển khai BE nối FE

- Owner: SPH; reviewer chưa được gán.
- last_verified: 2026-11-12.
- Trạng thái: **Đã viết code BE; kiểm chứng từng phần, chưa đủ điều kiện nghiệm thu toàn hệ thống.**
- Người dùng đã đồng ý triển khai plan BE; không có commit/PR/deploy trong lần làm này.

## Đã triển khai

1. Giữ bốn API notification để màn FE hiện có tiếp tục dùng: list, unread count, read một tin, read-all. List cố định createdAt/id giảm dần và chuẩn hóa page theo utility sẵn có; read lặp không ghi đè readAt; ownership/no-op ID khác tài khoản giữ nguyên. Bổ sung response 401 JSON ở security entry point.
2. Tạo typed operations events cho yêu cầu rút tiền, tranh chấp và hồ sơ bãi cần duyệt. Đưa enqueue outbox vào đúng transaction tạo nghiệp vụ. Nộp lại hồ sơ bãi có row lock và submission version; không phát tin cho thao tác sửa không hợp lệ khi đang PENDING.
3. Identity tự reconcile ID Admin ACTIVE, phát snapshot có version qua outbox; notification-service giữ projection chỉ ID/version/timestamp, không đọc chéo DB. Quét 10s khi thay đổi, heartbeat 60s; snapshot quá 180s hoặc không có recipient không được coi là đã phân phối.
4. Notification fan-out/processed marker cùng transaction; unique source_event_id/user_id chống duplicate. Hai Admin nhận hai bản với readAt riêng. Có validation envelope, giới hạn 1000 recipient, retry/DLT và đường replay có kiểm soát.
5. Shared outbox dùng datasource của từng producer; có retry/backoff/PENDING/FAILED/PUBLISHED và String producer riêng. Không sửa publisher/relay payment/refund cũ, không thay listener booking/payment/user cũ.
6. Viết migration additive và tài liệu event/data ownership/API/ADR/runbook, giữ nguyên migrations cũ và các thay đổi booking/parking-layout đã có trước task.

FE không cần đổi endpoint: notification mới được tạo vào bảng đã phục vụ GET hiện tại. Người dùng mở hoặc bấm Cập nhật trên `/admin/notifications` để tải tin; không có push/polling mới, broadcast, SMS hoặc quyết định xử lý nghiệp vụ trên trang thông báo.

## Phạm vi file

| Khu vực | File/cấu phần |
| --- | --- |
| common-library | `event/AdminOperationEvent.java`, `AdminRecipientSnapshot.java`; `messaging/TransactionalEventOutbox.java`, relay/config; compileOnly JDBC/Kafka/Boot cho shared infrastructure opt-in; test transaction/relay. |
| identity-service | Config import outbox; `AdminRecipientSnapshotScheduler`; V7 outbox/cursor/index eligibility; scheduler tests và fixture H2. |
| payment-service | Enqueue trong `WithdrawalServiceImpl`/`DisputeWriteService`; config outbox; V16; cập nhật tests producer và fixture H2. |
| parking-lot-service | Enqueue trong `ParkingLotServiceImpl`, lock khi nộp lại; field version trong ParkingLot; config/Kafka dependency; V7; producer tests và fixture H2. |
| notification-service | Handler/listener/Kafka config; source_event_id + V3; list/read/security chỉnh nhỏ; tests repository/API/JWT/fan-out/concurrency/migration/Kafka. |
| docs | ADR-0005, API contract, event catalog, data ownership, runbook và báo cáo này. |

Không xóa/reset các thay đổi sẵn có thuộc booking/physical slots/Firebase layout, common ErrorCode và tài liệu. Baseline status/diff được lưu local trong `build/sp-s3-ui-16-baseline-status.txt` và `build/sp-s3-ui-16-baseline.patch`.

## Quyết định cụ thể so với plan

- Dùng full snapshot reconciliation thay event riêng ở từng mutation role/status, để bao phủ cả Admin seed và cập nhật quản trị hiện có. Eligibility có độ trễ; không phải check quyền đồng bộ tại mỗi operation.
- Outbox mới `admin_event_outbox` tách khỏi outbox payment/refund cũ để không đổi topic routing/serializer của luồng tài chính.
- Khi không có người nhận/snapshot quá hạn: retry rồi DLT, không silent drop. Replay gửi tới nhóm recipient đủ điều kiện tại lúc phân phối lại.
- Read state là đã xem của từng Admin; không biến thành trạng thái đã xử lý hồ sơ chung.

## Kết quả kiểm chứng và giới hạn

Đây là kiểm chứng cục bộ; **không có kết quả Gradle toàn repo, Kafka/MySQL đầy đủ hoặc FE–BE live** ở thời điểm lập báo cáo.

| Kiểm tra | Bằng chứng / Kết quả |
| --- | --- |
| Tái hiện trước sửa | Gradle JDK21 chạy `NotificationReadRepositoryTest`: FAIL đúng vì read lặp cập nhật hàng đã đọc; `build/sp-s3-ui-16-red-jdk21.log`. |
| Biên dịch trực tiếp | PASS: toàn bộ main Java của common-library, Identity, Payment, Parking, Notification bằng JDK21 javac và dependency cache, output riêng `build/sp-s3-ui-16-offline-complete`; test source liên quan cũng biên dịch. Listener cập nhật cuối biên dịch riêng PASS. Logs `sp-s3-ui-16-direct-javac-complete.log`, `sp-s3-ui-16-listener-compile.log` trong build. Không thay thế Gradle resolution/bootJar. |
| Test nghiệp vụ trực tiếp | **49/49 PASS**, exit 0: outbox commit/rollback/retry/replay, Identity snapshot/heartbeat/capacity, migration Notification, transport envelope, service notification và producer Withdrawal/Dispute/Parking. JUnit launcher, JDK21, H2; `build/sp-s3-ui-16-direct-tests-complete-final.log`. |
| API/JWT/fan-out trực tiếp | **9/9 PASS**, exit 0 bằng Spring Boot + MockMvc/H2; JWT và spoofed headers, ownership, >100 bản ghi/read-all, read lặp, snapshot cũ/stale/rỗng, rollback fan-out, concurrent duplicate. Log `build/sp-s3-ui-16-api-direct-complete.log`. Tổng hai lượt cuối: **58 test khác nhau PASS**. Lần đầu đã phát hiện và sửa fixture JWT/expected exception; không sửa giảm yêu cầu bảo mật. |
| Test Kafka/DLT | Đã viết `AdminOperationsKafkaIntegrationTest`, biên dịch source test; chưa chạy broker nhúng hoặc cluster thật. |
| Gradle build/test sau sửa | Chưa hoàn tất. Sandbox không ghi được wrapper lock trong cache user; dùng cache workspace vẫn không kết nối daemon. Automatic approval cho chạy ngoài sandbox không hoàn tất do hạn mức (không phải kết luận lệnh không an toàn). |
| Diff | `git diff --check` đã chạy, không có lỗi whitespace; Git có thông báo LF/CRLF ở một số file. |

Harness kiểm tra trực tiếp là artifact local ignored: `build/sp-s3-ui-16-offline-compile.ps1`, `sp-s3-ui-16-offline-tests.ps1`, `OfflineTestRunner.java`. Nó dùng các jar cache theo BOM Spring Boot 3.3.5 khi có, có fallback artifact cache cho phụ thuộc khác; bởi vậy không được coi là bằng chứng Gradle resolution/CI. Mockito subclass + ByteBuddy agent chỉ áp dụng cho lần chạy trực tiếp trong sandbox, không thay config test chuẩn của repo.

Artifact runner cũng in warning về hai junit-platform.properties và một số auto-configuration không thuộc service khi dùng classpath cache rộng; không tuyên bố toàn bộ tooling zero-warning. Bằng chứng test trực tiếp này tách biệt khỏi các kết quả FE ở lần triển khai trước. Không cộng test FE giả lập vào bằng chứng backend thật.

## Những việc phải hoàn tất trước nghiệm thu

- Chạy Gradle compile/test/bootJar đúng dependency graph và toàn bộ các service liên quan.
- Chạy test Kafka nhúng và xác minh migration/locking/duplicate trên MySQL test, kiểm tra DLT/offset/replay.
- Khởi động stack test có gateway/Identity/Payment/Parking/Notification/Kafka/MySQL; tạo nghiệp vụ thật, đăng nhập FE thật và chứng minh event -> outbox -> notification -> API -> UI. Không dùng mock hoặc insert seed để thay bước producer.
- Kiểm tra ACL broker và rollout consumer/schema trước producer; giữ migration additive khi rollback.
- Reviewer sign-off và nghiệm thu; chưa deploy VPS hoặc sửa dữ liệu production.

Chi tiết cấu hình, giới hạn và replay: [runbook](../deployment/admin-notifications-runbook.md), [ADR](../adr/0005-admin-operations-notification-delivery.md), [API/FE integration](../api-contracts/admin-notifications.md).
