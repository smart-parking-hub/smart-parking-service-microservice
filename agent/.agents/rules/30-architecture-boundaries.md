# 30 — Microservices Architecture Boundaries

## Architecture baseline
Tham khảo mục tiêu có thể gồm Nginx, Gateway, business services, MySQL, optional Redis/Kafka, CI/CD, logs/metrics/tracing và backup. Đây KHÔNG phải bằng chứng rằng repo hiện tại đã triển khai các thành phần này.

Đối chiếu `docs/01-architecture/AS-IS.md` trước khi đề xuất thay đổi.

## Ranh giới service
- Chia theo business capability/bounded context, không chia máy móc theo bảng CRUD.
- Mỗi service phải có trách nhiệm, API/event contract, owner dữ liệu và cách triển khai rõ ràng.
- Không cho service A ghi trực tiếp bảng riêng của service B.
- Không tạo distributed transaction SQL xuyên nhiều service.
- Giao tiếp sync khi cần kết quả tức thời; async khi nghiệp vụ chấp nhận eventual consistency.
- Không thêm Kafka/Redis/Eureka/Config Server/Kubernetes nếu chưa nêu use case, chi phí, vận hành và phương án thay thế.
- Không gọi mọi API nội bộ qua Gateway mặc định; luồng nội bộ phải có quy ước rõ.

## Data consistency
- Transaction chỉ bảo đảm trong boundary database/local transaction phù hợp.
- Với event cần tin cậy, đánh giá Transactional Outbox.
- Consumer phải cân nhắc duplicate, retry, ordering và poison message.
- Lệnh thanh toán/đặt hàng phải có idempotency phù hợp.
- Saga cần xác định trạng thái trung gian, timeout, retry và compensating action.
- Không tuyên bố “exactly once” nếu không mô tả được giới hạn kỹ thuật và phạm vi.

## Discovery/configuration
- Docker DNS/Kubernetes DNS/Eureka là các lựa chọn khác nhau; không mặc định dùng cùng lúc.
- Config Server không tự động là secret manager.
- Cấu hình runtime phải có nguồn, môi trường, giá trị mặc định và cách validate rõ.
