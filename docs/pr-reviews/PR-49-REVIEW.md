# Báo Cáo Đánh Giá Kỹ Thuật (Review) Pull Request #49

> **Dự án:** `smart-parking-service-microservice` (Backend Microservices)  
> **Tiêu chuẩn áp dụng:** `ak-review-pr v3.0.0` (19-Step Workflow, Anti-AI Slop, Finding Standard & Safety Engine)  
> **Thời điểm rà soát:** 2026-11-15T13:45:00+07:00  
> **Commit SHA rà soát:** `a98db87ea992cfc9fe745ff576c30650a97960c4` (refs/pull/49/head)  
> **Quyết định đề xuất:** ⚠️ **REQUEST CHANGES (Yêu cầu bổ sung sửa đổi trước khi Merge)**

---

## 1. Thông Tin Tổng Quan Pull Request

| Thuộc tính | Chi tiết |
| :--- | :--- |
| **Pull Request** | [#49 - fix(booking): Use Vietnam time for deadlines and checkout charges](https://github.com/smart-parking-hub/smart-parking-service-microservice/pull/49) |
| **Tác giả** | `@lochuynh22` |
| **Nhánh nguồn (Head)** | `fix/booking-business-time` (`a98db87`) |
| **Nhánh đích (Base)** | `main` |
| **Phạm vi thay đổi** | 7 files (`+122` dòng / `-5` dòng) |
| **Tương thích Merge** | `Clean` (Không xung đột với nhánh `main` mới nhất) |
| **Trạng thái CI (Check Runs)** | ✅ `build`: COMPLETED / SUCCESS |
| **PR Frontend đồng hành** | [smart-parking-app-v2 #81](https://github.com/smart-parking-hub/smart-parking-app-v2/pull/81) *(Chặn lặp check-in và cải thiện UX vé)* |

---

## 2. Tóm Tắt Đánh Giá (Executive Summary)

PR #49 giải quyết một **lỗi sai số nghiêm trọng trong môi trường triển khai thực tế (Docker/VPS chạy múi giờ mặc định UTC)**:
- Trước PR này, hàm `checkIn` đã sử dụng múi giờ Việt Nam (`Asia/Ho_Chi_Minh`), nhưng `checkOut` lại dùng `LocalDateTime.now()` theo múi giờ của máy chủ host (UTC). Khi chạy trên Docker/VPS UTC, thời gian checkout (07:30 UTC) bị trừ với thời gian checkin (14:00 VN), tạo ra khoảng thời gian âm $(-6.5 \text{ giờ})$. Phép tính `Math.max(0, Duration.between(...))` biến tổng thời lượng thành **0 phút**, khiến hệ thống miễn phí hoặc tính thiếu nghiêm trọng phí gửi xe của khách hàng.
- PR đã chuyển đổi giờ checkout, giờ tạo booking (`now` và `expiresAt = now.plusMinutes(15)`) và các cron job scheduler (`BookingExpirationScheduler`) sang `Asia/Ho_Chi_Minh`.
- Bổ sung tài liệu chuẩn hóa luồng tính phí tại `docs/business-flows/booking-business-time.md`.
- Bổ sung 3 unit test giả lập môi trường UTC host (`TimeZone.setDefault("UTC")` có bọc `try / finally`).
- Bổ sung kiểm thử xác nhận tính bất biến và không lặp của check-in (`successfulCheckInCannotBeRepeatedOrChangeEntryTime`).

Tuy nhiên, **quá trình chuẩn hóa múi giờ chưa đồng bộ triệt để (Partial Migration Bug)**: Cột `expiresAt` đã chuyển sang lưu giờ Việt Nam, nhưng 2 nơi kiểm tra hạn thanh toán cọc (`InternalPaymentConfirmationController.java:32` và `PaymentEventListener.java:71, 84`) vẫn so sánh với `LocalDateTime.now()` của host UTC. Điều này dẫn đến lỗ hổng logic: **cho phép thanh toán cọc cho đơn đã hết hạn suốt 7 tiếng** trên host UTC.

---

## 3. Bảng Tổng Hợp Điểm Phát Hiện (Findings Matrix)

| ID | Tiêu đề | Phân loại | Mức độ (Severity) | Độ tin cậy (Confidence) | Mức ưu tiên (Priority) |
| :---: | :--- | :---: | :---: | :---: | :---: |
| **F-01** | Chưa chuẩn hóa múi giờ Việt Nam tại bộ xác nhận thanh toán cọc (`InternalPaymentConfirmationController` & `PaymentEventListener`) | `correctness` / `data-integrity` | **Major** | **High** | **P2** |
| **F-02** | Thiếu ca kiểm thử giả lập UTC cho luồng xử lý sự kiện thanh toán cọc hết hạn | `testing` | **Minor** | **High** | **P3** |
| **F-03** | Lặp lại chuỗi định danh múi giờ `"Asia/Ho_Chi_Minh"` rải rác thay vì dùng hằng số tập trung | `architecture` / `ai-slop` | **Suggestion** | **High** | **P4** |

---

## 4. Chi Tiết Các Điểm Phát Hiện

### F-01: Chưa chuẩn hóa múi giờ Việt Nam tại bộ xác nhận thanh toán (Major — P2)

- **Vị trí:**
  - `booking-service/src/main/java/com/smartparking/booking/api/controller/InternalPaymentConfirmationController.java#L32`
  - `booking-service/src/main/java/com/smartparking/booking/infrastructure/messaging/PaymentEventListener.java#L71-L74, L84`
- **Nguyên nhân kích hoạt (Trigger):**
  - Khi microservice chạy trên môi trường Linux VPS/Docker có múi giờ mặc định là UTC.
  - Tại `BookingServiceImpl.java#L83`, `expiresAt` được tính theo giờ Việt Nam: `now_VN.plusMinutes(15)` (ví dụ: tạo lúc `14:00 VN`, thì `expiresAt = 14:15 VN`).
- **Hành vi thực tế (Actual Behavior):**
  1. Tại `InternalPaymentConfirmationController.java:32`:
     ```java
     if (booking.getStatus() != BookingStatusEnum.PENDING_PAYMENT || booking.getExpiresAt() == null || !booking.getExpiresAt().isAfter(LocalDateTime.now())) return ApiResponse.ok(false);
     ```
  2. Tại `PaymentEventListener.java:71`:
     ```java
     if (booking.getExpiresAt() == null || !booking.getExpiresAt().isAfter(LocalDateTime.now())) {
         log.error("Paid event arrived after hold expiry; reconciliation required: booking={}", booking.getId());
         return;
     }
     ```
  3. Cả hai nơi đều so sánh `booking.getExpiresAt()` (đang mang giá trị giờ Việt Nam, ví dụ `14:15`) với `LocalDateTime.now()` của host UTC (ví dụ lúc `14:20 VN`, host UTC đang là `07:20 UTC`).
  4. Vì `LocalDateTime` không chứa thông tin offset, biểu thức so sánh thời gian `14:15.isAfter(07:20)` luôn trả về `true`!
  5. Hệ thống nhận định booking **chưa hết hạn** dù thực tế đã quá hạn 5 phút. Trong khoảng thời gian từ phút 15 đến khi scheduler quét hủy ở phút 18 (hoặc nếu scheduler bị trễ/lỗi), webhook thanh toán vẫn được chấp nhận và kích hoạt chuyển sang `PENDING_CHECKIN`.
  6. Ngoài ra, tại `PaymentEventListener.java:84`:
     ```java
     LocalDateTime deadline = booking.getExpectedCheckInTime() != null
             ? booking.getExpectedCheckInTime().plusMinutes(60)
             : LocalDateTime.now().plusHours(2);
     ```
     Nếu `expectedCheckInTime` rơi vào fallback null, deadline được tính bằng `LocalDateTime.now().plusHours(2)` theo giờ UTC (ví dụ `09:20`). Khi `BookingExpirationScheduler` quét bằng `now(Asia/Ho_Chi_Minh)` (`14:20`), nó thấy `09:20 < 14:20` nên lập tức hủy đơn mới thanh toán thành `EXPIRED` (No-show) ngay lập tức!
- **Hành vi kỳ vọng (Expected Behavior):**
  - Mọi thao tác so sánh và tính toán hạn chót liên quan đến `expiresAt` phải sử dụng chung `java.time.ZoneId.of("Asia/Ho_Chi_Minh")`.
- **Đề xuất khắc phục (Suggested Fix):**
  ```java
  // Trong InternalPaymentConfirmationController.java:
  ZoneId zone = java.time.ZoneId.of("Asia/Ho_Chi_Minh");
  if (booking.getStatus() != BookingStatusEnum.PENDING_PAYMENT 
          || booking.getExpiresAt() == null 
          || !booking.getExpiresAt().isAfter(LocalDateTime.now(zone))) {
      return ApiResponse.ok(false);
  }
  
  // Trong PaymentEventListener.java:
  ZoneId zone = java.time.ZoneId.of("Asia/Ho_Chi_Minh");
  if (booking.getExpiresAt() == null || !booking.getExpiresAt().isAfter(LocalDateTime.now(zone))) {
      log.error("Paid event arrived after hold expiry; reconciliation required: booking={}", booking.getId());
      return;
  }
  ...
  LocalDateTime deadline = booking.getExpectedCheckInTime() != null
          ? booking.getExpectedCheckInTime().plusMinutes(60)
          : LocalDateTime.now(zone).plusHours(2);
  ```

---

### F-02: Thiếu ca kiểm thử giả lập UTC cho luồng xác nhận thanh toán (Minor — P3)

- **Vị trí:** `booking-service/src/test/java/com/smartparking/booking/infrastructure/messaging/PaymentEventListenerTest.java`
- **Thực trạng:**
  - `PaymentEventListenerTest.java#L58` hiện đang dùng:
    ```java
    .expiresAt(java.time.LocalDateTime.now().plusMinutes(10))
    ```
    sử dụng giờ mặc định của máy dev/CI, chưa kiểm tra với `TimeZone.setDefault("UTC")` như `CheckoutBusinessTimeTest`.
- **Hành vi kỳ vọng:**
  - Bổ sung test case kiểm tra: Khi host chạy UTC và đơn hàng đã quá hạn 5 phút theo giờ Việt Nam (`expiresAt = now_VN.minusMinutes(5)`), `handlePaymentEvent` phải ghi log cảnh báo quá hạn và **không** chuyển trạng thái sang `PENDING_CHECKIN`.

---

### F-03: Magic string `"Asia/Ho_Chi_Minh"` bị phân mảnh rải rác (Suggestion — P4)

- **Vị trí:**
  - `BookingServiceImpl.java:59`
  - `CheckInOutServiceImpl.java:55, 90`
  - `BookingExpirationScheduler.java:35, 82`
  - `CheckoutBusinessTimeTest.java:32`
- **Thực trạng:**
  - Chuỗi `"Asia/Ho_Chi_Minh"` được hardcode inline lặp lại nhiều lần.
  - Trong khi đó, `com.smartparking.booking.domain.BookingRefundPolicy.java#L18` đã có sẵn:
    ```java
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    ```
- **Đề xuất:**
  - Tái sử dụng hằng số `BookingRefundPolicy.BUSINESS_ZONE` hoặc khai báo một hằng số chung trong domain/policy (ví dụ `BookingTimePolicy.BUSINESS_ZONE`) để tránh magic string và giảm phân mảnh.

---

## 5. Kết Quả Xác Minh Cục Bộ (Local Verification)

Đã checkout trực tiếp commit `a98db87ea992cfc9fe745ff576c30650a97960c4` và thực thi kiểm thử:

1. **Kiểm thử Checkout & Idempotency Check-in:**
   ```powershell
   ./gradlew.bat :booking-service:test --tests "com.smartparking.booking.application.service.CheckoutBusinessTimeTest"
   ```
   👉 **KẾT QUẢ:** `BUILD SUCCESSFUL` (2/2 test passed).
2. **Kiểm thử Booking Creation & Scheduler:**
   ```powershell
   ./gradlew.bat :booking-service:test --tests "com.smartparking.booking.application.service.BookingServiceImplTest" --tests "com.smartparking.booking.infrastructure.scheduler.BookingExpirationSchedulerTest"
   ```
   👉 **KẾT QUẢ:** `BUILD SUCCESSFUL` (tất cả test passed).

---

## 6. Kết Luận & Quyết Định Đề Xuất

Pull Request **#49** đã giải quyết rất đúng trọng tâm vấn đề tính sai phí gửi xe tại thời điểm checkout và quản lý hạn booking trên môi trường UTC.

Tuy nhiên, do lỗi đồng bộ timezone còn sót lại tại **F-01** (`InternalPaymentConfirmationController` & `PaymentEventListener`), việc merge ngay có thể dẫn đến rủi ro chấp nhận các giao dịch cọc quá hạn trên môi trường Production VPS (UTC).

👉 **QUYẾT ĐỊNH: ⚠️ REQUEST CHANGES (Yêu cầu tác giả hoàn tất xử lý F-01 & F-02 trước khi Merge vào `main`).**
