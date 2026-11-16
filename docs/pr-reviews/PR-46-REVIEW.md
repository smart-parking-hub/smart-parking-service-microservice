# Báo Cáo Đánh Giá Kỹ Thuật Pull Request #46

> **Dự án:** `smart-parking-service-microservice` (Backend Microservices)  
> **Tiêu chuẩn áp dụng:** `ak-review-pr v3.0.0` (Anti-AI Slop, Finding Standard & Safety Engine)  
> **Thời điểm đánh giá:** 2026-11-14T18:17:00+07:00  
> **Commit SHA rà soát:** `8315e6b62cf7867fab9007cdabc3d7734eb8d0d3` (`refs/pull/46/head`)  
> **Quyết định đề xuất:** ✅ **APPROVE (Phê duyệt để Merge vào nhánh `main`)**

---

## 1. Thông Tin Tổng Quan Pull Request

| Thuộc tính | Chi tiết |
| :--- | :--- |
| **Pull Request** | [#46 - feat(payment): add merchant ledger and verify PayOS amounts](https://github.com/smart-parking-hub/smart-parking-service-microservice/pull/46) |
| **Tác giả** | `@lochuynh22` (Huỳnh La Tiến Lộc) |
| **Nhánh nguồn (Head)** | `feat/payment-merchant-ledger-payos-amount` (`8315e6b`) |
| **Nhánh đích (Base)** | `main` (`8c2ced3`) |
| **Mã công việc (Jira/Trello)** | `SP-S3-PAY-03` |
| **Phạm vi thay đổi** | 10 files (`+559` dòng / `-1` dòng) |
| **Tương thích Merge** | `Clean` (Không xung đột với nhánh `main` mới nhất — xác thực qua `git merge-tree`) |
| **Kiểm thử thực tế** | **100% PASS** (15/15 test mới trong `payment-service` và `booking-service`) |

---

## 2. Tóm Tắt Đánh Giá (Executive Summary)

PR #46 là một pull request chất lượng cao, giải quyết đồng thời hai bài toán trọng yếu trong phân hệ thanh toán (`payment-service`):

1. **Cung cấp API sổ cái giao dịch ví Chủ bãi (`GET /api/v1/merchant/revenue/ledger`):**
   - Hỗ trợ phân trang an toàn (`page` $\ge 0$, `size` từ 1 đến 100).
   - Truy vấn đọc tối ưu `@Transactional(readOnly = true)` trên bảng `wallet_ledger_entries`, sắp xếp ổn định theo `createdAt DESC, id DESC`.
   - **Bảo mật Principal & Chống IDOR tuyệt đối:** Mã `merchantId` được trích xuất duy nhất từ `@AuthenticationPrincipal AuthenticatedUser` (được xác thực bởi Gateway/JWT), bỏ qua hoàn toàn các query parameter giả mạo như `merchantId=999`.
   - **Bảo vệ ranh giới dữ liệu:** DTO `MerchantLedgerEntryResponse` là Java record chỉ chứa các trường scalar cần thiết; loại bỏ hoàn toàn việc serialize entity `Payment` hay tài khoản ngân hàng nội bộ.

2. **Khóa chặt tính toàn vẹn số tiền thanh toán PayOS (PayOS Amount & Link Verification):**
   - **Tái xác thực Link cũ (Cached Link Revalidation):** Trước khi trả về link PayOS `PENDING` đã lưu trong cache, hệ thống truy vấn lại báo giá chuẩn từ `booking-service` (`bookingPaymentVerifier.require`), đối chiếu khớp số tiền cọc, kiểm tra đúng `merchantId` và `parkingLotId`.
   - **Truy vấn trạng thái thời gian thực từ PayOS:** Gọi `payOS.paymentRequests().get(orderCode)` để đảm bảo link còn `PENDING` và số tiền trên cổng PayOS khớp 100% với số tiền cọc của hệ thống. Nếu bị lệch hoặc link đã bị hủy/hết hạn, ném ngay `AppException(ErrorCode.CONFLICT)` để chặn việc thanh toán sai số tiền.
   - **Xác thực Link tạo mới (Fresh Link Validation):** Khi PayOS tạo link mới, kiểm tra `amount`, `orderCode` và `checkoutUrl` hợp lệ trước khi lưu vào cơ sở dữ liệu.
   - **Chống ghi đè giá từ phía Client:** Không bao giờ tin tưởng `amount` gửi lên từ body của client; luôn lấy số tiền báo giá từ `booking-service`.

---

## 3. Bảng Tổng Hợp Điểm Phát Hiện (Findings Summary)

| ID | Tiêu đề | Phân loại | Mức độ | Mức tin cậy | Độ ưu tiên | Trạng thái |
| :---: | :--- | :---: | :---: | :---: | :---: | :---: |
| — | Không phát hiện lỗi bảo mật, logic hay AI slop | — | — | — | — | **CLEAN** |

- **Bảo mật:** Không có hardcoded secret; kiểm soát phân quyền chặt chẽ (`ROLE_MERCHANT`).
- **Hiệu năng:** Phân trang bị chặn ở mức tối đa 100 bản ghi/trang; không gây tràn RAM hay Full Table Scan.
- **Tương thích:** Tận dụng route `/api/v1/merchant/revenue/**` đã có sẵn tại API Gateway, không cần sửa đổi gateway hay migration database.

---

## 4. Kết Quả Xác Minh Thực Tế (Verification Run)

Chạy trực tiếp trên máy trạm với Java 21 LTS:

```powershell
# 1. Kiểm thử Controller & Repository Sổ Cái Ví Merchant + Xác thực Số tiền PayOS:
.\gradlew.bat :payment-service:test --tests "*MerchantLedger*" --tests "*PaymentAmountConsistencyTest*" --no-daemon --console=plain
# Kết quả: BUILD SUCCESSFUL in 1m 28s (14/14 test PASS)

# 2. Kiểm thử Tính toán Tỷ lệ Cọc 20% trong Booking Service:
.\gradlew.bat :booking-service:test --tests "*ParkingChargeTest" --no-daemon --console=plain
# Kết quả: BUILD SUCCESSFUL in 41s (1/1 test PASS)
```

---

## 5. Kết Luận & Quyết Định Đề Xuất

Pull Request **#46** hoàn thiện trọn vẹn yêu cầu nghiệp vụ của `SP-S3-PAY-03`, ngăn chặn rủi ro thất thoát hoặc thanh toán sai lệch số tiền cọc trên PayOS, đồng thời cung cấp API sổ cái minh bạch cho Chủ bãi.

👉 **QUYẾT ĐỊNH: APPROVE (Sẵn sàng để Merge vào `main`).**
