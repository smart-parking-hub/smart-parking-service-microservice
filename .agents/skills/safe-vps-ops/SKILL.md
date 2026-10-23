---
name: safe-vps-ops
description: >-
  Use this skill when auditing, monitoring, or executing operational tasks on VPS or production
  servers hosting SmartParkingHub containers. Enforces read-only audit commands and strict safety protocols.
---

# Safe VPS Operations Skill (Vận Hành VPS An Toàn)

Kỹ năng này quy định cách Agent tương tác với máy chủ VPS chạy môi trường thực tế của SmartParkingHub.

## 1. Bộ Lệnh Kiểm Tra An Toàn (Được Phép Chạy Ngay)

### Kiểm tra container và mạng Docker
```bash
docker compose ps
docker stats --no-stream
docker network inspect smart-parking-net
```

### Kiểm tra tài nguyên máy chủ
```bash
# Kiểm tra dung lượng ổ đĩa (Disk space)
df -h
# Kiểm tra RAM và Swap
free -m
# Kiểm tra tải CPU và tiến trình
top -b -n 1 | head -n 20
# Kiểm tra các cổng đang mở
netstat -tuln | grep -E "80|443|8080|3306|6379|9092"
```

### Kiểm tra sức khỏe microservices qua Actuator
```bash
curl -s http://localhost:8080/actuator/health
curl -s http://localhost:8081/actuator/health
curl -s http://localhost:8082/actuator/health
curl -s http://localhost:8083/actuator/health
curl -s http://localhost:8084/actuator/health
curl -s http://localhost:8085/actuator/health
```

## 2. Quy Trình Xin Phép Cho Thao Tác Thay Đổi (Write Operations)
Nếu cần khởi động lại 1 container hoặc deploy bản vá lỗi:
1. Thông báo rõ tên container cần can thiệp (ví dụ: `smart-parking-booking`).
2. Giải thích lý do vì sao cần can thiệp (ví dụ: "Phát hiện rò rỉ kết nối DB, cần khởi động lại container").
3. Nêu phương án rollback nếu container không thể khởi động lại (`previous image tag`).
4. **Chỉ thực thi sau khi nhận lệnh rõ ràng từ người dùng.**
