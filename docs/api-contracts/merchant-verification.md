# Merchant Business Verification API Contract

- **Service**: `identity-service`
- **Base Paths**:
  - Merchant: `/api/v1/merchant/profile/verification`
  - Admin: `/api/v1/admin/merchant-verifications`
- **Sprint / Task**: `SP-MERCHANT-KYC-01-BE` & `SP-MERCHANT-KYC-01-FE`
- **Data Owner**: `identity-service` (`merchant_verification_*` tables)

---

## 1. Overview & Workflow

Chủ bãi xe (Merchant) gửi hồ sơ định danh doanh nghiệp gồm mã số doanh nghiệp, tên pháp lý và tài liệu đính kèm (Giấy phép kinh doanh, Giấy ủy quyền, Tài liệu bổ sung).
Hệ thống kiểm tra kỹ thuật (dung lượng, định dạng, số trang, mật khẩu) và trích xuất OCR cục bộ (Tesseract OCR / PDFBox) để đối chiếu thông tin khai báo với nội dung tài liệu.
Quản trị viên (Admin) xem xét hồ sơ, kết quả đánh giá sơ bộ (PASS / WARN / NOT_CHECKED / UNAVAILABLE), duyệt hồ sơ hoặc gửi yêu cầu bổ sung theo từng mục.

### Trạng thái hồ sơ (`VerificationStatus`)
- `DRAFT`: Bản nháp chưa nộp hoặc đang được Merchant chỉnh sửa trước khi gửi lần đầu.
- `PENDING_REVIEW`: Đã nộp, đang chờ Admin xét duyệt.
- `CHANGES_REQUESTED`: Admin yêu cầu bổ sung/chỉnh sửa một số mục cụ thể.
- `VERIFIED`: Đã được Admin phê duyệt chính thức.

### Trạng thái đánh giá OCR (`AssessmentStatus`)
- `NOT_STARTED`: Chưa chạy đánh giá.
- `PENDING`: Đang trong tiến trình xử lý.
- `COMPLETED`: Đã hoàn tất đánh giá OCR và đối chiếu các tiêu chí.
- `UNAVAILABLE`: Engine OCR không khả dụng, lỗi đọc hoặc timeout; cho phép Admin kiểm tra thủ công.

---

## 2. Merchant Endpoints (`/api/v1/merchant/profile/verification`)

### 2.1 GET `/api/v1/merchant/profile/verification`
Lấy chi tiết hồ sơ xác minh của Merchant đang đăng nhập.

- **Security**: Bearer JWT, `ROLE_MERCHANT`.
- **Response**: `200 OK`
```json
{
  "success": true,
  "message": "Thành công",
  "code": "SUCCESS",
  "data": {
    "id": 1,
    "merchantId": 10,
    "status": "DRAFT",
    "draftRevision": 1,
    "submissionVersion": 1,
    "businessRegistrationNumber": "0101234567",
    "legalName": "CÔNG TY TNHH VẬN TẢI SMART",
    "resubmissionNote": null,
    "documents": [
      {
        "id": 101,
        "type": "BUSINESS_REGISTRATION",
        "originalName": "gpkd.pdf",
        "contentType": "application/pdf",
        "sizeBytes": 204850,
        "uploadedAt": "2026-11-18T10:00:00Z"
      }
    ],
    "assessment": {
      "status": "COMPLETED",
      "checkedAt": "2026-11-18T10:01:00Z",
      "checks": [
        {
          "code": "REQUIRED_DOCUMENT",
          "result": "PASS",
          "documentType": "BUSINESS_REGISTRATION",
          "field": null,
          "declaredValue": null,
          "extractedValue": null,
          "message": "Đã tải lên đủ giấy phép đăng ký kinh doanh bắt buộc"
        },
        {
          "code": "REGISTRATION_NUMBER_MATCH",
          "result": "PASS",
          "documentType": "BUSINESS_REGISTRATION",
          "field": "businessRegistrationNumber",
          "declaredValue": "0101234567",
          "extractedValue": "0101234567",
          "message": "Mã số doanh nghiệp trùng khớp với giấy tờ"
        }
      ]
    },
    "requestedItems": [],
    "requirements": [
      {
        "type": "BUSINESS_REGISTRATION",
        "required": true,
        "maxFiles": 3
      },
      {
        "type": "AUTHORIZATION_LETTER",
        "required": false,
        "maxFiles": 3
      },
      {
        "type": "SUPPORTING_DOCUMENT",
        "required": false,
        "maxFiles": 3
      }
    ],
    "editableItems": ["BUSINESS_INFO", "BUSINESS_REGISTRATION", "AUTHORIZATION_LETTER", "SUPPORTING_DOCUMENT"],
    "history": [],
    "updatedAt": "2026-11-18T10:00:00Z"
  }
}
```

### 2.2 PUT `/api/v1/merchant/profile/verification/details`
Lưu nháp thông tin kinh doanh (Mã số thuế / GPKD, Tên pháp lý công ty, Ghi chú giải trình khi nộp lại).

- **Security**: Bearer JWT, `ROLE_MERCHANT`.
- **Request Body**:
```json
{
  "draftRevision": 1,
  "businessRegistrationNumber": "0101234567",
  "legalName": "CÔNG TY TNHH VẬN TẢI SMART",
  "resubmissionNote": "Đã cập nhật lại tên pháp lý theo yêu cầu của Admin"
}
```
- **Response**: `200 OK` (trả về `MerchantVerificationDetailResponse` với `draftRevision` tăng lên 1).

### 2.3 PUT `/api/v1/merchant/profile/verification/documents/{documentType}`
Tải lên hoặc thay thế toàn bộ tệp cho một loại tài liệu trong bản nháp (`BUSINESS_REGISTRATION`, `AUTHORIZATION_LETTER`, `SUPPORTING_DOCUMENT`).

- **Security**: Bearer JWT, `ROLE_MERCHANT`.
- **Content-Type**: `multipart/form-data`
- **Parts**:
  - `draftRevision`: Số nguyên (bắt buộc)
  - `files`: Danh sách 1 đến 3 tệp (tối đa 5MB mỗi tệp, tổng request không quá 16MB; định dạng PDF, JPEG, PNG, WebP).
- **Response**: `200 OK` (trả về `MerchantVerificationDetailResponse` với `draftRevision` tăng lên 1).

### 2.4 DELETE `/api/v1/merchant/profile/verification/documents/{documentType}?draftRevision={draftRevision}`
Xóa toàn bộ tệp thuộc một loại tài liệu khỏi bản nháp.

- **Security**: Bearer JWT, `ROLE_MERCHANT`.
- **Response**: `200 OK` (trả về `MerchantVerificationDetailResponse` với `draftRevision` tăng lên 1).

### 2.5 POST `/api/v1/merchant/profile/verification/submit`
Gửi hồ sơ lên hệ thống để Admin xét duyệt (`DRAFT` / `CHANGES_REQUESTED` -> `PENDING_REVIEW`).

- **Security**: Bearer JWT, `ROLE_MERCHANT`.
- **Request Body**:
```json
{
  "draftRevision": 2,
  "ocrDocumentId": "5ab4e7b7-0a35-4bae-a9c5-94a63819e1f2"
}
```
- **Validation**:
  - Phải có mã số đăng ký kinh doanh và tên pháp lý.
  - Phải có ít nhất 1 tệp Giấy phép đăng ký kinh doanh (`BUSINESS_REGISTRATION`).
  - `ocrDocumentId` là UUID tệp đang hoạt động trong nhóm `BUSINESS_REGISTRATION` của đúng bản nháp. Được bỏ qua khi chỉ có một tệp. Bắt buộc gửi khi có nhiều tệp. Identity lưu tệp được chọn cùng bản nộp.
  - Nếu ở trạng thái `CHANGES_REQUESTED`: Tất cả các mục được yêu cầu bổ sung phải được cập nhật/thay thế.
- **Response**: `200 OK` (trả về `MerchantVerificationDetailResponse` với trạng thái `PENDING_REVIEW`, `submissionVersion` được tạo/tăng).

### 2.6 GET `/api/v1/merchant/profile/verification/documents/{documentId}/content`
Tải/xem tệp tài liệu thuộc quyền sở hữu của Merchant.

- **Security**: Bearer JWT, `ROLE_MERCHANT` (chỉ tải tệp của chính mình).
- **Response**: File raw bytes kèm `Content-Type`, `Content-Disposition`, `Cache-Control: private, no-store`.

---

## 3. Admin Endpoints (`/api/v1/admin/merchant-verifications`)

### 3.1 GET `/api/v1/admin/merchant-verifications`
Danh sách hồ sơ xác minh đã nộp (phân trang, lọc theo trạng thái). Không hiển thị hồ sơ `DRAFT`.

- **Security**: Bearer JWT, `ROLE_ADMIN`.
- **Query Params**:
  - `page`: int (default 0)
  - `size`: int (default 20)
  - `status`: `PENDING_REVIEW` | `CHANGES_REQUESTED` | `VERIFIED` (optional)
- **Response**: `200 OK` (`Page<AdminVerificationSummaryResponse>`)
```json
{
  "success": true,
  "code": "SUCCESS",
  "data": {
    "content": [
      {
        "id": 1,
        "merchantId": 10,
        "legalName": "CÔNG TY TNHH VẬN TẢI SMART",
        "businessRegistrationNumber": "0101234567",
        "status": "PENDING_REVIEW",
        "assessmentStatus": "COMPLETED",
        "submissionVersion": 1,
        "submittedAt": "2026-11-18T10:05:00Z"
      }
    ],
    "totalElements": 1,
    "totalPages": 1,
    "size": 20,
    "number": 0
  }
}
```

### 3.2 GET `/api/v1/admin/merchant-verifications/{dossierId}`
Chi tiết bản nộp gần nhất gồm thông tin khai, tài liệu và kết quả OCR của cùng phiên bản, kể cả khi Merchant đang sửa nháp. Hồ sơ chưa nộp trả 404. Danh sách chỉ gồm hồ sơ đã nộp và xếp mặc định theo thời điểm nộp mới nhất.

`assessment.source` chứa `documentId`, `originalName`, `method` và `submissionVersion` khi worker đã ghi nguồn đối chiếu của bản nộp. `method` là `PDF_TEXT`, `SCANNED_PDF_OCR` hoặc `IMAGE_OCR`. Kết quả cũ thiếu dữ liệu nguồn và lần kiểm tra chưa đọc được tệp có `source: null`. Dùng `submissionVersion` và `documentId` trong URL tải tệp của Admin.

- **Security**: Bearer JWT, `ROLE_ADMIN`.
- **Response**: `200 OK` (`MerchantVerificationDetailResponse`).

### Tra cứu MST trong bản nộp

`POST /api/v1/admin/merchant-verifications/{dossierId}/submissions/{submissionVersion}/tax-lookup` yêu cầu Bearer JWT và `ROLE_ADMIN`. Dùng `{"confirmedBusinessTaxCode": true}` sau khi Admin xác nhận mã ghi trên giấy tờ là MST doanh nghiệp 10 số hoặc chi nhánh dạng 10 số-3 số. Identity lấy mã từ bản nộp hiện hành ở trạng thái `PENDING_REVIEW`. Có thể tra cứu từ bản nộp đầu, kể cả khi OCR đang xử lý.

Phản hồi `200 OK` dùng envelope `ApiResponse` và `Cache-Control: private, no-store`. `data` gồm `dossierId`, `submissionVersion`, `checkedAt` (UTC), `outcome`, `business` và `retryAt` (UTC hoặc `null`).

| `outcome` | Ý nghĩa |
| --- | --- |
| `FOUND` | Casso trả đúng MST và có tên. `business` gồm `taxCode`, `name`, `status`, `source` và `trial`. `status` và `source` có thể là `null`. `trial: true` đánh dấu phản hồi dùng thử. |
| `NOT_FOUND` | Lookup tại Casso trả HTTP 400 với `errorCode` là `IDKIT_BUSINESS_NOT_FOUND`. `business` là `null`. |
| `UNAVAILABLE` | Tích hợp tắt, bị giới hạn, lỗi kết nối/xác thực, hoặc dữ liệu trả về thiếu hay lệch mã. `business` là `null`. `retryAt` có giá trị nếu đang chờ hết giới hạn. |

Request thiếu xác nhận hoặc sai định dạng mã nhận `400`. Với mã 12 số hoặc khi client cũ gửi `confirmedHouseholdTaxCode: true` cho bản nộp hiện hành, API trả `400` kèm hướng dẫn đối chiếu giấy tờ và dừng trước khi gọi Casso. Hồ sơ chưa nộp hoặc không tồn tại nhận `404`. Bản nộp cũ hoặc hồ sơ đã rời hàng chờ nhận `409`. Identity kiểm tra điều kiện trước khi gọi Casso và kiểm tra lại phiên bản sau khi Casso phản hồi. Việc tra cứu chỉ đọc dữ liệu hồ sơ. Admin duyệt hoặc yêu cầu bổ sung qua API quyết định hiện tại.

### 3.3 GET `/api/v1/admin/merchant-verifications/{dossierId}/submissions/{submissionVersion}/documents/{documentId}/content`
Xem / tải tệp đính kèm thuộc một phiên bản nộp cụ thể của hồ sơ.

- **Security**: Bearer JWT, `ROLE_ADMIN`.
- **Response**: File raw bytes kèm header bảo mật (`Cache-Control: private, no-store`, `X-Content-Type-Options: nosniff`).

### 3.4 POST `/api/v1/admin/merchant-verifications/{dossierId}/approve`
Phê duyệt hồ sơ doanh nghiệp (`PENDING_REVIEW` -> `VERIFIED`).

- **Security**: Bearer JWT, `ROLE_ADMIN`.
- **Request Body**:
```json
{
  "submissionVersion": 1,
  "documentsReviewed": true,
  "note": "Hồ sơ hợp lệ, đã đối chiếu bản gốc"
}
```
- **Validation**:
  - `documentsReviewed` bắt buộc phải là `true`.
  - `note` bắt buộc nếu kết quả đánh giá OCR có cảnh báo (`WARN`) hoặc trạng thái OCR là `UNAVAILABLE`.
  - Trả về `409 CONFLICT` nếu phiên bản đã cũ hoặc hồ sơ không còn ở trạng thái `PENDING_REVIEW`.
- **Response**: `200 OK` (`MerchantVerificationDetailResponse`).
- **Side Effects**:
  - Cập nhật `merchants.verification_status = 'VERIFIED'`.
  - Bắn sự kiện outbox `MerchantVerificationEvent` (type: `MERCHANT_VERIFICATION_APPROVED`) lên topic `merchant-verification.events`.

### 3.5 POST `/api/v1/admin/merchant-verifications/{dossierId}/request-changes`
Yêu cầu chủ bãi bổ sung/chỉnh sửa hồ sơ (`PENDING_REVIEW` -> `CHANGES_REQUESTED`).

- **Security**: Bearer JWT, `ROLE_ADMIN`.
- **Request Body**:
```json
{
  "submissionVersion": 1,
  "resetLegalName": true,
  "items": [
    {
      "key": "BUSINESS_REGISTRATION",
      "reason": "Ảnh chụp giấy phép kinh doanh bị mờ góc dưới bên phải, đề nghị tải lại bản rõ hơn."
    },
    {
      "key": "BUSINESS_INFO",
      "reason": "Tên pháp lý công ty không khớp với tên trên Giấy chứng nhận ĐKKD."
    }
  ]
}
```
- **Validation**:
  - `items` không được rỗng, không được trùng lặp `key`.
  - Mỗi `reason` sau trim không được rỗng.
  - `resetLegalName` mặc định là `false`. Giá trị `true` yêu cầu có mục `BUSINESS_INFO` và kết quả OCR báo tên pháp lý không khớp kèm tên trích xuất. Identity để trống tên ở bản nháp sửa. Tên trong các bản đã nộp vẫn đọc từ bản chụp của từng phiên bản.
  - `key` hợp lệ: `BUSINESS_INFO`, `BUSINESS_REGISTRATION`, `AUTHORIZATION_LETTER`, `SUPPORTING_DOCUMENT`.
  - Trả về `409 CONFLICT` nếu phiên bản nộp không khớp với phiên bản hiện tại.
- **Response**: `200 OK` (`MerchantVerificationDetailResponse`).
- **Side Effects**:
  - Cập nhật `merchants.verification_status = 'CHANGES_REQUESTED'`.
  - Bắn sự kiện outbox `MerchantVerificationEvent` (type: `MERCHANT_VERIFICATION_CHANGES_REQUESTED`) lên topic `merchant-verification.events`.

---

## 4. Error Codes & HTTP Status

| HTTP Status | Error Code | Mô tả |
| :--- | :--- | :--- |
| `400 BAD_REQUEST` | `ERR_400` | Dữ liệu đầu vào không hợp lệ (thiếu trường bắt buộc, danh sách items rỗng, thiếu giấy phép kinh doanh khi nộp). |
| `400 BAD_REQUEST` | `ERR_400` | Tệp có mật khẩu, tệp vượt quá 5 trang (PDF), hoặc độ phân giải vượt quá 20 megapixels. |
| `403 FORBIDDEN` | `ERR_403` | Không có quyền truy cập hoặc cố gắng xem tệp của Merchant khác. |
| `404 NOT_FOUND` | `ERR_404` | Không tìm thấy hồ sơ hoặc tệp tài liệu. |
| `409 CONFLICT` | `ERR_409` | Xung đột phiên bản: `draftRevision` cũ (bị ghi đè bởi tab khác), `submissionVersion` cũ, hoặc trạng thái hồ sơ không cho phép thao tác. |
| `413 PAYLOAD_TOO_LARGE` | `ERR_413` | Kích thước tệp vượt quá 5 MiB hoặc tổng dung lượng tệp vượt quá 16 MiB. Servlet dành thêm dung lượng cho multipart framing. |
| `415 UNSUPPORTED_MEDIA_TYPE` | `ERR_415` | MIME ngoài danh sách hỗ trợ hoặc định dạng ảnh thực khác MIME khai báo. Tệp hỏng trả 400. |


### Đối chiếu bản nộp và lịch sử

- Document ID là chuỗi UUID. Admin tải tài liệu của đúng phiên bản trong đường dẫn. Merchant không sở hữu tài liệu nhận 404.
- Trong CHANGES_REQUESTED, GET/mutation của Merchant trả `requestedItems[].addressed`. Giá trị là true khi đã thay bằng ít nhất một tệp có nội dung mới, đã đổi thông tin khai, hoặc đã xác nhận lại thông tin kèm giải trình của vòng hiện tại. Lệnh submit kiểm tra lại tất cả mục và giữ các nhóm tài liệu ngoài yêu cầu.
- Timeline trả `items` cho mỗi yêu cầu bổ sung của đúng phiên bản. `actor` và `note` là các trường dành cho Admin.
- Submit trả `PENDING_REVIEW` và OCR `PENDING` sau khi lưu bản nộp. Admin có thể yêu cầu bổ sung trong lúc OCR chạy. Duyệt bản đang OCR trả 409.
