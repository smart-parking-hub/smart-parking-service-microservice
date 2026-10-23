# Skill: Safe VPS Operations

## Mặc định chỉ đọc
Bắt đầu bằng lệnh quan sát, không thay đổi trạng thái:
- `date -Is`, `hostname`, `uptime`
- `free -h`, `df -h`, `lsblk`
- `ss -ltnp`
- `docker ps` và `docker stats` nếu Docker đang dùng
- `systemctl status <service>` và `journalctl -u <service> --since ...` nếu được phép
- `nginx -t` trước mọi đề xuất reload Nginx
Không chạy lệnh trên host production nếu chưa xác minh host và quyền truy cập.

## Trước khi thay đổi
1. Ghi host/environment.
2. Xác định file cấu hình thực sự đang được nạp (`nginx -T`, `systemctl cat`, compose config) nhưng tránh làm lộ secret.
3. Backup file cấu hình trước khi sửa.
4. Dùng `diff` để xem thay đổi.
5. Kiểm tra cú pháp.
6. Có cửa sổ triển khai/approval nếu có gián đoạn.
7. Xác định rollback.
8. Sau thay đổi kiểm tra status, port, health endpoint và logs.

## Port/firewall/SSH
- Không đổi SSH port, UFW, network hoặc port bind trong cùng một bước sửa ứng dụng.
- Không đóng phiên SSH hiện tại trước khi xác minh phiên mới hoạt động khi đổi SSH.
- Không mở DB/broker/cache ra Internet mặc định.
- Không giết process chỉ vì port đang được dùng; xác định owner và mục đích trước.
- Không dùng `kill -9` làm bước đầu tiên.
