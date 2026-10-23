# 50 — Security and Data Safety

## Authentication/authorization
- Xác minh quyền ở backend/service sở hữu resource; không tin userId/role từ client.
- Kiểm tra issuer, audience khi áp dụng, expiry, signature, algorithm policy và token type theo thiết kế.
- Không log token hoặc secret.
- Service-to-service trust phải được mô tả; không tin header identity tùy ý từ Internet.
- CORS không phải cơ chế authorization.
- Rate limit phải đặt đúng boundary; không dùng rate limit thay thế auth.

## Database
- Mọi migration cần được review và có kế hoạch tương thích với phiên bản app rollout.
- Dữ liệu production không được copy vào local/agent context nếu chưa loại bỏ dữ liệu nhạy cảm.
- Query update/delete phải có điều kiện rõ, kiểm tra số hàng bị ảnh hưởng và backup/transaction phù hợp.
- Không sửa dữ liệu bằng SQL trực tiếp để “chữa” lỗi nếu chưa hiểu invariant nghiệp vụ.
- Với stock/balance/payment, xem xét race condition, locking, idempotency và audit trail.

## Secrets
- Dùng secret manager hoặc file môi trường được bảo vệ theo môi trường.
- Không commit `.env`, private key, credentials, token hoặc backup chứa dữ liệu nhạy cảm.
- Báo vị trí secret bị lộ mà không lặp lại giá trị.
