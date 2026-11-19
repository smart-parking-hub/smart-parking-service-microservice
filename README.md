# SmartParkingHub - Enterprise Microservices Architecture

Backend mới bắt đầu: [đọc nhanh và chọn điểm vào](docs/BACKEND-START-HERE.md).

Dự án Microservices chuẩn doanh nghiệp ứng dụng Java 21, Spring Boot 3.3.x, Gradle Multi-Project, Docker, Kafka, Redis và MySQL theo nguyên lý Domain-Driven Design (DDD), Event-Driven Architecture và Database-per-Service.

## 1. Cấu trúc Tổng thể Thư mục (Project Directory Tree)

```
smart-parking-microservice/
├── .github/
│   └── workflows/
│       ├── ci-build.yml                  # CI: Compile, test, package bằng JDK 21
│       └── cd-deploy.yml                 # CD: Deploy tự động lên VPS qua Docker
├── docs/                                 # Bộ 12 tài liệu kiến trúc & vận hành thực tế
│   ├── architecture-overview.md          # Sơ đồ tổng thể 6 lớp, phân định ranh giới
│   ├── service-catalog.md                # Danh mục 6 service, port, dependencies
│   ├── business-domains.md               # Bounded contexts & invariants nghiệp vụ
│   ├── business-flows/                   # Chi tiết các luồng: Auth, Booking, Payment...
│   ├── data-ownership.md                 # Quyền sở hữu bảng, nguyên tắc cấm query chéo DB
│   ├── event-catalog.md                  # Danh mục Kafka topics, events, idempotent consumer
│   ├── security-model.md                 # JWT extraction, internal header relay, secrets
│   ├── deployment-runbook.md             # Hướng dẫn deploy VPS, rolling update, rollback
│   ├── observability.md                  # Log correlation, Prometheus metrics, Grafana
│   ├── failure-scenarios.md              # Xử lý timeout, outbox, duplicate event, lock
│   └── adr/                              # Architecture Decision Records (ADR 0001 - 0003)
├── docker/                               # Toàn bộ cấu hình container & hạ tầng
│   ├── compose/local/                    # Preset local, compatibility entries và capability files
│   ├── compose/overlays/                 # P0, SMTP và PayOS lineage
│   ├── compose/vps-cluster/              # Ba node VPS: Edge, Data và Core
│   ├── nginx/                            # Nginx reverse proxy, static SPA & SSL
│   ├── prometheus/                       # Scrape configs /actuator/prometheus
│   ├── grafana/                          # Dashboards & Datasources
│   └── kafka/                            # Topic init scripts
├── scripts/                              # Batch & shell script tiện ích
│   ├── build-all.bat
│   ├── start-infra.bat
│   └── start-services.bat
├── common-library/                       # Shared Library: ApiResponse, Events, Exceptions, Security
├── api-gateway/                          # Edge Layer (:8088): Spring Cloud Gateway, JWT filter, Tracing
├── identity-service/                     # Business Service (:8081): Auth, Users, Drivers, Merchants
├── parking-lot-service/                  # Business Service (:8082): Bãi đỗ xe, Slot, Giá, Cache Redis
├── booking-service/                      # Business Service (:8083): Đặt chỗ, Giữ chỗ (Hold), Check-in/out, Outbox
├── payment-service/                      # Business Service (:8084): Thanh toán PayOS, Ví, Double-entry Ledger
├── notification-service/                 # Business Service (:8085): Kafka Consumer, Email, Push, SMS
├── build.gradle                          # Root Gradle Build cấu hình Java 21 & Spring Boot 3.3.5
├── settings.gradle                       # Gradle Module Settings
└── .env.example                          # Mẫu cấu hình biến môi trường
```

## 2. Bảng Phân chia Cổng và Database theo Service

| Service | Port | Database Schema | Công nghệ bổ sung |
| :--- | :--- | :--- | :--- |
| **api-gateway** | 8088 | Không | Spring Cloud Gateway, Reactive Redis |
| **identity-service** | 8081 | `smart_parking_identity_db` | Spring Security, JWT, Flyway |
| **parking-lot-service** | 8082 | `smart_parking_lot_db` | Spring Data Redis, Flyway |
| **booking-service** | 8083 | `smart_parking_booking_db` | Spring Kafka, OpenFeign, Outbox, Flyway |
| **payment-service** | 8084 | `smart_parking_payment_db` | PayOS Java SDK, Spring Kafka, Outbox, Flyway |
| **notification-service** | 8085 | `smart_parking_notification_db` | Spring Kafka, Spring Mail, Flyway |

## 3. Khởi chạy Nhanh trên Máy Local / VPS

Từ backend root, Windows có thể dùng launcher tích hợp mới:

```powershell
.\scripts\dev\start-local-integration.bat
```

Chế độ này chạy MySQL/Redis/Kafka và bảy app, dùng cùng dữ liệu/volume của local-dev. Các launcher cũ vẫn còn. Xem [các cách chạy Compose](docker/compose/README.md) để chọn dev/full, monitoring, SonarQube, OTP hoặc P0 giữ dữ liệu. Kiểm tra cấu hình bằng `python scripts/build-test/check-compose.py` với Python 3.10+ và Docker Compose, dùng biến giả và không khởi động container.

VPS dùng [deploy launcher](scripts/deploy/README.md) và các file cluster hiện hành; bản đồ vận hành nằm trong [deployment runbook](docs/deployment-runbook.md).
