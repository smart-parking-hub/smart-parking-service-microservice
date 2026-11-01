# Kế Hoạch Kiểm Thử & Báo Cáo Lỗi Kỹ Thuật (Test Plan & Bug Report)
## Sprint MVP 1 — Core Smart Parking & VietQR Settlement

- **Dự án**: Smart Truck Parking Hub
- **Sprint**: Sprint MVP 1 — Core Smart Parking & VietQR Settlement
- **Quy mô**: 22 Tasks | 100 Story Points (Fibonacci)
- **Ngày lập**: 01/11/2026
- **Tác giả**: QA Lead & System Architect Team
- **Tài liệu lưu tại**: `smart-parking-service-microservice/docs/tests/SPRINT_MVP1_TEST_PLAN_AND_BUG_REPORT.md`

---

## MỤC LỤC
1. [Mục Tiêu & Phạm Vi Kiểm Thử (Scope)](#1-mục-tiêu--phạm-vi-kiểm-thử-scope)
2. [Chi Tiết Kế Hoạch Kiểm Thử (E2E Test Matrix)](#2-chi-tiết-kế-hoạch-kiểm-thử-e2e-test-matrix)
3. [Báo Cáo Chi Tiết Các Lỗi & Khiếm Khuyết Kỹ Thuật Phát Hiện (Bug Reports)](#3-báo-cáo-chi-tiết-các-lỗi--khiếm-khuyết-kỹ-thuật-phát-hiện-bug-reports)
   - [BUG-01: Bãi đỗ đã có slot nhưng thiếu cấu hình giá (Thiếu ràng buộc nghiệp vụ)](#bug-01-bãi-đỗ-đã-có-slot-nhưng-thiếu-cấu-hình-giá)
   - [BUG-02: Thanh toán PayOS thành công nhưng không có mã QR Check-in & Spam Toast lỗi](#bug-02-thanh-toán-payos-thành-công-nhưng-không-có-mã-qr-check-in--spam-toast-lỗi)
   - [BUG-03: Race Condition giữa Scheduler hủy chỗ (10-15 phút) và thanh toán đến trễ](#bug-03-race-condition-giữa-scheduler-hủy-chỗ-và-thanh-toán)
   - [BUG-04: Lỗ hổng Client gửi số tiền Total Amount và Advance Deposit không được Validate lại](#bug-04-lỗ-hổng-tin-tưởng-client-về-số-tiền-tính-toán)
   - [BUG-05: Lỗi FeignClient gọi Parking-Lot-Service ("Không thể kết nối đến hệ thống bãi đỗ xe")](#bug-05-lỗi-feignclient-gọi-parking-lot-service-khi-đặt-chỗ)
   - [BUG-06: Lỗi lệch Enum loại xe trên bộ lọc tìm kiếm (Chỉ lọc được xe Container)](#bug-06-lỗi-lệch-enum-loại-xe-trên-bộ-lọc-tìm-kiếm-chỉ-lọc-được-xe-container)
4. [Kế Hoạch Khắc Phục & Code Sửa Lỗi Chi Tiết (Actionable Fixes)](#4-kế-hoạch-khắc-phục--code-sửa-lỗi-chi-tiết)
5. [Quy Trình Kiểm Thử Hồi Quy (Regression Testing Checklist)](#5-quy-trình-kiểm-thử-hồi-quy-regression-testing-checklist)

---

## 1. Mục Tiêu & Phạm Vi Kiểm Thử (Scope)

Sprint MVP 1 tập trung nghiệm thu luồng nghiệp vụ cốt lõi sống còn: **Tài xế tìm bãi xe -> Khóa giữ slot an toàn -> Thanh toán VietQR PayOS -> Nhận vé QR điện tử -> Check-in qua cổng bãi xe**.

### 7 Chặng End-to-End User Journey cần nghiệm thu:
1. **[Đăng ký / Đăng nhập]** (SP-S1-07, SP-S1-08): JWT Authentication, RBAC (DRIVER, MERCHANT, ADMIN).
2. **[Tìm kiếm Bãi xe GPS & Bản đồ Leaflet]** (SP-S1-10, SP-S1-11): Lọc bán kính GPS, tính khoảng cách Haversine, lọc theo loại phương tiện.
3. **[Xem Bảng giá & Chi tiết Slot đỗ]** (SP-S1-09, SP-S1-12): Hiển thị slot trống thực tế và đơn giá giờ theo loại xe.
4. **[Bấm Đặt chỗ & Khóa Slot 10-15 phút]** (SP-DON-01, SP-DON-02, SP-DON-04): Redis Distributed Lock chống overselling, Transactional Outbox.
5. **[Quét mã VietQR PayOS & Bắt Webhook]** (SP-DON-05, SP-DON-06, SP-DON-07): Tạo Payment Link PayOS, xác thực chữ ký HMAC-SHA256, xử lý Webhook.
6. **[Kafka tự động xác nhận đơn hàng]** (SP-DON-08): Chuyển trạng thái booking sang `PENDING_CHECKIN`.
7. **[Nhận Vé điện tử & Quét QR Check-in tại Cổng]** (SP-DON-09, SP-DON-10): Sinh QR code từ `bookingCode`, bảo vệ cửa sổ check-in (tối đa trước 30 phút).

---

## 2. Chi Tiết Kế Hoạch Kiểm Thử (E2E Test Matrix)

| Mã Test Case | Hạng mục kiểm thử | Kịch bản chi tiết | Kết quả mong đợi | Mức độ ưu tiên |
| :--- | :--- | :--- | :--- | :--- |
| **TC-01** | Tìm kiếm bãi xe | Lọc bãi xe theo GPS và loại xe tải `CONTAINER` | Chỉ hiển thị bãi xe có slot `CONTAINER` khả dụng kèm giá giờ chính xác | P0 |
| **TC-02** | Ràng buộc Giá & Slot | Chủ bãi tạo slot cho loại xe nhưng chưa nhập bảng giá | Hệ thống chặn không cho phép kích hoạt/duyệt bãi nếu chưa có giá | **P0 (PASSED - ĐÃ FIX)** |
| **TC-03** | Tranh chấp Slot (Concurrency) | 2 tài xế cùng bấm đặt 1 slot cuối cùng tại cùng 1 mili-giây | Chỉ 1 xe giữ chỗ thành công; xe thứ hai nhận thông báo hết chỗ (409 Conflict) | P0 |
| **TC-04** | Thanh toán VietQR PayOS | Quét mã PayOS bằng app ngân hàng thực tế | Webhook PayOS gửi về hệ thống, xác thực chữ ký HMAC-SHA256 hợp lệ | P0 |
| **TC-05** | Đồng bộ & Sinh mã QR Check-in | Thanh toán cọc thành công, quay lại trang Đặt chỗ của tôi | Trạng thái chuyển thành `PENDING_CHECKIN` và **hiển thị ngay mã QR Check-in** | **P0 (PASSED - ĐÃ FIX)** |
| **TC-06** | Hủy tự động khi hết hạn (Scheduler) | Tài xế đặt chỗ nhưng không thanh toán cọc sau 10-15 phút | Scheduler tự động chuyển booking sang `EXPIRED`, hoàn trả lại slot khả dụng | P0 |
| **TC-07** | Quét QR Check-in tại cổng | Merchant mở màn hình Quét QR và scan mã `bookingCode` | Check-in thành công nếu trong khung giờ cho phép (trước tối đa 30 phút); chuyển sang `PARKING` | P0 |
| **TC-08** | Chặn Check-in sai bãi xe | Tài xế mang mã QR của Bãi A đến quét tại cổng Bãi B | Hệ thống từ chối (HTTP 400: Mã đặt chỗ thuộc về bãi khác) | P1 |

---

## 3. Báo Cáo Chi Tiết Các Lỗi & Khiếm Khuyết Kỹ Thuật Phát Hiện (Bug Reports)

---

### BUG-01: Bãi đỗ đã có slot nhưng thiếu cấu hình giá
- **Độ nghiêm trọng**: **CRITICAL (P0)** - Vi phạm tính toàn vẹn nghiệp vụ (Business Rule Invariant).
- **Vị trí phát hiện**:
  - Backend: `parking-lot-service/src/main/java/com/smartparking/parkinglot/application/service/impl/SlotServiceImpl.java`
  - Backend: `parking-lot-service/src/main/java/com/smartparking/parkinglot/application/service/impl/ParkingLotServiceImpl.java`
  - Frontend: `smart-parking-app/src/features/bookings/components/BookingFlowModal.tsx`
  - Frontend: `smart-parking-app/src/features/merchant/pricing/pages/MerchantPricingPage.tsx`

#### 1. Mô tả hiện tượng (Symptom):
- Chủ bãi xe (Merchant) có thể cấu hình số lượng slot cho loại xe (ví dụ: `CONTAINER`, `HEAVY_TRUCK`) trong `SlotServiceImpl.configureSlots()` nhưng không bị bắt buộc phải cấu hình `PricingRule` (giá theo giờ) cho loại xe đó.
- Admin khi phê duyệt bãi xe (`ParkingLotServiceImpl.approveParkingLot()`) không có bước xác thực kiểm tra: **"Mọi loại xe có slot > 0 đều bắt buộc phải có bảng giá tương ứng"**.
- Khi tài xế tìm kiếm và bấm đặt chỗ bãi xe này:
  - Frontend tính toán tiền:
    ```typescript
    const priceRule = lot.pricingRules.find((rule) => rule.vehicleType === vehicle?.vehicleType)
    const total = hours * (priceRule?.basePricePerHour ?? 0) // total ra 0 VND!
    ```
  - Backend `SlotServiceImpl.holdSlot()` khi thấy thiếu cấu hình giá thì tự ý fallback ngầm:
    ```java
    BigDecimal basePrice = pricingRuleRepository.findByParkingLotIdAndVehicleType(parkingLotId, vehicleType)
            .map(PricingRule::getBasePricePerHour)
            .orElse(BigDecimal.valueOf(20000)); // Hardcoded 20.000đ ngầm!
    ```

#### 2. Tác động tiêu cực:
- Thất thoát doanh thu hoặc tranh chấp giá với chủ bãi xe.
- Tài xế nhìn thấy giá 0đ trên ứng dụng nhưng khi tạo đơn lại bị tính tiền cọc mặc định 50.000đ hoặc 20.000đ/giờ.

---

### BUG-02: Thanh toán PayOS thành công nhưng không có mã QR Check-in & Spam Toast lỗi
- **Độ nghiêm trọng**: **BLOCKER (P0)** - Chặn người dùng hoàn thành User Journey.
- **Vị trí phát hiện**:
  - Backend: `payment-service/src/main/java/com/smartparking/payment/application/service/impl/PaymentServiceImpl.java` (Dòng 184 - 193)
  - Backend: `booking-service/src/main/java/com/smartparking/booking/infrastructure/messaging/PaymentEventListener.java`
  - Frontend: `smart-parking-app/src/features/bookings/pages/MyBookingsPage.tsx` (Dòng 64 - 70)
  - Frontend: `smart-parking-app/src/features/payments/pages/PaymentReturnPage.tsx`
- **Bằng chứng thực tế**: Hình ảnh người dùng cung cấp ghi nhận hàng loạt thông báo lỗi đỏ xếp chồng:
  - 🔴 *"Không tìm thấy giao dịch thanh toán để đồng bộ"*
  - 🔴 *"Booking không còn chờ thanh toán cọc"*

#### 1. Nguyên nhân gốc rễ 1 — Lọc sai trạng thái khi đồng bộ (Filter Bug trong `PaymentServiceImpl`):
Trong `PaymentServiceImpl.java`:
```java
@Override
@Transactional
public PaymentDto syncPayOSPayment(Long driverUserId, Long orderCode, Long bookingId, String paymentLinkId) {
    Payment payment = null;
    if (orderCode != null) {
        payment = paymentRepository.findByOrderCode(orderCode).orElse(null);
    }
    if (payment == null && bookingId != null) {
        payment = paymentRepository.findByBookingId(bookingId).stream()
                .filter(p -> p.getStatus() == PaymentStatusEnum.PENDING) // ❌ LỖI NGHIÊM TRỌNG TẠI ĐÂY!
                .findFirst()
                .orElse(null);
    }

    if (payment == null) {
        throw new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy giao dịch thanh toán để đồng bộ");
    }

    if (payment.getStatus() == PaymentStatusEnum.SUCCESS) {
        outboxPublisherService.publishPaymentCompleted(payment);
        return PaymentDto.fromEntity(payment);
    }
    ...
```
- **Phân tích lỗi**:
  - Khi người dùng đã thanh toán thành công (qua Webhook hoặc redirect PayOS lần đầu), `payment.status` trong database đã là `SUCCESS`.
  - Khi frontend gọi API `/api/v1/payments/sync` với tham số `bookingId`: Phương thức `findByBookingId(bookingId)` tìm thấy bản ghi thanh toán, nhưng đoạn code `.filter(p -> p.getStatus() == PaymentStatusEnum.PENDING)` đã **loại bỏ bản ghi đã SUCCESS**!
  - Kết quả: `payment` bị gán bằng `null`, dẫn đến ném lỗi `AppException(ErrorCode.NOT_FOUND, "Không tìm thấy giao dịch thanh toán để đồng bộ")`.
  - Đoạn code xử lý `if (payment.getStatus() == PaymentStatusEnum.SUCCESS)` ở dòng 195 trở nên vô dụng (Dead Code) khi tra cứu theo `bookingId`.

#### 2. Nguyên nhân gốc rễ 2 — Spam Toast trên giao diện (`MyBookingsPage.tsx`):
Trong `MyBookingsPage.tsx`:
```typescript
const loadBookings = useCallback(async (preferSelectedId?: number) => {
  setLoading(true)
  try {
    let data = await getMyBookings()
    const stuck = data.content.filter(
      (item) => item.status === 'PENDING_PAYMENT' || item.status === 'AWAITING_FINAL_PAYMENT',
    )
    if (stuck.length > 0) {
      // ❌ GỌI ĐỒNG BỘ ĐỒNG LOẠT TẤT CẢ BOOKING CHƯA THANH TOÁN
      await Promise.allSettled(stuck.map((item) => syncPayOSPayment({ bookingId: item.id })))
      data = await getMyBookings()
    }
    ...
```
- **Phân tích lỗi**: Mỗi khi người dùng vào trang *Đặt chỗ của tôi*, hệ thống tự động quét tất cả booking đang ở trạng thái `PENDING_PAYMENT` (kể cả các đơn cũ đã quá hạn hoặc đơn chưa từng bấm thanh toán trên PayOS).
- Với mỗi đơn đó, backend ném ra lỗi `Không tìm thấy giao dịch...` hoặc `Booking không còn chờ thanh toán cọc`.
- Frontend bắt lỗi và hiển thị toast đỏ cho từng đơn, dẫn đến hiện tượng 7-10 toast lỗi hiển thị xếp chồng che kín màn hình như trong ảnh.

#### 3. Nguyên nhân gốc rễ 3 — Không có mã QR Check-in sau khi thanh toán:
- Frontend chỉ hiển thị QR code khi `booking.status` là `PENDING_CHECKIN`, `PARKING`, hoặc `OVERTIME`:
  ```tsx
  {['PENDING_CHECKIN', 'PARKING', 'OVERTIME'].includes(selected.status) ? (
    <QRCodeSVG value={selected.bookingCode} size={180} />
  ) : (
    <div className="qr-placeholder"><span>Chờ thanh toán cọc để kích hoạt QR</span></div>
  )}
  ```
- Việc chuyển từ `PENDING_PAYMENT` sang `PENDING_CHECKIN` **hoàn toàn phụ thuộc bất đồng bộ vào Kafka**:
  1. `payment-service` ghi vào bảng `outbox_events`.
  2. `OutboxRelayScheduler` (quét định kỳ mỗi 5 giây) đọc ra bắn vào Kafka topic `payment.events`.
  3. `booking-service` lắng nghe topic và cập nhật `booking.setStatus(PENDING_CHECKIN)`.
- **Nếu Kafka bị nghẽn, outbox relay chưa kịp chạy, hoặc môi trường dev/local không chạy Kafka container**:
  - `booking-service` không bao giờ nhận được event!
  - Booking vẫn mãi mãi ở trạng thái `PENDING_PAYMENT`.
  - Tài xế đã bị trừ tiền ngân hàng nhưng không thấy mã QR check-in xuất hiện trên ứng dụng!

---

### BUG-03: Race Condition giữa Scheduler hủy chỗ và thanh toán
- **Độ nghiêm trọng**: **HIGH (P1)**
- **Vị trí**: `booking-service/src/main/java/com/smartparking/booking/infrastructure/scheduler/BookingExpirationScheduler.java`

#### Mô tả:
- `BookingExpirationScheduler` quét mỗi 60 giây và tự động chuyển các booking quá 15 phút chưa cọc sang `EXPIRED`, đồng thời gọi `parkingLotClient.releaseSlot(...)` để nhả chỗ.
- Nếu tài xế quét VietQR ở phút thứ 14:55: Tiền đã trừ từ tài khoản ngân hàng, nhưng webhook PayOS hoặc Kafka event đến trễ ở phút 15:05 -> Booking đã bị Scheduler đổi thành `EXPIRED` và slot đã bị cấp cho tài xế khác trong hàng đợi!
- Khi webhook thanh toán đến, `booking.getStatus() != PENDING_PAYMENT` -> Dẫn đến lỗi *"Booking không còn chờ thanh toán cọc"* và tài xế bị mất tiền cọc mà không có chỗ đỗ.

---

### BUG-04: Lỗ hổng Client gửi số tiền Total Amount và Advance Deposit không được Validate lại
- **Độ nghiêm trọng**: **HIGH (P1)** - Lỗ hổng toàn vẹn tài chính.
- **Vị trí**: `booking-service/src/main/java/com/smartparking/booking/application/service/impl/BookingServiceImpl.java` (Dòng 49 - 74)

#### Mô tả:
- `CreateBookingRequest` cho phép client gửi trường `totalAmount` và `advanceDeposit`.
- `BookingServiceImpl.createBooking()` sử dụng trực tiếp giá trị do Client gửi lên:
  ```java
  if (request.getAdvanceDeposit() != null && request.getAdvanceDeposit().compareTo(BigDecimal.ZERO) > 0) {
      deposit = request.getAdvanceDeposit();
  }
  ```
- Nếu kẻ xấu can thiệp request HTTP, sửa `totalAmount = 1000` VNĐ và `advanceDeposit = 200` VNĐ, hệ thống vẫn chấp nhận và tạo link PayOS cọc 200 VNĐ cho thời lượng đỗ xe cả tuần!
- **Yêu cầu chuẩn**: Backend `booking-service` phải tự tính toán lại `totalAmount` và `advanceDeposit` dựa trên: `(Thời gian ra - Thời gian vào) * BasePrice` lấy từ `parking-lot-service`.

---

### BUG-05: Lỗi FeignClient gọi Parking-Lot-Service khi đặt chỗ ("Không thể kết nối đến hệ thống bãi đỗ xe")
- **Độ nghiêm trọng**: **BLOCKER (P0)** - Chặn tài xế tạo lượt đặt chỗ ở bước 4 của User Journey.
- **Vị trí phát hiện**:
  - Backend: `booking-service/src/main/java/com/smartparking/booking/infrastructure/client/ParkingLotClient.java` (Dòng 11)
  - Backend: `booking-service/src/main/java/com/smartparking/booking/application/service/impl/BookingServiceImpl.java` (Dòng 101 - 103)
  - Backend Config: `booking-service/src/main/resources/application.yml` (Dòng 48 - 50)
- **Bằng chứng thực tế**: Hình ảnh người dùng chụp khi bấm **"Giữ chỗ & thanh toán"**:
  - 🔴 Toast thông báo: *"Không thể kết nối đến hệ thống bãi đỗ xe, vui lòng thử lại sau!"*

#### 1. Mô tả và Phân tích Nguyên nhân:
- Khi tài xế bấm nút **"Giữ chỗ & thanh toán"** trên popup Đặt chỗ nhanh, Frontend gửi `POST /api/v1/bookings`.
- `BookingServiceImpl.createBooking()` thực hiện lưu booking tạm và gọi sang `parking-lot-service` để giữ chỗ:
  ```java
  ApiResponse<SlotHoldResponse> holdRes = parkingLotClient.holdSlot(request.getParkingLotId(), holdReq);
  ```
- Tuy nhiên trong `ParkingLotClient.java`:
  ```java
  @FeignClient(name = "parking-lot-service") // ❌ THIẾU THUỘC TÍNH URL
  public interface ParkingLotClient {
  ```
  Trong khi đó, ở `application.yml` của `booking-service`:
  ```yaml
  eureka:
    client:
      enabled: ${EUREKA_CLIENT_ENABLED:false} # Mặc định đang TẮT Eureka Client (false)
  services:
    parking-lot:
      url: ${PARKING_LOT_SERVICE_URL:http://localhost:8082}
  ```
- **Hậu quả**: Do `EUREKA_CLIENT_ENABLED: false` và `@FeignClient` không được gán thuộc tính `url = "${services.parking-lot.url:http://localhost:8082}"`:
  1. OpenFeign không thể phân giải tên service `parking-lot-service` qua Eureka.
  2. OpenFeign gửi HTTP request trực tiếp tới hostname `http://parking-lot-service/` dẫn đến lỗi `UnknownHostException` / `FeignException`.
  3. Hoặc nếu chạy môi trường local, service `parking-lot-service` (cổng 8082) chưa được khởi chạy hoặc bị tắt.
- `BookingServiceImpl` bắt `feign.FeignException` và ném ra:
  ```java
  throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Không thể kết nối đến hệ thống bãi đỗ xe, vui lòng thử lại sau!");
  ```
  khiến toast đỏ xuất hiện trên giao diện người dùng như trong ảnh.

---

### BUG-06: Lỗi lệch Enum loại xe trên bộ lọc tìm kiếm (Chỉ lọc được xe Container)
- **Độ nghiêm trọng**: **HIGH (P1)** - Chặn người dùng tìm kiếm bãi đỗ cho các loại xe phổ biến (xe con, xe tải nhẹ, xe tải nặng).
- **Vị trí phát hiện**:
  - Frontend: `smart-parking-app/src/features/parking-search/pages/SearchParkingPage.tsx` (Dòng 263 - 279)
  - Backend: `parking-lot-service/src/main/java/com/smartparking/parkinglot/api/controller/ParkingLotController.java` (Dòng 135)
  - Common Library: `common-library/src/main/java/com/smartparking/common/enums/VehicleTypeEnum.java`
- **Bằng chứng thực tế**: Người dùng phản ánh khi mở menu dropdown *"Tìm theo loại xe khác"* trên trang Tìm bãi đỗ:
  - Chọn *Ô tô 4-7 chỗ*, *Xe tải nhẹ (<3.5T)*, hoặc *Xe tải nặng (>3.5T)* thì không lọc được bãi xe nào.
  - Chỉ khi chọn *Xe Container* thì hệ thống mới tìm và hiển thị được bãi đỗ.

#### 1. Mô tả và Phân tích Nguyên nhân:
- Trong `VehicleTypeEnum.java` (Backend) và `vehicleTypes.ts` (Frontend), các giá trị Enum hợp lệ là:
  - `SEDAN_SUV` (Ô tô con / SUV)
  - `LIGHT_TRUCK` (Xe tải nhẹ)
  - `HEAVY_TRUCK` (Xe tải nặng)
  - `CONTAINER` (Xe Container)
  - `BUS_COACH` (Xe khách / xe buýt)
- Tuy nhiên, trong component `SearchParkingPage.tsx`, các option của dropdown lại bị gán giá trị **hardcoded sai lệch**:
  ```tsx
  <optgroup label="Tìm theo loại xe khác">
    <option value="type:CAR_4_7">Ô tô 4-7 chỗ</option>         {/* ❌ Backend là SEDAN_SUV */}
    <option value="type:TRUCK_LIGHT">Xe tải nhẹ (<3.5T)</option>{/* ❌ Backend là LIGHT_TRUCK */}
    <option value="type:TRUCK_HEAVY">Xe tải nặng (>3.5T)</option>{/* ❌ Backend là HEAVY_TRUCK */}
    <option value="type:CONTAINER">Xe Container</option>         {/* ✅ Chỉ có CONTAINER là khớp */}
  </optgroup>
  ```
- **Hậu quả**:
  - Khi người dùng chọn *Ô tô 4-7 chỗ*, frontend gửi API `GET /api/v1/parking-lots/search?vehicleType=CAR_4_7`.
  - Backend Spring Boot cố chuyển đổi chuỗi `"CAR_4_7"` sang `VehicleTypeEnum` thất bại -> Ném `MethodArgumentTypeMismatchException` (HTTP 400 Bad Request).
  - Tương tự với `TRUCK_LIGHT` và `TRUCK_HEAVY`.
  - Chỉ duy nhất `"CONTAINER"` khớp chính xác với `VehicleTypeEnum.CONTAINER` nên chỉ có xe Container tìm kiếm được bãi!

---

## 4. Kế Hoạch Khắc Phục & Code Sửa Lỗi Chi Tiết

### Khắc phục BUG-01: Bắt buộc cấu hình Giá khi có Slot
#### 1. Thêm Validation trong `parking-lot-service` (`SlotServiceImpl.java` & `ParkingLotServiceImpl.java`):
- Khi duyệt bãi xe (`approveParkingLot`):
  ```java
  List<Slot> slots = slotRepository.findByParkingLotId(lot.getId());
  List<PricingRule> rules = pricingRuleRepository.findByParkingLotId(lot.getId());
  Set<VehicleTypeEnum> pricedVehicles = rules.stream().map(PricingRule::getVehicleType).collect(Collectors.toSet());

  for (Slot slot : slots) {
      if (slot.getTotalCapacity() > 0 && !pricedVehicles.contains(slot.getVehicleType())) {
          throw new AppException(ErrorCode.BAD_REQUEST, 
              "Bãi đỗ có cấu hình slot cho loại xe " + slot.getVehicleType() + " nhưng chưa thiết lập bảng giá!");
      }
  }
  ```
- Trong `SlotServiceImpl.holdSlot`: Không dùng fallback 20.000đ ngầm. Nếu thiếu bảng giá, ném ngay `AppException(ErrorCode.BAD_REQUEST, "Loại xe này chưa có bảng giá khả dụng tại bãi đỗ")`.

---

### Khắc phục BUG-02: Sửa lỗi Đồng bộ PayOS & Kích hoạt mã QR Check-in tức thì
#### 1. Sửa `PaymentServiceImpl.syncPayOSPayment`:
Bỏ điều kiện lọc cứng `PENDING`, ưu tiên tìm bản ghi thanh toán mới nhất của booking:
```java
if (payment == null && bookingId != null) {
    payment = paymentRepository.findByBookingId(bookingId).stream()
            .sorted(Comparator.comparing(Payment::getId).reversed())
            .findFirst()
            .orElse(null);
}
```
Nếu `payment.getStatus() == PaymentStatusEnum.SUCCESS`, trả về ngay thông tin thành công và kích hoạt outbox / event bổ sung nếu cần.

#### 2. Thêm Fast-Path Sync trực tiếp giữa `payment-service` và `booking-service`:
Để không phụ thuộc 100% vào độ trễ Kafka:
- Khi `syncPayOSPayment` hoặc `confirmDemoDeposit` xác nhận thành công:
  Ngoài việc ghi Outbox Kafka, cung cấp endpoint nội bộ (FeignClient) gọi sang `booking-service` để cập nhật trạng thái `PENDING_CHECKIN` ngay lập tức trong vòng 200ms.
- Nhờ đó, tài xế quay lại ứng dụng sẽ thấy mã QR Check-in ngay tức khắc mà không bị độ trễ.

#### 3. Loại bỏ Auto-Sync Spam trên Frontend (`MyBookingsPage.tsx`):
- Không tự ý chạy `Promise.allSettled` gọi sync ngầm cho toàn bộ booking khi tải trang.
- Chỉ hiển thị nút **"Đồng bộ PayOS"** hoặc **"Kiểm tra thanh toán"** thủ công cho booking mà tài xế đang chọn xem chi tiết.
- Bọc `try...catch` trong các hàm sync để không bắn thông báo lỗi nếu đơn đó người dùng chưa từng tạo liên kết thanh toán.

---

### Khắc phục BUG-03: Xử lý Race Condition khi hết hạn giữ chỗ
- Thêm **Grace Period (Thời gian gia hạn)** 5 phút trong `BookingExpirationScheduler`: Chỉ hủy các booking quá hạn mà không có bất kỳ giao dịch thanh toán nào đang ở trạng thái `PROCESSING` hoặc được tạo trong vòng 5 phút gần nhất.
- Khi Webhook PayOS báo thanh toán thành công cho một booking đã lỡ bị `EXPIRED`: Tự động kích hoạt cơ chế hoàn tiền (Auto-Refund) hoặc tạo Ticket hỗ trợ cho tài xế thay vì báo lỗi.

---

### Khắc phục BUG-04: Tính toán lại giá cọc tại Backend
- Trong `BookingServiceImpl.createBooking`:
  Lấy giá giờ `baseHourlyPrice` từ `holdRes.getData().getBaseHourlyPrice()`.
  Tính lại:
  $$\text{durationHours} = \lceil (\text{checkOut} - \text{checkIn}) / 1 \text{ hour} \rceil$$
  $$\text{expectedTotal} = \text{durationHours} \times \text{baseHourlyPrice}$$
  $$\text{requiredDeposit} = \text{expectedTotal} \times 20\%$$
  Ghi đè giá trị này vào entity `Booking` thay vì tin tưởng số tiền từ Client.

---

### Khắc phục BUG-05: Sửa FeignClient ParkingLotClient
Tại `booking-service/src/main/java/com/smartparking/booking/infrastructure/client/ParkingLotClient.java`:
```java
@FeignClient(name = "parking-lot-service", url = "${services.parking-lot.url:http://localhost:8082}")
public interface ParkingLotClient {
    ...
}
```
Đồng thời đảm bảo `parking-lot-service` đang chạy trên cổng `8082`.

---

### Khắc phục BUG-06: Sửa Giá Trị Dropdown Loại Xe Trên SearchParkingPage.tsx
Tại `smart-parking-app/src/features/parking-search/pages/SearchParkingPage.tsx`:
Cập nhật lại giá trị các option khớp chính xác với `VehicleTypeEnum`:
```tsx
<optgroup label="Tìm theo loại xe khác">
  <option value="type:SEDAN_SUV">Ô tô 4-7 chỗ (Sedan / SUV)</option>
  <option value="type:LIGHT_TRUCK">Xe tải nhẹ (&lt;3.5T)</option>
  <option value="type:HEAVY_TRUCK">Xe tải nặng (&gt;3.5T)</option>
  <option value="type:CONTAINER">Xe Container</option>
  <option value="type:BUS_COACH">Xe khách / Xe buýt</option>
</optgroup>
```
và thẻ `<select>` dự phòng:
```tsx
  <option value="SEDAN_SUV">🚗 Ô tô 4-7 chỗ</option>
  <option value="LIGHT_TRUCK">🚚 Xe tải nhẹ (&lt;3.5T)</option>
  <option value="HEAVY_TRUCK">🚛 Xe tải nặng (&gt;3.5T)</option>
  <option value="CONTAINER">🚜 Xe Container</option>
  <option value="BUS_COACH">🚌 Xe khách / Xe buýt</option>
```

---


---

### BUG-07: Lỗi UI/UX trang Đặt chỗ của tôi (Panel chi tiết che thanh lọc & làm bóp méo bảng booking)
- **Độ nghiêm trọng**: **MEDIUM - HIGH (P1/P2)** - Ảnh hưởng trực tiếp đến trải nghiệm xem lịch sử, check-in và tìm kiếm lượt đặt chỗ của tài xế.
- **Vị trí phát hiện**:
  - Frontend: `smart-parking-app/src/features/bookings/pages/MyBookingsPage.tsx`
  - Frontend CSS: `smart-parking-app/src/index.css` (khu vực `.my-bookings-page`, `.bookings-workspace`, `.booking-detail-panel`)
- **Bằng chứng thực tế**: Hình ảnh người dùng chụp màn hình trang `localhost:5173/bookings`:
  - Panel chi tiết nhô lên trên cao, đè lên thanh tìm kiếm và bộ lọc trạng thái.
  - Bảng booking bị co dúm, mất cột hiển thị thông tin thời gian và loại xe.

#### 1. Mô tả và Phân tích Nguyên nhân:
- **Cơ chế lỗi cũ**:
  ```css
  .bookings-workspace.detail-open { padding-right: min(38vw, 440px); }
  .booking-detail-panel {
    position: absolute;
    z-index: 5;
    top: -135px; /* ❌ Thủ phạm chính: kéo ngược panel 135px lên trên đỉnh đè lên .booking-list-filters */
    right: calc((100vw - min(100vw - 40px, 1200px)) / -2); /* ❌ Offset âm không ổn định khi resize */
    height: calc(100% + 173px); /* ❌ Chiều cao magic number */
  }
  ```
- **Hậu quả**:
  1. Khi người dùng bấm vào một booking để xem chi tiết, panel chi tiết bị kéo ngược lên trên `top: -135px`, đè thẳng lên thanh `.booking-list-filters`, che khuất ô tìm kiếm và dropdown chọn trạng thái.
  2. Dùng `padding-right` thay vì CSS Grid khiến bảng `.booking-table` bị ép nghẹt, co rút thông tin, mất 2 cột Thời gian và Loại xe.
  3. Chiều cao `calc(100% + 173px)` khiến panel bị phụ thuộc vào số lượng dòng của bảng master list: nếu danh sách chỉ có 1 booking thì panel bị cắt cụt, nếu danh sách dài thì panel kéo dài bất hợp lý.

#### 2. Giải pháp Đã Triển Khai:
1. **Chuyển sang kiến trúc Layout CSS Grid 2 cột chuẩn Enterprise Master-Detail**:
   - Khi chưa chọn booking: `.bookings-workspace { display: grid; grid-template-columns: 1fr; }`
   - Khi mở chi tiết: `.bookings-workspace.detail-open { display: grid; grid-template-columns: 1fr 420px; gap: 24px; align-items: start; }`
   - Bỏ toàn bộ `position: absolute; top: -135px; right: ...` và `padding-right`.
2. **Bảo vệ thanh tìm kiếm và lọc (`.booking-list-filters`)**:
   - Nằm độc lập 100% phía trên grid workspace, luôn hiển thị đầy đủ, không bao giờ bị đè lấn.
3. **Cải tiến Panel Chi Tiết (`.booking-detail-panel`)**:
   - Áp dụng `position: sticky; top: 84px;` để panel luôn nằm trong tầm nhìn khi cuộn danh sách booking dài.
   - Thêm nút đóng [X] mượt mà, người dùng bấm đóng sẽ tự động mở rộng bảng booking toàn màn hình.
   - Trình bày thông tin vé đỗ xe sạch sẽ: Trạng thái badge, QR Code check-in, Thông tin địa điểm, Lịch trình, Chi phí, Nút thanh toán VietQR / PayOS và Chỉ đường.
4. **Tối ưu trên Tablet / Mobile (`@media (max-width: 1080px)`)**:
   - Tự động chuyển thành Drawer Side-sheet trượt mượt mà từ phải sang kèm backdrop mờ, không làm bóp méo bảng booking.

## 5. Quy Trình Kiểm Thử Hồi Quy (Regression Testing Checklist)

Trước khi đóng Sprint MVP 1, đội ngũ QA và Dev bắt buộc chạy kiểm thử hồi quy theo các bước sau:

- [ ] **Bước 1**: Tạo một bãi đỗ xe mới với slot cho xe tải 10 tấn (`HEAVY_TRUCK`), cố tình không cấu hình giá. Xác nhận Admin bị chặn không thể duyệt bãi.
- [ ] **Bước 2**: Nhập bảng giá hợp lệ cho `HEAVY_TRUCK`, duyệt bãi xe thành công.
- [ ] **Bước 3**: Tài xế tìm kiếm bãi xe trên bản đồ Leaflet, kiểm tra giá giờ hiển thị đúng với giá đã cấu hình (không bị hiển thị 0đ hoặc NaN).
- [ ] **Bước 4**: Tạo đặt chỗ giữ slot. Kiểm tra Redis key giữ slot và kiểm tra thời hạn giữ slot 15 phút.
- [ ] **Bước 5**: Quét VietQR PayOS (hoặc thanh toán thử nghiệm). Kiểm tra Webhook HMAC-SHA256 hợp lệ.
- [ ] **Bước 6**: Kiểm tra tài khoản tài xế: Booking lập tức chuyển sang `PENDING_CHECKIN` và **mã QR Check-in xuất hiện rõ ràng, hợp lệ**.
- [ ] **Bước 7**: Vào trang *Đặt chỗ của tôi*, xác nhận không còn bất kỳ toast lỗi đỏ spam nào xuất hiện.
- [ ] **Bước 8**: Cổng bãi xe dùng tài khoản Merchant mở trang quét QR, quét mã check-in và xác nhận xe vào bãi thành công.

---
*Tài liệu được lưu trữ tự động trong hệ thống tài liệu kiểm thử của dự án SmartParkingHub.*



---

## 6. Bảng Tổng Hợp Trạng Thái & Hướng Dẫn Kiểm Tra Từng Bug (Test Guide)

| Mã Bug | Trạng thái | File đã sửa | Tóm tắt giải pháp kỹ thuật |
| :--- | :--- | :--- | :--- |
| **BUG-01** | ✅ **RESOLVED** | `ParkingLotServiceImpl.java`, `SlotServiceImpl.java` | Bắt buộc mọi loại xe có slot phải có bảng giá khi Admin duyệt bãi; bỏ fallback 20k ngầm. |
| **BUG-02** | ✅ **RESOLVED** | `PaymentServiceImpl.java`, `MyBookingsPage.tsx` | Bỏ lọc cứng `PENDING`, hỗ trợ sync khi đã `SUCCESS`; xóa auto-sync spam toast trên UI. |
| **BUG-03** | ✅ **RESOLVED** | `BookingExpirationScheduler.java` | Thêm 3 phút Grace Period tránh race condition hủy chỗ khi webhook PayOS đến trễ. |
| **BUG-04** | ✅ **RESOLVED** | `BookingServiceImpl.java` | Backend tự tính lại `totalAmount` và `advanceDeposit` từ `basePricePerHour` & thời lượng. |
| **BUG-05** | ✅ **RESOLVED** | `ParkingLotClient.java` | Bổ sung `url = "${services.parking-lot.url:http://localhost:8082}"` vào FeignClient. |
| **BUG-06** | ✅ **RESOLVED** | `SearchParkingPage.tsx` | Đồng bộ Enum: `SEDAN_SUV`, `LIGHT_TRUCK`, `HEAVY_TRUCK`, `CONTAINER` trên frontend. |
| **BUG-07** | ✅ **RESOLVED** | `MyBookingsPage.tsx`, `index.css` | Tái cấu trúc CSS Grid Master-Detail, bảo vệ thanh tìm kiếm & panel vé đỗ xe responsive. |

---

### Hướng Dẫn Các Bước Kiểm Tra Lại (Step-by-Step Verification)

#### 🔹 Kiểm tra BUG-01: Ràng buộc Bảng giá khi có Slot
1. Dùng tài khoản Merchant vào màn hình quản lý bãi đỗ, cấu hình 5 slot cho `CONTAINER` nhưng không tạo giá cho `CONTAINER`.
2. Dùng tài khoản Admin duyệt bãi: Hệ thống ném lỗi `HTTP 400`: *"Bãi đỗ có cấu hình slot cho loại xe CONTAINER nhưng chưa thiết lập bảng giá tương ứng!"*.
3. Thêm bảng giá giờ cho `CONTAINER` và duyệt lại: Bãi xe được duyệt thành công sang trạng thái `APPROVED`.

#### 🔹 Kiểm tra BUG-02: Không còn Spam Toast & Nhận mã QR Check-in ngay
1. Vào trang *Đặt chỗ của tôi* (`http://localhost:5173/bookings`): Xác nhận tải trang êm ái, **không còn xuất hiện bão toast lỗi đỏ xếp tầng**.
2. Chọn đơn đặt chỗ đang chờ thanh toán, bấm **Thanh toán demo** hoặc thanh toán PayOS.
3. Ngay sau khi thanh toán: Booking chuyển sang `PENDING_CHECKIN` và **mã QR Check-in xuất hiện rõ ràng, hợp lệ**.

#### 🔹 Kiểm tra BUG-03: Chống Race Condition khi thanh toán sát giờ
1. Tạo đặt chỗ mới (thời hạn giữ slot 15 phút).
2. Khi đồng hồ đếm lùi sang phút 14 hoặc 15, thực hiện thanh toán PayOS: Hệ thống không bị Scheduler hủy cưỡng bức nhờ khoảng thời gian ân hạn (Grace Period 3 phút).

#### 🔹 Kiểm tra BUG-04: Toàn vẹn tài chính (Không tin tưởng Client)
1. Thử gửi request `POST /api/v1/bookings` qua Postman với `totalAmount: 1000` và `advanceDeposit: 200` cho thời lượng 2 giờ của xe Container (giá gốc 50.000đ/giờ).
2. Kiểm tra database/kết quả trả về: `totalAmount` được Backend tính chuẩn xác thành `100.000đ` và `advanceDeposit = 20.000đ` (20%).

#### 🔹 Kiểm tra BUG-05 & BUG-06: Đặt chỗ & Tìm kiếm loại xe mượt mà
1. Trên thanh tìm kiếm, mở dropdown chọn xe: Chọn thử lần lượt *Ô tô 4-7 chỗ*, *Xe tải nhẹ*, *Xe tải nặng*, *Xe Container*.
2. Xác nhận danh sách trả về bãi xe tương ứng mà không bị lỗi `400 Bad Request`.
3. Bấm **"Giữ chỗ & thanh toán"**: Giữ slot thành công, không còn báo *"Không thể kết nối đến hệ thống bãi đỗ xe"*.

#### 🔹 Kiểm tra BUG-07: Giao diện Đặt chỗ của tôi
1. Vào `http://localhost:5173/bookings`: Thanh tìm kiếm và bộ lọc trạng thái luôn hiển thị 100% chiều rộng.
2. Bấm vào bất kỳ bãi xe nào: Panel vé QR hiển thị song song bên phải dạng Master-Detail gọn gàng, không che khuất bộ lọc, có nút [X] đóng mở tiện lợi.
