# Báo Cáo Điều Tra Sự Cố (Incident Investigation Report)

## 1. Thông Tin Sự Cố
- **Mã sự cố (Incident ID):** INC-YYYYMMDD-XX
- **Thời gian phát hiện:** YYYY-MM-DD HH:mm:ss
- **Mức độ nghiêm trọng (Severity):** [CRITICAL / HIGH / MEDIUM / LOW]
- **Dịch vụ ảnh hưởng:** [identity-service / parking-lot-service / booking-service / payment-service / notification-service]
- **Triệu chứng (Symptom):** Mô tả tóm tắt hiện tượng lỗi từ góc độ người dùng.

---

## 2. Chuỗi Bằng Chứng Xác Thực (Evidence Trail)
- **Correlation ID / Trace ID:** `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`
- **Tệp tin & Dòng lệnh phát sinh lỗi:** `[ClassName.java](file:///path/to/ClassName.java#L123)`
- **Trích đoạn Log & Stack Trace:**
```text
(Dán đoạn log ngắn gọn trích xuất từ container hoặc loki)
```

---

## 3. Nguyên Nhân Gốc Rễ (Root Cause Analysis - 5 Whys)
- **Nguyên nhân trực tiếp:** 
- **Cơ chế phát sinh lỗi:** (Ví dụ: do race condition khi 2 request cùng gọi hàm holdSlot cùng lúc, dẫn tới xung đột khóa).

---

## 4. Phương Án Khắc Phục Đề Xuất (Proposed Remediation)
- **Giải pháp ngắn hạn (Hotfix):** Sửa lỗi tối thiểu để phục hồi dịch vụ.
- **Giải pháp dài hạn:** Tối ưu kiến trúc, bổ sung index, thêm circuit breaker.
- **Test case kiểm chứng:** `ClassNameTest#shouldHandleConcurrentHoldGracefully()`

---

## 5. Kế Hoạch Rollback Nếu Bản Vá Thất Bại
- Các bước khôi phục trạng thái cũ trong vòng dưới 5 phút.
