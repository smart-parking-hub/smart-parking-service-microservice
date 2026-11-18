# Merchant Verification Deployment & Runtime Guide

## 1. Overview
Tính năng xác minh doanh nghiệp chủ bãi xe (`SP-MERCHANT-KYC-01`) bổ sung quy trình tải tài liệu, xác thực kỹ thuật tệp (kích thước, định dạng, số trang, mã hóa), trích xuất OCR sơ bộ cục bộ và quy trình phê duyệt của Admin.

## 2. System Dependencies
- **Apache PDFBox 3.0.2**: Xử lý đọc tệp PDF, kiểm tra mã hóa/mật khẩu, kiểm tra số trang giới hạn (tối đa 5 trang) và trích xuất chữ số hóa kỹ thuật số (digital text).
- **TwelveMonkeys ImageIO WebP 3.11.0**: Mở rộng Java ImageIO để đọc định dạng WebP, kiểm tra kích thước và độ phân giải megapixel (tối đa 20MP).
- **Tesseract OCR (Cục bộ)**:
  - Runtime Alpine Docker (`identity-service/Dockerfile`): Cài đặt gói `tesseract-ocr`, `tesseract-ocr-data-vie`, `tesseract-ocr-data-eng` qua `apk add --no-cache`.
  - Local Dev Windows: Tự động phát hiện lệnh `tesseract` trên PATH. Nếu không có Tesseract, hệ thống tự động fallback:
    - Với file PDF có text layer: PDFBox trích xuất text trực tiếp không phụ thuộc Tesseract binary.
    - Với file ảnh hoặc scan: Trả về trạng thái `AssessmentStatus.UNAVAILABLE`, ghi log cảnh báo và cho phép Admin kiểm tra bằng mắt thường rồi duyệt kèm ghi chú bắt buộc (không chặn luồng nghiệp vụ).

### Casso IDKit tax lookup

Identity uses Casso Open Platform business and household lookup endpoints. Grant the app `idkit:business:read` for business and branch codes and `idkit:household:read` for 12-digit household codes. Configure `CASSO_APP_ID` and `CASSO_APP_SECRET` in the Identity runtime and set `CASSO_TAX_LOOKUP_ENABLED=true` when enabling the feature. The default is `false`. Local Compose passes these settings to `identity-service`. Grant outbound HTTPS access to `openapi.casso.vn` from Identity.

`CASSO_TAX_LOOKUP_TIMEOUT` defaults to `5s` for the token and lookup requests combined. `CASSO_TAX_LOOKUP_MINIMUM_INTERVAL` defaults to `3s` per Admin and per dossier within each Identity instance. The client reuses the OAuth token, refreshes it once if Casso rejects it, and honors `Retry-After` after Casso returns `429`. Provider responses and URL paths containing the tax code should be excluded from request logs and telemetry.

Use public company samples for smoke tests. The provider returned `200` for its published sample company and a known branch. A missing record returned HTTP `400` with `errorCode=IDKIT_BUSINESS_NOT_FOUND`. Disabling the feature stops requests to Casso and leaves manual KYC review available.

Trial responses may contain demonstration data, including household responses that echo the requested MST alongside a different name. Identity marks data from a trial app and the Admin screen displays the label beside the result. Use official app credentials for KYC decisions. [Casso IDKit](https://www.vietqr.io/tra-cuu-mst)

## 3. Database Migration
- Migration script: `identity-service/src/main/resources/db/migration/V8__merchant_business_verification.sql`.
- Tạo các bảng:
  - `merchant_verification_dossiers`
  - `merchant_verification_documents`
  - `merchant_verification_document_bytes`
  - `merchant_verification_submissions`
  - `merchant_verification_submission_documents`
  - `merchant_verification_assessment_checks`
  - `merchant_verification_requested_items`
  - `merchant_verification_history`
- Migration hoàn toàn mang tính cộng dồn (additive), không sửa đổi cấu trúc bảng cũ hay ảnh hưởng đến dữ liệu bãi xe, booking hoặc thanh toán.
- V9 thêm `assessment_attempts`, `assessment_lease_until` và index lấy job vào bảng bản nộp. Hồ sơ kiểm duyệt lấy trạng thái từ `merchant_verification_dossiers.status`.
- V10 bổ sung định danh Cloudinary, tệp được chọn và phương thức OCR vào các bảng Identity. Các giá trị mới để trống trên bản ghi cũ. Bảng `merchant_verification_cloudinary_cleanup` theo dõi lượt tải ảnh để có thể dọn ảnh sau khi transaction bị rollback hoặc tiến trình dừng giữa chừng.

## 4. Kafka Topics & Messaging
- **Topic và DLT**: `merchant-verification.events`, `merchant-verification.events.DLT`
- **Partitions**: 3 | **Replication Factor**: 1 (local/dev)
- **Producer**: `identity-service` (sử dụng Transactional Outbox `admin_event_outbox`).
- **Consumer**: `notification-service` (`notification-merchant-verification`).
- **Events**:
  - `MERCHANT_VERIFICATION_APPROVED`: Phát khi Admin duyệt hồ sơ. Gửi in-app notification đến Merchant dẫn về `/merchant/profile#giay-to`.
  - `MERCHANT_VERIFICATION_CHANGES_REQUESTED`: Phát khi Admin yêu cầu chỉnh sửa/bổ sung. Gửi in-app notification đến Merchant dẫn về `/merchant/profile#giay-to`.
- **Deduplication**: Lưu `source_event_id` trong `notifications` và `processed_events` để đảm bảo idempotent khi replay message.

## 5. Security & Isolation
- PDF và ảnh lưu theo cách cũ đọc từ `merchant_verification_document_bytes`. Ảnh Cloudinary dùng kiểu phân phối `authenticated`. Identity lấy bản gốc bằng URL ký có hiệu lực 60 giây sau khi kiểm tra quyền và kiểm tra SHA-256 của bytes tải về. Trình duyệt nhận bytes qua API hiện có.
- API tải/xem nội dung tài liệu trả header:
  - `Cache-Control: private, no-store`
  - `X-Content-Type-Options: nosniff`
- Kiểm soát phân quyền: Merchant chỉ được xem tệp của chính mình; Admin chỉ được xem tệp thuộc phiên bản đã nộp của hồ sơ.


## Worker OCR và giới hạn upload

Bản nộp có OCR PENDING được lưu cùng transaction nộp hồ sơ. Mỗi instance chạy tối đa một job tại một thời điểm. Worker claim bản nộp bằng lease 90 giây, chạy OCR ngoài transaction nghiệp vụ, sau đó ghi kết quả của chính bản nộp nếu lease và lần claim còn hiệu lực. Job dở dang có thể được nhận lại sau khi hết lease. Sau tối đa ba lần claim xử lý hoặc quá thời hạn 15 phút kể từ lúc nộp, worker ghi UNAVAILABLE để Admin có thể xét thủ công. Kết quả của v1 chỉ cập nhật bản ghi OCR v1.

Cấu hình local cho worker gồm `app.verification.assessment-worker-enabled` (mặc định true) và `app.verification.assessment-poll-ms` (mặc định 2000). Tesseract chạy cục bộ với `vie+eng`. PDFBox (Apache-2.0), TwelveMonkeys ImageIO (BSD-3-Clause) và Tesseract (Apache-2.0) là các phụ thuộc xử lý tệp.

Identity servlet giới hạn 5 MiB mỗi tệp và 17 MiB theo request multipart để chứa framing. Tầng nghiệp vụ kiểm tổng bytes 16 MiB và số lượng từ 1 đến 3 tệp. Những proxy bên ngoài Gateway cần giữ ngưỡng ít nhất bằng servlet để API trả lỗi theo hợp đồng.

## Cloudinary và quay lui

Identity đọc bộ cấu hình `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` của dự án. Có thể truyền bộ `KYC_CLOUDINARY_*` tương ứng để dùng môi trường Cloudinary riêng cho KYC. Cấu hình tài khoản dùng cho KYC cần giữ nguyên nội dung ảnh gốc và chấp nhận chữ ký SHA-256.

Bật `KYC_CLOUDINARY_WRITES_ENABLED=true` để gửi JPEG, PNG và WebP mới lên `smartparking/merchant-kyc`. Cả API KYC và API tải giấy tờ cũ dùng cấu hình này. Khi lần tải thất bại, toàn bộ thay đổi nhóm tài liệu và phiên bản nháp được rollback. Worker sẽ thử dọn từng mã đã ghi sau 5 phút và thử lại khi Cloudinary lỗi. Tệp của bản nộp được giữ để Admin tra cứu.

Để quay lui, đặt `KYC_CLOUDINARY_WRITES_ENABLED=false` và giữ cấu hình Cloudinary đang đọc. Identity chuyển ảnh tải mới về MySQL, tiếp tục đọc ảnh Cloudinary đã ghi và dọn các lượt tải lỗi. Giữ ứng dụng hỗ trợ đọc V10 trong giai đoạn chuyển tiếp.
