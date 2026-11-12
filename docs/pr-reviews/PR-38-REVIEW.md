# Báo Cáo Đánh Giá Kỹ Thuật Pull Request #38

> **Dự án:** `smart-parking-service-microservice` (Backend Microservices)  
> **Tiêu chuẩn áp dụng:** `ak-review-pr v3.0.0` (Anti-AI Slop, Finding Standard & Safety Engine)  
> **Thời điểm đánh giá:** 2026-11-12T21:45:00+07:00  
> **Quyết định đề xuất:** ✅ **APPROVE WITH MINOR RECOMMENDATIONS (Phê duyệt để Merge)**

---

## 1. Thông Tin Tổng Quan Pull Request

| Thuộc tính | Chi tiết |
| :--- | :--- |
| **Pull Request** | [#38 - feat(identity): persist merchant bank account holder](https://github.com/smart-parking-hub/smart-parking-service-microservice/pull/38) |
| **Tác giả** | `@lochuynh22` |
| **Nhánh nguồn (Head)** | `feat/identity-pay-08-merchant-bank-profile` (`48c9df9`) |
| **Nhánh đích (Base)** | `main` (`3508b5f`) |
| **Mã công việc (Jira/Trello)** | `SP-S3-PAY-08-BE` |
| **Phạm vi thay đổi** | 11 files (`+1,199` dòng / `-6` dòng) |
| **CI / Quality Gate** | ✅ GitHub Actions: Completed & Success (Tất cả test và build Gradle xanh 100%) |
| **Mergeable** | `True` (Mergeable state: `clean`, không conflict với nhánh `main`) |

---

## 2. Tóm Tắt Đánh Giá (Executive Summary)

PR #38 là một pull request chất lượng cao, thực hiện đúng và đủ yêu cầu nghiệp vụ của nhiệm vụ **`SP-S3-PAY-08-BE`** (Bổ sung và lưu trữ tên chủ tài khoản ngân hàng của Chủ bãi xe):

1. **Hoàn thiện chuỗi dữ liệu giữa `identity-service` và `payment-service`:** 
   Trước đây, entity `WithdrawalRequest` và DTO `CreateWithdrawalRequestDto` bên `payment-service` đã có sẵn trường `accountHolderName`, nhưng phía `identity-service` bảng `merchants` chỉ lưu `bank_name` và `bank_account_num`. PR này bổ sung cột `account_holder_name` giúp hoàn thiện hồ sơ ngân hàng của Merchant, cho phép Frontend tự động điền (autofill) chính xác tên chủ tài khoản khi tạo lệnh rút tiền.
2. **Migration cơ sở dữ liệu an toàn tuyệt đối (Zero Downtime):**
   Flyway `V5__add_merchant_account_holder_name.sql` thêm cột `VARCHAR(100) NULL` vào sau `bank_account_num`. Cột cho phép giá trị `NULL` nên hoàn toàn tương thích ngược với toàn bộ dữ liệu lịch sử và không làm gián đoạn hệ thống đang chạy.
3. **Thiết kế API & Validation mẫu mực:**
   - Hỗ trợ cơ chế cập nhật từng phần (PATCH-like): Client cũ hoặc request không truyền `accountHolderName` sẽ giữ nguyên giá trị hiện có trong database, không bị ghi đè thành `null`.
   - Tự động trim khoảng trắng đầu cuối qua `@JsonSetter` trong DTO và kiểm tra regex `^\d{6,30}$` cho số tài khoản.
   - **Bảo toàn số 0 ở đầu số tài khoản:** Xử lý chuỗi số tài khoản ngân hàng (như Vietcombank `0071...`) chuẩn xác, không bị ép kiểu sang số làm mất số `0`.
   - **Bảo toàn tiếng Việt có dấu:** Không ép in hoa, không loại bỏ dấu tiếng Việt trên tên chủ tài khoản (ví dụ: `BÙI THỊ CHỦ BÃI` hay `Trần Thị Ngọc Diệp`).
   - **Tính nguyên tử (Atomicity):** Kiểm tra tính hợp lệ của toàn bộ thông tin ngân hàng trước khi cập nhật `User` và `Merchant`, tránh trường hợp cập nhật dở dang một phần khi có lỗi.
4. **Bảo mật & Phân quyền (Security & Principal Isolation):**
   - `@PreAuthorize("hasRole('MERCHANT')")` bắt buộc role Chủ bãi.
   - Lấy `userId` trực tiếp từ JWT `CustomUserDetails.getId()`, ngăn chặn hoàn toàn tấn công sửa ID (IDOR) qua body hay query parameter.
   - Response trả về che số tài khoản (`maskedBankAccount`: `*********4567`) để hiển thị an toàn trên giao diện.
5. **Bộ kiểm thử tự động toàn diện:**
   - 18 ca kiểm thử tích hợp bảo mật (`MerchantProfileSecurityIntegrationTest.java`): phủ 401, 403, 200, whitespace trimming, atomicity, IDOR isolation.
   - 16 ca kiểm thử tầng service (`MerchantProfileServiceImplTest.java`): phủ toàn bộ luồng nghiệp vụ và trường hợp biên (edge cases).
   - Kiểm thử migration Flyway trên cả H2 lẫn MySQL 8.4 thực tế (`IdentitySchemaMigrationTest.java`).
6. **Cải tiến hạ tầng CI:**
   Bổ sung MySQL 8.4 service container vào GitHub Actions workflow (`ci-build.yml`), giúp việc chạy test Flyway trên môi trường CI diễn ra ổn định và khép kín.

---

## 3. Bảng Tổng Hợp Các Điểm Phát Hiện (Findings Summary)

| ID | Tiêu đề | Phân loại | Mức độ | Mức tin cậy | Độ ưu tiên | File ảnh hưởng |
| :---: | :--- | :---: | :---: | :---: | :---: | :--- |
| **F-01** | `IdentitySchemaMigrationTest` gọi `fail()` khi không có MySQL khiến `./gradlew test` cục bộ có thể bị gián đoạn | `testing` / `dx` | **Minor** | High | **P3** | `IdentitySchemaMigrationTest.java#L125-L130` |
| **F-02** | Lưu ý va chạm phiên bản Flyway V5 với file migration chưa commit trên máy cục bộ | `database` / `conflict` | **Minor** *(Cảnh báo)* | High | **P3** | `identity-service/.../db/migration/` |
| **F-03** | Khuyến nghị bổ sung đường dẫn chính xác `/api/v1/merchant/profile` tại API Gateway | `gateway` / `routing` | **Suggestion** | High | **P4** | `api-gateway/.../application.yml#L14` |

---

## 4. Chi Tiết Các Điểm Phát Hiện & Hướng Dẫn Kỹ Thuật

### 🟢 F-01: `IdentitySchemaMigrationTest` gọi `fail()` khi thiếu kết nối MySQL máy trạm
* **Vị trí:** `identity-service/src/test/java/com/smartparking/identity/IdentitySchemaMigrationTest.java` (Dòng 125–130 và 180–185)
* **Hiện tượng thực tế:**
  ```java
  @Test
  @DisplayName("MySQL: Flyway V1..V5 fresh database migration runs cleanly and validates merchants column structure")
  void mySqlFreshDatabaseMigrationV1ToV5() throws Exception {
      if (!isMySqlAvailable()) {
          fail("MySQL test environment verification FAILED: cannot connect to MySQL at "
                  + MYSQL_HOST + ":" + MYSQL_PORT + " with user '" + MYSQL_USER
                  + "'. Fresh database migration verification requires an accessible MySQL instance...");
      }
      ...
  }
  ```
* **Phân tích:**
  Tác giả đã rất có trách nhiệm khi bổ sung MySQL 8.4 service container vào GitHub Actions CI (`ci-build.yml`) nên CI luôn PASS. Tuy nhiên, trên máy cá nhân của lập trình viên, nếu ai đó chạy `./gradlew test` mà chưa bật Docker MySQL thì test suite này sẽ báo đỏ (Fail) do hàm `fail(...)` được gọi trực tiếp.
* **Khuyến nghị tối ưu:**
  Trong JUnit 5, đối với các bài test phụ thuộc hạ tầng bên ngoài, nên sử dụng `Assumptions.assumeTrue(...)` thay vì `fail(...)`:
  ```java
  // Khuyến nghị:
  org.junit.jupiter.api.Assumptions.assumeTrue(
      isMySqlAvailable(),
      "Bỏ qua kiểm thử MySQL vì không phát hiện MySQL đang chạy tại " + MYSQL_HOST + ":" + MYSQL_PORT
  );
  ```
  Cách này đảm bảo:
  - Khi chạy trên CI (đã có MySQL service container): Test vẫn chạy 100% và kiểm tra đầy đủ.
  - Khi chạy trên máy cá nhân của dev (không có MySQL): Test sẽ hiển thị `SKIPPED` màu vàng thay vì làm gãy build của các module khác.

---

### 🟢 F-02: Cảnh báo va chạm phiên bản Flyway V5 với file migration cục bộ
* **Vị trí:** `identity-service/src/main/resources/db/migration/`
* **Hiện tượng:**
  Trong PR #38, migration mới được đặt tên là:
  `V5__add_merchant_account_holder_name.sql`.
  Tuy nhiên, trong working tree cục bộ của dự án hiện đang có file untracked:
  `identity-service/src/main/resources/db/migration/V5__add_updated_at_to_user_warnings.sql`.
* **Rủi ro:**
  Nếu merge PR #38 vào `main` trước, nhánh `main` sẽ chính thức chiếm phiên bản `V5`. Khi đó, nếu bạn commit file `V5__add_updated_at_to_user_warnings.sql` thì Flyway sẽ báo lỗi trùng lặp phiên bản `Found more than one migration with version 5`.
* **Cách xử lý:**
  Sau khi merge PR #38, hãy đổi tên file cục bộ thành `V6__add_updated_at_to_user_warnings.sql`.

---

### ℹ️ F-03: Khuyến nghị bổ sung đường dẫn tại API Gateway
* **Vị trí:** `api-gateway/src/main/resources/application.yml` (Dòng 14)
* **Hiện trạng:**
  ```yaml
  - Path=..., /api/v1/merchant/profile/**, /api/merchant/profile/**, ...
  ```
* **Đánh giá:**
  Pattern `/**` trong Spring Cloud Gateway phiên bản mới nhận diện được cả đường dẫn gốc `/api/v1/merchant/profile`. Tuy nhiên, để đồng bộ tuyệt đối với các cấu hình khác như `/api/v1/vehicles, /api/v1/vehicles/**` và `/api/v1/admin/users, /api/v1/admin/users/**`, nên khai báo rõ cả 2 biến thể:
  ```yaml
  - Path=..., /api/v1/merchant/profile, /api/v1/merchant/profile/**, /api/merchant/profile, /api/merchant/profile/**, ...
  ```

---

## 5. Đánh Giá Tiêu Chuẩn Anti-AI Slop & Chất Lượng Code

- ✅ **Không có Ghost Code / Code giả:** Toàn bộ code thêm mới đều được sử dụng trong Controller, Service, DTO, Repository và Test.
- ✅ **Tính đóng gói và ranh giới dịch vụ (Service Boundaries):** Được ghi chép tường minh trong `docs/data-ownership.md` và `docs/api-contracts/merchant-profile.md`. `payment-service` và `identity-service` giữ tính độc lập dữ liệu cao (Snapshot Isolation), không có truy vấn chéo DB.
- ✅ **Bảo toàn dữ liệu tài chính & ngân hàng:** Số tài khoản có số 0 ở đầu được giữ nguyên dạng `String`, tên chủ tài khoản giữ nguyên dấu tiếng Việt.
- ✅ **Tài liệu bàn giao (Documentation):** API Contract được viết cực kỳ chi tiết, kèm curl mẫu, JSON request/response và bảng mã lỗi.

---

## 6. Kết Luận & Quyết Định Nghiệm Thu

Pull Request #38 được triển khai rất chỉn chu, sạch sẽ, kiểm thử kỹ lưỡng từ tầng logic tới tầng cơ sở dữ liệu.

### Quyết định: ✅ **APPROVE (PHÊ DUYỆT ĐỂ MERGE)**
- Cho phép Merge PR #38 vào nhánh `main` của repo `smart-parking-service-microservice`.
- Sau khi merge, lưu ý đổi tên file migration cục bộ `V5__add_updated_at_to_user_warnings.sql` thành `V6` (nếu dùng đến).
