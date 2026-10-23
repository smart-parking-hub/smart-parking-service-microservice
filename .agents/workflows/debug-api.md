# Workflow: /debug-api - Quy Trình Truy Vết & Xử Lý Sự Cố API

## Mục Đích
Kích hoạt quy trình điều tra khi một API bị lỗi, trả về 5xx, 4xx bất thường hoặc bị timeout.

## Các Bước Thực Thi Của Agent (SOPs)

1. **Thu Thập Thông Tin Ban Đầu:**
   - Endpoint bị lỗi (ví dụ: `POST /api/bookings`).
   - Request payload, response status, response error message.
   - Header `X-Correlation-Id` (nếu có).

2. **Dò Tìm Bằng Chứng Trong Mã Nguồn:**
   - Mở Controller xử lý endpoint đó.
   - Dò ngược theo luồng: `Controller` -> `UseCase` -> `Domain Service` -> `Repository` hoặc `External Client`.
   - Trích dẫn file và dòng code nghi vấn bằng markdown link `[File](file:///path/to/file#L10)`.

3. **Kiểm Tra Các Khả Năng Ngoại Lệ Phổ Biến:**
   - *NPE / Null Value:* Trường dữ liệu nào không được kiểm tra null?
   - *Database Constraint:* Khóa ngoại, unique constraint, hoặc enum không hợp lệ?
   - *Network Timeout:* Cuộc gọi Feign sang service khác bị chậm?
   - *Distributed Lock:* Redis lock bị hết hạn trước khi transaction hoàn tất?

4. **Tạo Test Case Tái Hiện Lỗi (Failing Test):**
   - Viết test tái hiện lại input gây lỗi. Chạy test để xác nhận FAIL.

5. **Lập Báo Cáo Sự Cố & Đề Xuất:**
   - Điền báo cáo theo [`.agents/templates/incident-investigation-report.md`](file:///d:/Documents/JAVA%20WEB%20SERVICE/SmartParkingHub/.agents/templates/incident-investigation-report.md).
   - Xin phép trước khi thực hiện sửa mã nguồn.
