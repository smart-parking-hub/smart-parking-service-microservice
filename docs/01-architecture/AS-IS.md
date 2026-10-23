# Kiến Trúc Hiện Tại (AS-IS Architecture)

## 1. Mô Tả Tổng Quan
Hệ thống ban đầu được xây dựng theo mô hình Monolith (Nguyên khối) trong thư mục `smart-parking-service`:
- Toàn bộ nghiệp vụ (User, ParkingLot, Slot, Booking, Payment, Wallet, Notification) nằm chung trong một ứng dụng Spring Boot duy nhất.
- Một cơ sở dữ liệu MySQL duy nhất lưu trữ toàn bộ các bảng.
- Frontend React gọi trực tiếp đến backend monolith qua cổng 8080.

## 2. Các Rủi Ro & Nút Thắt Đã Xác Định (Bottlenecks)
1. **Khóa bản ghi (Locking & Race Condition):** Khi nhiều người dùng cùng truy cập đặt chỗ đỗ xe tại các bãi xe đông đúc, việc quản lý lock trong cùng 1 DB dễ gây chậm trễ (High Latency) và xung đột bản ghi.
2. **Khả năng Chịu lỗi Thấp (Single Point of Failure):** Nếu module thanh toán gặp trục trặc hoặc đối tác PayOS phản hồi chậm dẫn đến cạn kiệt connection pool của HikariCP, toàn bộ tính năng tìm bãi xe, đăng nhập và thông báo cũng bị treo theo.
3. **Triển khai cồng kềnh:** Sửa một lỗi nhỏ ở chức năng gửi thông báo bắt buộc phải build lại toàn bộ ứng dụng và gây gián đoạn các phiên đặt xe đang diễn ra.
