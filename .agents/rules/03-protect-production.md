# Rule 03: Bảo Vệ Môi Trường Production & VPS (Protect Production)

## 1. Nguyên Tắc Phân Tách Môi Trường
- **Môi trường Phát triển (Local/Dev):** Được phép chạy build, unit test, container local.
- **Môi trường Vận hành Thực tế (VPS/Staging/Production):** Cực kỳ nhạy cảm, chứa dữ liệu người dùng thật và các giao dịch thanh toán tài chính PayOS.

## 2. Quy Định Đối Với Thao Tác Trên VPS
1. **Mặc định là chế độ Chỉ Đọc (Read-Only Audit):**
   - Các lệnh kiểm tra sức khỏe hệ thống được phép chạy không cần xin phép:
     - `docker compose ps`
     - `docker logs --tail 100 <container>`
     - `df -h`, `free -m`, `uptime`
     - `curl -f http://localhost:port/actuator/health`
2. **Cấm Tự Ý Thực Hiện Thao Tác Ghi/Thay Đổi (Write/Deploy Ban):**
   - **Cấm tự ý restart container**: `docker restart`, `docker compose down`.
   - **Cấm tự ý deploy phiên bản mới**: `docker compose up -d --build`.
   - **Cấm sửa file cấu hình trực tiếp trên máy chủ VPS**: Mọi thay đổi cấu hình phải được commit qua Git và triển khai thông qua pipeline CI/CD hoặc kịch bản có người giám sát.
3. **Kế Hoạch Bắt Buộc Khi Thao Tác Production:**
   - Mọi đề xuất thay đổi trên VPS bắt buộc phải đi kèm kế hoạch Rollback khẩn cấp (thời gian rollback < 5 phút) nếu gặp sự cố.
