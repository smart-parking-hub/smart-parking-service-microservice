# Architecture TO-BE (mục tiêu đề xuất)

> Đây là đề xuất, không phải trạng thái đang chạy. Mọi thay đổi cần ADR/approval.

## VPS target profile
- CPU: 6 vCPU (theo gói VPS được cung cấp; cần xác minh thực tế)
- RAM: 12 GB
- Disk: 80 GB
- Network: 100 Mbps
- Host count: 1 (single-host failure domain)
- Backup: nhà cung cấp quảng cáo backup hằng ngày; phải xác minh retention, scope, restore và không thay thế backup độc lập.

## Baseline đề xuất
- Nginx: TLS, static frontend, reverse proxy.
- Spring Cloud Gateway: chỉ khi cần API edge riêng.
- Business services: tách theo bounded context, không theo từng bảng.
- MySQL/PostgreSQL: theo stack đã chọn; không đổi DB chỉ vì template.
- Docker Compose: triển khai một host.
- CI/CD: build/test/image/deploy có phê duyệt và rollback.
- Logs/metrics/health checks: bắt đầu nhẹ.
- Redis/Kafka/Config Server/Eureka: optional theo use case và ngân sách tài nguyên.
- Kubernetes: chưa mặc định cho một VPS đơn lẻ.

## Resource policy
- Đo RSS/CPU/disk trước khi đặt limits.
- Không cộng heap JVM như thể đó là tổng RAM của process.
- Giữ buffer cho OS, native memory, page cache và tải tăng.
- Đặt giới hạn log/image retention.
- Không đặt backup duy nhất trên cùng VPS.
- Không hứa tải concurrent/users trước load test.

## Design decisions cần ghi ADR
- Service boundaries:
- Database ownership:
- Sync vs async:
- Gateway/discovery:
- Secrets:
- Observability:
- Deployment/rollback:
- RPO/RTO and backup:
