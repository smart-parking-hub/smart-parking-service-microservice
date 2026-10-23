# Deployment Runbook - Hướng dẫn Triển khai Production & VPS

## 1. Yêu cầu Tiên quyết trên VPS
- Ubuntu 22.04 LTS hoặc 24.04 LTS (Tối thiểu 4 vCPU, 8GB RAM, 40GB SSD)
- Đã cài đặt Docker (v26+) và Docker Compose (v2.24+)
- Đã mở các cổng Firewall UFW: `80`, `443`, `22`

## 2. Quy trình Triển khai Lần đầu (Initial Setup)

```bash
# 1. Clone repository về VPS
git clone <repo-url> /opt/smartparking/microservices
cd /opt/smartparking/microservices

# 2. Tạo file biến môi trường .env từ template mẫu
cp .env.example .env
nano .env # Điền mật khẩu DB, JWT_SECRET, PAYOS_KEYS

# 3. Khởi động tầng Hạ tầng (MySQL, Redis, Kafka, Monitoring)
cd docker
docker compose -f docker-compose.infra.yml up -d

# 4. Kiểm tra sức khỏe của hạ tầng
docker compose -f docker-compose.infra.yml ps

# 5. Khởi tạo Kafka Topics
docker compose -f docker-compose.infra.yml exec kafka bash /scripts/init-topics.sh

# 6. Build và khởi động toàn bộ Microservices
docker compose -f docker-compose.apps.yml up -d --build
```

## 3. Quy trình Triển khai Phiên bản mới (Zero-Downtime Rolling Update)
Khi có bản code mới:
```bash
# 1. Kéo mã nguồn mới nhất
git pull origin main

# 2. Build và update service cụ thể (Ví dụ booking-service)
docker compose -f docker-compose.apps.yml build booking-service
docker compose -f docker-compose.apps.yml up -d --no-deps booking-service

# 3. Kiểm tra health check
curl -f http://localhost:8083/actuator/health || echo "Service unhealthy!"
```

## 4. Phương án Rollback khẩn cấp
Nếu phát hiện lỗi nghiêm trọng sau khi release:
```bash
# 1. Chuyển container về image tag trước đó
docker compose -f docker-compose.apps.yml up -d --no-deps booking-service:previous_tag

# 2. Nếu database đã bị migrate ngược, sử dụng Flyway rollback hoặc script khôi phục backup
```
