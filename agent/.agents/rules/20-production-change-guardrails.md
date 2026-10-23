# 20 — Production Change Guardrails

## Mặc định
Agent không được tự thực thi deploy hoặc thao tác production. Agent được phép soạn lệnh/kế hoạch để người dùng xem xét, trừ khi người dùng đã cho phép rõ ràng hành động cụ thể trong môi trường cụ thể.

## Nhóm lệnh
### READ-ONLY (thường được phép sau khi xác định đúng môi trường)
- `pwd`, `git status`, `git diff`, `git log`
- đọc file cấu hình không chứa secret
- `ss -ltnp`, `df -h`, `free -h`, `uptime`, `docker ps`, `docker stats`
- `systemctl status ...`, `journalctl ...` ở chế độ đọc
- `nginx -t`, `nginx -T` (cẩn thận đầu ra có thể chứa thông tin nhạy cảm; không đưa secret vào báo cáo)
- test/build local trong workspace, nếu không gây side effect ngoài dự kiến.

### CẦN XÁC NHẬN TRƯỚC
- `systemctl restart/reload/stop/start`
- `docker compose up/down`, pull image, recreate container
- database migration hoặc write query
- sửa firewall, SSH, DNS, Nginx, port binding, systemd
- deploy/rollback, rotate secret, thay quyền file
- bất kỳ lệnh nào tác động tới dữ liệu hoặc user đang sử dụng hệ thống.

### MẶC ĐỊNH CẤM
- Xóa volume/database/backup hoặc dữ liệu production.
- `docker compose down -v`, `docker system prune -a` trên production.
- `DROP/TRUNCATE`, bulk update/delete không có transaction/kế hoạch phục hồi.
- `git push --force` lên branch dùng chung.
- tắt TLS/auth/firewall để vượt qua lỗi.
- mở DB/cache/broker ra Internet mà không có thiết kế bảo mật và phê duyệt.

## Trước thao tác có rủi ro
Phải trình bày:
1. Environment và host đích đã xác minh.
2. Lệnh chính xác và tác dụng từng phần.
3. Những gì có thể gián đoạn hoặc mất dữ liệu.
4. Pre-check và điều kiện dừng.
5. Backup/restore hoặc rollback khả thi.
6. Post-check và tiêu chí thành công.
7. Yêu cầu người dùng xác nhận.

Không coi “hãy sửa lỗi” là quyền tự động deploy production.
