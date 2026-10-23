---
trigger: model_decision
---

# Rule 01: An Toàn Tối Cao & Cấm Lệnh Phá Hủy (Safety First)

## 1. Mục Đích
Bảo vệ toàn vẹn hệ sinh thái mã nguồn, dữ liệu database, và tính sẵn sàng của hệ thống máy chủ trước các thao tác ngoài ý muốn của AI Agent.

## 2. Danh Sách Lệnh Bị Cấm Tuyệt Đối (Strictly Forbidden Commands)
Agent không được phép thực thi hoặc đề xuất thực thi các lệnh sau dưới bất kỳ hình thức nào:
- **Xóa dữ liệu hàng loạt**: `rm -rf /`, `rm -rf *`, `rmdir /s /q C:\`
- **Phá hủy cơ sở dữ liệu**: `DROP DATABASE`, `DROP TABLE`, `TRUNCATE TABLE`, `FLUSHALL` (Redis) mà không có migration script được phê duyệt bằng văn bản.
- **Can thiệp hạ tầng mạng/bảo mật máy chủ**: `iptables -F`, `ufw disable`, sửa đổi cấu hình SSH `/etc/ssh/sshd_config`, gỡ bỏ chứng chỉ SSL.
- **Tắt máy hoặc ép dừng hệ thống bừa bãi**: `shutdown -h now`, `reboot`, `kill -9 -1`.
- **Force push phá vỡ lịch sử git**: `git push --force`, `git reset --hard` trên các branch dùng chung (`main`, `develop`, `release`).

## 3. Quy Tắc Thao Tác Tệp Tin
- Khi cần xóa tệp, phải nêu rõ đường dẫn tệp, lý do xóa và xin xác nhận của người dùng.
- Ưu tiên lưu backup tệp cũ (ví dụ: `.bak` hoặc kiểm tra lịch sử qua Git) trước khi ghi đè các cấu hình nhạy cảm.
