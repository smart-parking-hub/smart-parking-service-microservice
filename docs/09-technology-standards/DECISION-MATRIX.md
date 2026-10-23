# Ma Trận Quyết Định Công Nghệ & Thiết Kế (Technology Decision Matrix)

Ma trận này phân loại rõ: **Những gì nên làm sớm**, **Những gì chỉ làm khi có nhu cầu**, và **Những gì không được làm theo mặc định**.

---

## 1. Ma Trận Công Nghệ (Technology Matrix)

| Nhóm | Công nghệ / Thiết kế | Lý do áp dụng / Tiêu chí kích hoạt |
| :--- | :--- | :--- |
| **Nên áp dụng sớm (Baseline)** | • Java 21 LTS + Spring Boot 3.3.x<br>• Gradle Wrapper (`gradlew`)<br>• Docker Compose multi-file overlays<br>• Nginx reverse proxy<br>• Spring Security Stateless<br>• API-First + OpenAPI 3 (SpringDoc)<br>• Flyway Database Migration<br>• Unit & Integration Test tự động<br>• GitHub Actions CI/CD<br>• Log có cấu trúc (JSON + Correlation ID)<br>• Spring Boot Actuator / Prometheus metrics | Nền tảng thiết yếu để kiểm soát chất lượng, tính tái lặp (reproducibility), và khả năng vận hành ngay từ vạch xuất phát. |
| **Chỉ có khi nhu cầu xuất hiện (On-Demand)** | • Apache Kafka<br>• Redis / Valkey Cache & Distributed Lock<br>• Spring Cloud Gateway<br>• Transactional Outbox & Saga Orchestrator<br>• Testcontainers (cho integration test sát môi trường)<br>• Spring Cloud Contract / Pact<br>• Distributed Tracing (Tempo / Jaeger)<br>• Grafana Dashboards + Alerting<br>• Secret Manager tập trung (Vault) | Bật khi có use case đo lường được: ví dụ khi có giao dịch tài chính webhook bất đồng bộ (cần Kafka + Outbox), khi có race condition đặt trùng slot xe (cần Redis Lock). |
| **Không nên làm theo mặc định (Avoid by Default)** | • Triển khai Kubernetes cho cụm 1 VPS đơn lẻ<br>• Bổ sung Eureka Server khi Docker DNS nội bộ đã giải quyết tốt<br>• Dùng Kafka cho mọi giao tiếp (thay vì REST khi cần kết quả ngay)<br>• Chia service quá nhỏ (Nano-services: 1 bảng SQL = 1 service)<br>• Sử dụng nhiều ngôn ngữ / framework khác nhau chỉ để trải nghiệm<br>• Đổi database trong lúc đang hotfix một bug<br>• Dùng tag `:latest` làm định danh duy nhất cho image production | Làm tăng chi phí vận hành, phức tạp hóa hạ tầng, tiêu tốn RAM mà không mang lại giá trị nghiệp vụ tương xứng. |

---

## 2. Ma Trận Thiết Kế Bắt Buộc Trong Microservices

| Thiết kế | Trạng thái | Agent bắt buộc phải kiểm tra |
| :--- | :--- | :--- |
| **Bounded Context / DDD** | Bắt buộc | Ranh giới nghiệp vụ, ai là owner dữ liệu, các service có bị phụ thuộc vòng tròn không? |
| **Hexagonal / Clean Architecture**| Nên áp dụng | Domain logic có bị phụ thuộc trực tiếp vào Spring JPA, Kafka annotations hoặc HTTP framework không? |
| **Database Ownership** | Bắt buộc | Có service nào query trực tiếp hoặc JOIN chéo sang bảng của service khác không? |
| **Transactional Outbox** | Bắt buộc với Event quan trọng | Có rủi ro DB commit thành công nhưng bắn Kafka event thất bại gây mất dữ liệu không? |
| **Idempotency** | Bắt buộc với Payment & Consumer | Nếu webhook PayOS gửi lặp lại 3 lần, hệ thống có bị cộng tiền 3 lần không? |
| **Resilience (Timeout + Circuit Breaker)** | Bắt buộc cho Network calls | Mọi Feign call có timeout không? Có nguy cơ cascade failure khi service downstream bị treo không? |
| **Expand/Contract Migration** | Bắt buộc khi đổi schema | Phiên bản app cũ và mới có thể cùng chạy song song trong lúc rollout không? |
