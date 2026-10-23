# SmartParkingHub - Enterprise Microservices Architecture

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
│   ├── docker-compose.yml                # File tổng hợp
│   ├── docker-compose.infra.yml          # MySQL 8.4, Redis 7.2, Kafka KRaft, Prometheus, Grafana
│   ├── docker-compose.apps.yml           # 6 microservices container
│   ├── nginx/                            # Nginx reverse proxy, static SPA & SSL
│   ├── prometheus/                       # Scrape configs /actuator/prometheus
│   ├── grafana/                          # Dashboards & Datasources
│   └── kafka/                            # Topic init scripts
├── scripts/                              # Batch & shell script tiện ích
│   ├── build-all.bat
│   ├── start-infra.bat
│   └── start-services.bat
├── common-library/                       # Shared Library: ApiResponse, Events, Exceptions, Security
├── api-gateway/                          # Edge Layer (:8080): Spring Cloud Gateway, JWT filter, Tracing
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
| **api-gateway** | 8080 | Không | Spring Cloud Gateway, Reactive Redis |
| **identity-service** | 8081 | `smart_parking_identity_db` | Spring Security, JWT, Flyway |
| **parking-lot-service** | 8082 | `smart_parking_lot_db` | Spring Data Redis, Flyway |
| **booking-service** | 8083 | `smart_parking_booking_db` | Spring Kafka, OpenFeign, Outbox, Flyway |
| **payment-service** | 8084 | `smart_parking_payment_db` | PayOS Java SDK, Spring Kafka, Outbox, Flyway |
| **notification-service** | 8085 | `smart_parking_notification_db` | Spring Kafka, Spring Mail, Flyway |

## 3. Khởi chạy Nhanh trên Máy Local / VPS

```bash
# Bước 1: Khởi động Hạ tầng
cd docker
docker compose -f docker-compose.infra.yml up -d

# Bước 2: Build toàn bộ mã nguồn
cd ..
./gradlew build -x test

# Bước 3: Khởi động các Microservices
cd docker
docker compose -f docker-compose.apps.yml up -d --build
```
