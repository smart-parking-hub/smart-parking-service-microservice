# Merchant Profile API Contract

- **Service**: `identity-service`
- **Base Path**: `/api/v1/merchant/profile`
- **Sprint / Task**: Sprint 3 / `SP-S3-PAY-08-BE`
- **Data Owner**: `identity-service` (`merchants` table)

---

## 1. GET `/api/v1/merchant/profile`

Lấy thông tin hồ sơ của Merchant đang đăng nhập (dựa trên JWT principal).

### Security
- **Authentication**: Bearer JWT token bắt buộc.
- **Role**: `ROLE_MERCHANT` (`hasRole('MERCHANT')`).
- **Principal**: Lấy `userId` trực tiếp từ token claim `subject`, không nhận `userId` hay `merchantId` từ query/body.

### Response `200 OK` (Hồ sơ đã cập nhật thông tin ngân hàng đầy đủ)
```json
{
  "success": true,
  "message": "Success",
  "code": "SUCCESS",
  "data": {
    "merchantId": 1,
    "userId": 4,
    "fullName": "Bùi Thị Chủ Bãi",
    "phone": "0988776655",
    "email": "chubai@example.com",
    "citizenId": "079200009999",
    "maskedCitizenId": "********9999",
    "businessLicense": "GPKD-12345",
    "bankName": "Vietcombank",
    "bankAccountNum": "0071001234567",
    "accountHolderName": "BÙI THỊ CHỦ BÃI",
    "maskedBankAccount": "*********4567",
    "walletBalance": 1500000.00,
    "accountStatus": "ACTIVE",
    "verificationStatus": "VERIFIED",
    "verificationLabel": "Đã xác thực",
    "address": "123 Đường Số 1, Quận 1, TP.HCM",
    "emergencyContact": "0911223344"
  },
  "timestamp": "2026-11-12T10:15:30.123456Z"
}
```

### Response `200 OK` (Hồ sơ lịch sử chưa cập nhật accountHolderName)
```json
{
  "success": true,
  "message": "Success",
  "code": "SUCCESS",
  "data": {
    "merchantId": 1,
    "userId": 4,
    "fullName": "Bùi Thị Chủ Bãi",
    "phone": "0988776655",
    "email": "thanh.dongsaigon@gmail.com",
    "citizenId": "079085001234",
    "maskedCitizenId": "********1234",
    "businessLicense": "GPKD-0315891234",
    "bankName": "Vietcombank",
    "bankAccountNum": "0071001234567",
    "accountHolderName": null,
    "maskedBankAccount": "*********4567",
    "walletBalance": 48500000.00,
    "accountStatus": "ACTIVE",
    "verificationStatus": "VERIFIED",
    "verificationLabel": "Đã xác thực",
    "address": "Số 789 Nguyễn Thị Định, Phường Cát Lái, Thành phố Thủ Đức, TP. Hồ Chí Minh",
    "emergencyContact": "0908123457"
  },
  "timestamp": "2026-11-12T10:15:30.123456Z"
}
```

> **Lưu ý**:
> - `accountHolderName`: Nullable (`String`). Key `accountHolderName` luôn hiện diện trong payload JSON response (không bị omit). Với các hồ sơ cũ chưa cập nhật tên chủ tài khoản riêng, giá trị trả về là `null` (`"accountHolderName": null`).
> - `accountHolderName` là giá trị **tự khai** bởi chủ bãi xe, hệ thống **chưa thực hiện xác thực liên kết ngân hàng**.
> - `maskedBankAccount`: Che toàn bộ chỉ để lại 4 số cuối.
> - Không log dữ liệu ngân hàng nhạy cảm ra file log hay console.

---

## 2. PUT `/api/v1/merchant/profile`

Cập nhật thông tin hồ sơ của Merchant đang đăng nhập.

### Security
- **Authentication**: Bearer JWT token bắt buộc.
- **Role**: `ROLE_MERCHANT`.
- **Principal Isolation**: Chỉ cập nhật hồ sơ tương ứng với `userId` trong token. Không thể can thiệp vào hồ sơ của chủ bãi khác.

### Request Body (`application/json`)
```json
{
  "fullName": "Bùi Thị Chủ Bãi",
  "email": "chubai.new@example.com",
  "citizenId": "079200009999",
  "businessLicense": "GPKD-12345",
  "bankName": "Vietcombank",
  "bankAccountNum": "0071001234567",
  "accountHolderName": "BÙI THỊ CHỦ BÃI",
  "address": "123 Đường Số 1, Quận 1, TP.HCM",
  "emergencyContact": "0911223344"
}
```

### Response `200 OK`
```json
{
  "success": true,
  "message": "Cập nhật hồ sơ thành công",
  "code": "SUCCESS",
  "data": {
    "merchantId": 1,
    "userId": 4,
    "fullName": "Bùi Thị Chủ Bãi",
    "phone": "0988776655",
    "email": "chubai.new@example.com",
    "citizenId": "079200009999",
    "maskedCitizenId": "********9999",
    "businessLicense": "GPKD-12345",
    "bankName": "Vietcombank",
    "bankAccountNum": "0071001234567",
    "accountHolderName": "BÙI THỊ CHỦ BÃI",
    "maskedBankAccount": "*********4567",
    "walletBalance": 1500000.00,
    "accountStatus": "ACTIVE",
    "verificationStatus": "VERIFIED",
    "verificationLabel": "Đã xác thực",
    "address": "123 Đường Số 1, Quận 1, TP.HCM",
    "emergencyContact": "0911223344"
  },
  "timestamp": "2026-11-12T10:15:30.123456Z"
}
```

### Quy tắc xử lý và Validation (PATCH-like)
1. **PATCH-like**:
   - Trường vắng mặt hoặc mang giá trị `null`: Giữ nguyên dữ liệu hiện hữu trong database, không ghi đè thành null.
   - Client cũ không gửi `accountHolderName`: Hồ sơ vẫn giữ nguyên `accountHolderName` hiện tại trong DB.
2. **Trimming & Length**:
   - Các trường ngân hàng nếu được gửi lên đều được tự động trim khoảng trắng ở hai đầu trước khi kiểm tra định dạng và lưu trữ.
   - `bankName`: Sau khi trim phải có độ dài 1–100 ký tự. Chuỗi rỗng hoặc toàn khoảng trắng trả về HTTP `400 Bad Request`.
   - `bankAccountNum`: Sau khi trim phải gồm 6–30 chữ số (`^\d{6,30}$`). Bảo toàn số `0` ở đầu (ví dụ: `"0071001234567"`). Chuỗi rỗng hoặc không đúng định dạng trả về HTTP `400 Bad Request`.
   - `accountHolderName`: Sau khi trim phải có độ dài 1–100 ký tự. Bảo toàn ký tự nguyên bản (không ép in hoa, không loại bỏ dấu tiếng Việt). Chuỗi rỗng hoặc toàn khoảng trắng trả về HTTP `400 Bad Request`.
3. **Tính nguyên tử (Atomicity - Không cập nhật một phần)**:
   - Toàn bộ validation ngân hàng được thẩm định trước khi lưu entity. Nếu bất kỳ trường nào vi phạm, transaction dừng và trả lỗi `400 Bad Request`, không cập nhật một phần dữ liệu.
4. **Không ảnh hưởng snapshot thanh toán**:
   - Việc cập nhật thông tin ngân hàng tại đây không sửa đổi các `WithdrawalRequest` đã tạo trong `payment-service`. Mỗi yêu cầu rút tiền lưu snapshot độc lập tại thời điểm tạo lệnh.

### Error Responses
- **401 Unauthorized**: Chưa đăng nhập hoặc token không hợp lệ / hết hạn.
- **403 Forbidden**: Người dùng không có vai trò `ROLE_MERCHANT` (ví dụ tài xế hoặc admin gọi vào endpoint này).
- **400 Bad Request**:
  - Dữ liệu gửi lên sai định dạng (ví dụ: `bankName` rỗng/whitespace, `bankAccountNum` chứa chữ cái hoặc ít hơn 6 ký tự).
  - Cấu trúc response lỗi chuẩn:
  ```json
  {
    "success": false,
    "code": "ERR_400",
    "message": "Tên ngân hàng phải từ 1 đến 100 ký tự!",
    "timestamp": "2026-11-12T10:15:30.123456Z"
  }
  ```
- **409 Conflict**:
  - `email` hoặc `citizenId` đã được sử dụng bởi người dùng khác.

---

## 3. Legacy Compatibility Route: POST `/api/v1/merchant/profile/documents` & Verification Badge

- **Service**: `identity-service`
- **Path**: `/api/v1/merchant/profile/documents`
- **Method**: `POST`
- **Description**: Tái sử dụng route cũ để phục vụ tương thích ngược. Khi client gọi endpoint này:
  - Nếu chỉ gửi số giấy phép kinh doanh (`businessLicense`): Hệ thống cập nhật trường `businessRegistrationNumber` trong bản nháp hồ sơ xác minh (`DRAFT`) và cập nhật `merchants.business_license`.
  - Nếu có đính kèm tệp: Hệ thống lưu tệp vào bản nháp dưới loại tài liệu bắt buộc `BUSINESS_REGISTRATION`.
  - Route này chỉ ghi trong `DRAFT`, dùng chung giới hạn, validation và kho tệp Identity. Các trạng thái khác trả `409 CONFLICT`.
  - PDF lưu trong MySQL. Ảnh JPEG, PNG và WebP tải mới lưu theo cấu hình Cloudinary của KYC. Bản nháp được tạo từ route cũ cũng để trống tên pháp lý.
  - Trả về envelope `MerchantProfileResponse` với message thông báo đã lưu vào bản nháp.
- **Verification Status & Badge mapping**:
  - `VERIFIED`: Đã xác thực (chỉ khi hồ sơ được Admin phê duyệt).
  - `PENDING_REVIEW` / `PENDING`: Đang chờ duyệt.
  - `CHANGES_REQUESTED`: Yêu cầu bổ sung.
  - `DRAFT`: Bản nháp.
  - `NOT_SUBMITTED`: Chưa nộp.
  - Hồ sơ cũ có số giấy phép nhưng chưa qua quy trình duyệt hồ sơ doanh nghiệp sẽ hiển thị trạng thái `CHƯA XÁC THỰC` (`NOT_SUBMITTED` hoặc `DRAFT`), không tự suy ra `VERIFIED`.
- Để sử dụng toàn bộ quy trình tải nhiều loại tài liệu, quản lý phiên bản và xem kết quả thẩm định OCR, tham khảo [Merchant Verification Contract](./merchant-verification.md).

Khi hồ sơ đã tồn tại, trường số giấy phép thuộc workflow xác minh. PUT hồ sơ chung vẫn cập nhật các trường hồ sơ thông thường.
