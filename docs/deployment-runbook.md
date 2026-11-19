# Deployment runbook — các đường vận hành hiện hành

Cập nhật ngày 2026-11-19. Bản đồ này thay các ví dụ cũ trỏ tới Compose ở `docker/`. Phần Compose/Casso của nhánh chọn lọc này chưa triển khai VPS. Bản sửa deploy trước đó đã chạy thành công, có bằng chứng bên dưới.

## Local và kiểm thử cấu hình

Từ backend root, xem [Compose guide](../docker/compose/README.md) để chọn tổ hợp. Kiểm tra cấu hình bằng Python 3.10+ và Docker Compose:

```powershell
python scripts/build-test/check-compose.py
python -m unittest discover -s scripts/build-test/tests -v
```

Bộ kiểm tra dùng env giả, kiểm build/bind path và các hợp đồng cấu hình. Tệp bind trên VPS được xác minh tại host riêng. Các trường hợp Large và layout sai topology phải bị từ chối. Đây là kiểm tra model; kiểm chứng migration và luồng nghiệp vụ là bước riêng.

## Cluster đang được các công cụ deploy tham chiếu

| Node | File dưới `docker/compose/vps-cluster/` | Thành phần |
| --- | --- | --- |
| Edge | `compose.vps1-edge.yml` | Nginx/frontend, Gateway, Identity, Redis edge |
| Data | `compose.vps2-data.yml` | MySQL, Kafka, Payment, Notification, AI |
| Core | `compose.vps3-core.yml` | Parking, Booking, Redis core |

- Lối thủ công: [deploy-vps.ps1 và hướng dẫn](../scripts/deploy/README.md), mapping tại `scripts/deploy/deploy.config.json`.
- Lối CI/CD: [cd-deploy.yml](../.github/workflows/cd-deploy.yml), dùng project `docker` và env file đã provision trên host.
- Menu 1.7 chọn Local build hoặc GitHub CI, cả hai dùng helper khóa host, pin digest và kiểm health; xem [khôi phục và phát hành bản sửa](implementation/vps2-recovery-20261119.md). Bản sửa deploy đã có ở main `2f56c82`; CD `44564988649` và kiểm tra độc lập đạt 7/7 CURRENT. Trước thao tác mới, dùng 1.9 để kiểm lại và xác định node, artifact, env, DB/schema và mount hiện hành. Thư mục Nginx/frontend/MySQL được mount theo vị trí Compose hiệu lực; đối chiếu host thực vì không phải mọi đường dẫn đó đều có trong checkout.
- Layout filesystem cần ghép đúng overlay Core và volume external theo [runbook](deployment/parking-layout-filesystem.md). Cấu hình local có key service khác.
- `compose.vps-r4.yaml` và `compose.vps-large.yaml` không thay thế topology cluster. Large hiện không ghép được với base do xung đột replicas/container_name. Hạn chế R4 được ghi trong [audit](BACKEND-COMPOSE-AUDIT.md).

## Kiểm chứng trước và sau triển khai đã được phê duyệt

Ghi image/tag/digest, Compose file order, project, env-file, network, các mount và Flyway history trước thao tác. Kiểm health từng service mục tiêu và một luồng thật phù hợp với thay đổi. Dùng outbox/consumer/reconciliation để đối chiếu trạng thái khi có luồng bất đồng bộ. Một lệnh `up` thành công chưa chứng minh zero downtime hay thành công nghiệp vụ.

Không còn hướng dẫn gọi `/scripts/init-topics.sh` trong Kafka theo ví dụ cũ, vì Compose được kiểm chưa mount script tại đó. Xác nhận bootstrap topic đang dùng trước khi thực hiện tác vụ khởi tạo riêng.

## Migration, dữ liệu giữ lại và rollback

- Lịch: [booking schedule rollout](deployment/booking-schedule-rollout.md), Parking trước Booking tương thích, bảo toàn lịch đã nhận.
- Payment có DB canonical và [lineage local V6/V7](api-contracts/payos-local-lineage.md). Kiểm tra history và artifact để chọn cấu hình đúng.
- P0: giữ launcher `scripts/dev/start-p0-local.bat` cùng thứ tự overlay và artifact pin; kiểm trên bản sao nếu có bài test ghi dữ liệu.
- Ảnh/layout/dispute evidence cần đi cùng backup DB và đúng volume. Xem [backup và disaster recovery](DISASTER_RECOVERY_AND_BACKUP_GUIDE.md).
- Rollback application cần artifact/config đã ghi nhận. Dữ liệu phát sinh và schema đã migrate phải được đối chiếu với phiên bản muốn quay lại. Không có rollback Flyway tự động được bảo đảm bởi script readability hoặc ví dụ đổi tên service. Không dùng cú pháp `booking-service:previous_tag` ở vị trí tên service của `compose up`; image được xác định qua cấu hình Compose.

Quy tắc production và phê duyệt vẫn theo [AGENTS.md](../AGENTS.md) và [Protect production](../.agents/rules/03-protect-production.md).
