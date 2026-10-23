# Rule 06: Hàng Rào An Toàn & Bảo Mật (Security Guardrails)

## 1. Quản Lý Bí Mật (Secrets & Credentials)
- **Tuyệt đối không commit bí mật:** Không commit mật khẩu DB, khóa JWT bí mật, API key PayOS vào Git.
- Sử dụng biến môi trường (Environment Variables) hoặc file `.env` (được đưa vào `.gitignore`).

## 2. Xác Thực & Phân Quyền (AuthN & AuthZ)
1. **Edge Gateway:**
   - Xác thực token JWT của Client.
   - Trích xuất thông tin người dùng và chuyển tiếp vào mạng nội bộ qua HTTP headers an toàn:
     - `X-User-Id`
     - `X-User-Role`
     - `X-User-Email`
     - `X-Correlation-Id`
2. **Internal Business Services:**
   - Không tin tưởng mù quáng role/user ID do Client gửi trực tiếp từ frontend.
   - Luôn kiểm tra quyền sở hữu dữ liệu: Người dùng chỉ được xem/hủy lượt đặt chỗ của chính họ (`WHERE booking.user_id = :userId`), ngoại trừ quyền `ROLE_ADMIN`.

## 3. Chống Các Lỗ Hổng Web Phổ Biến
- **SQL Injection:** Luôn dùng Spring Data JPA, Hibernate Named Parameters hoặc Prepared Statements. Cấm nối chuỗi SQL thô.
- **Rate Limiting:** Áp dụng rate limiting tại Gateway bằng Redis để phòng chống brute-force và DDoS.
- **Log Sanitation:** Cấm ghi mật khẩu dạng rõ, số thẻ ngân hàng, hoặc JWT token đầy đủ vào tệp log.
