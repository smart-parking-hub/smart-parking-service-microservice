# SmartParkingHub — các cách chạy Compose

Các lệnh trong trang này chạy từ **repo backend root**, trừ khi ghi khác. Tên file và launcher cũ được giữ. Đợt bảo trì ngày 2026-11-19 sửa đường dẫn legacy và tách định nghĩa dùng lại. Project, container, network, volume và môi trường app của các preset cũ được đối chiếu trước/sau.

## Kiểm tra trước khi chạy

```powershell
python scripts/build-test/check-compose.py
python -m unittest discover -s scripts/build-test/tests -v
# Wrapper Windows chạy được từ thư mục bất kỳ:
.\scripts\build-test\check-compose.bat --scenario integration
```

Cần Python 3.10+ và Docker Compose plugin hỗ trợ `config --format json`, `extends` và `include`. Checker dùng biến giả, bỏ qua `.env` của repo và chỉ render cấu hình. PASS với `expected rejection` chứng minh tổ hợp không hỗ trợ bị từ chối. Các bind path trên VPS cần kiểm tra trên host riêng.

## Chọn cách chạy

Các path trong bảng nằm dưới `docker/compose/`.

| Mục đích | File sau `local/compose.yaml` | Ghi chú |
| --- | --- | --- |
| Local dev như trước | `local/compose.local.yaml` | MySQL/Redis và bảy app. Luồng event cần broker đang chạy hoặc chọn integration. |
| Integration vừa đủ | `local/compose.local.yaml`, `local/compose.kafka.yaml` | Thêm Kafka, không cần monitoring hoặc discovery/config. |
| Full lab | `local/compose.local.yaml`, `local/compose.local-full.yaml` | Model có Kafka, Prometheus/Grafana và Eureka/Config. |
| Quan sát | `local/compose.local.yaml`, `local/compose.monitoring.yaml` | Thêm Prometheus/Grafana vào local. |
| Lab discovery/config | `local/compose.local.yaml`, `local/compose.discovery.yaml` | Chạy server; cấu hình client mặc định giữ nguyên. |
| Layout filesystem | `local/compose.local.yaml`, `local/compose.layout-filesystem.yaml` | External volume và flags theo [runbook](../../docs/deployment/parking-layout-filesystem.md). |

`compose.local-full.yaml` tham chiếu các capability Kafka, monitoring và discovery. MySQL/Redis lấy từ `compose.infra.yaml`. SonarQube nằm riêng ở `compose.quality.yaml`. Các capability có build/bind path được dùng cùng `--project-directory .` từ backend root. Các preset `extends` có override path cần thiết do cơ chế rebase của Compose; giữ các override này khi bảo trì.

## Launcher Windows

- `scripts/dev/start-local-dev.bat`: giữ hành vi local-dev trước đây.
- `scripts/dev/start-local-integration.bat`: opt-in MySQL/Redis/Kafka và bảy app; nhận `-SkipBuild`, `-Service payment` qua runner hiện có.
- `scripts/dev/start-local-full.bat`: giữ hành vi runner trước đây: mở MySQL/Redis/Kafka rồi bảy app. Dùng lệnh full bên dưới nếu muốn mở cả monitoring và discovery/config.
- `scripts/dev/start-infra.bat`: vẫn dùng preset infra cũ, có cả monitoring và SonarQube.
- `scripts/quality/start-sonarqube.bat`: tiếp tục mở riêng SonarQube qua entry cũ.

Các launcher build JAR và recreate container. Chế độ integration dùng cùng project/network/port của lab mặc định. Nhánh cleanup/reset/readiness của runner giữ hành vi cũ trong lần tách cấu hình này.

## Lệnh Compose trực tiếp

```powershell
# Integration: cùng app, DB và mount như local-dev, bổ sung Kafka.
docker compose -p docker --project-directory . -f docker/compose/local/compose.yaml -f docker/compose/local/compose.local.yaml -f docker/compose/local/compose.kafka.yaml up -d --build

# Full: mở tất cả service trong model, gồm monitoring và discovery/config.
docker compose -p docker --project-directory . -f docker/compose/local/compose.yaml -f docker/compose/local/compose.local.yaml -f docker/compose/local/compose.local-full.yaml up -d --build

# SonarQube độc lập; launcher cũ vẫn dùng được.
docker compose -p docker --project-directory . -f docker/compose/local/compose.quality.yaml up -d sonarqube
```

Trước khi chạy, xác nhận môi trường và `.env` mong muốn. Hai họ app cũ/mới giữ mặc định DEMO khác nhau. Một biến trong `.env` chỉ được truyền vào app nếu Compose có cấu hình tương ứng.

## Tương thích file cũ

`local/docker-compose.infra.yml` dùng lại capability, giữ override Kafka, mount Grafana và volume Sonar của riêng preset. Bind paths đã sửa để trỏ đúng asset trong repo. `local/docker-compose.apps.yml` sửa build context và bổ sung năm biến Casso chỉ cho Identity, mặc định tắt/để trống; env/mount khác giữ nguyên. `local/docker-compose.yml` còn include cả hai. Tên project mặc định của file include là `local` trên CLI đã kiểm, còn các file standalone và launcher thường dùng `docker`. Giữ project hiện hành khi tiếp tục dữ liệu đã có.

## P0, migration và dữ liệu giữ lại

`start-p0-local.bat`, thứ tự restore/schedule/no-request-rate và các tag pin được giữ nguyên. Đây là đường dùng lại artifact/hạ tầng có sẵn. `compose.payos-local-v6-v7.yaml` chỉ dành cho DB Payment mang lineage tương ứng. Xem [rollout lịch](../../docs/deployment/booking-schedule-rollout.md) và [lineage PayOS](../../docs/api-contracts/payos-local-lineage.md) trước khi nâng artifact hoặc kiểm migration.

Ảnh bãi local-dev dùng `docker_parking_lot_images`, overlay lịch P0 chọn `docker_parking_images`. Dispute evidence dùng `docker_payment_uploads`. Kafka giữ nguyên log-dir và mount như trước; lần này chưa di chuyển dữ liệu broker.

## VPS và tổ hợp chưa hỗ trợ hoàn chỉnh

Edge/Data/Core, deploy launcher và CD được giữ nguyên. Kiểm tra bind path tại host thực. Layout cluster dùng key service khác local nên có file riêng. R4 còn hạn chế trong [audit](../../docs/BACKEND-COMPOSE-AUDIT.md). Large hiện xung đột replicas/container_name khi ghép base; checker giữ bài kiểm tra từ chối này. Lần bảo trì local chưa thay đổi triển khai VPS.

## Test nhiều trường hợp

Dùng integration để test event/outbox. Thêm Mailpit cho OTP hoặc bật SMTP thật có chủ đích bằng overlay mail áp dụng cuối. Kiểm PayOS fresh/retained trên schema riêng. Kiểm layout với volume có/thiếu và quyền filesystem. Lab song song phải tách cả tên container, port, network, schema và volume: chỉ đổi `-p` chưa đủ.

Xem [audit và ma trận test](../../docs/BACKEND-COMPOSE-AUDIT.md), [kế hoạch và kết quả](../../docs/implementation/backend-publish-selection-20261119.md).
