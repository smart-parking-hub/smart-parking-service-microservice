# Failure Scenarios & Kịch bản Ứng phó Sự cố Phân tán

Trong môi trường phân tán, các sự cố sau là bất khả kháng và hệ thống phải được chuẩn bị sẵn sàng:

| Kịch bản sự cố | Hậu quả tiềm tàng | Cơ chế phòng thủ & Xử lý |
| :--- | :--- | :--- |
| **1. Cổng thanh toán (PayOS) phản hồi chậm hoặc timeout** | Tài xế bị treo màn hình, không biết tiền đã trừ chưa | 1. Đặt `connectTimeout = 3s`, `readTimeout = 8s`<br>2. Sử dụng `Resilience4j Circuit Breaker`<br>3. Không tự ý retry trừ tiền! Hiển thị trạng thái "Đang chờ cổng thanh toán xác nhận". Gửi webhook để đồng bộ sau. |
| **2. Hai tài xế cùng chọn 1 chỗ đỗ (Race condition)** | Double booking (Đặt trùng 1 slot) | 1. Redis Distributed Lock: `SET lock:slot:{id} NX EX 5`<br>2. Unique Database Constraint trên lịch đặt chỗ<br>3. Người đến sau nhận lỗi `409 Conflict` và gợi ý slot khác. |
| **3. Broker Kafka bị sập (Downtime tạm thời)** | Service không phát được event sang các service khác | 1. Áp dụng **Transactional Outbox Pattern**: Lưu event vào bảng `outbox_events` cùng transaction SQL với dữ liệu chính.<br>2. Khi Kafka sống lại, Background Scheduler tiếp tục quét và gửi các event `PENDING`. Tuyệt đối không mất mát event. |
| **4. Consumer nhận 1 event nhiều lần** | Gửi email lặp lại, trừ tiền 2 lần | 1. Thiết kế Consumer tuân thủ nguyên tắc **Idempotent**.<br>2. Kiểm tra `eventId` trong bảng `processed_events`. Nếu đã có, commit offset và dừng xử lý ngay. |
| **5. Database của 1 microservice bị sập** | Toàn bộ hệ thống có bị chết chùm không? | **Không**. Nhờ cô lập database-per-service, nếu `notification-db` lỗi thì chỉ chức năng gửi thông báo bị tạm ngưng; việc xem bãi xe, đặt chỗ, thanh toán vẫn hoạt động bình thường. |
