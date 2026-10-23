# Rule 05: Tiêu Chuẩn Mã Nguồn Java 21 & Spring Boot 3.x (Java/Spring Standards)

## 1. Công Nghệ & Nền Tảng
- **Ngôn ngữ:** Java 21 LTS. Tận dụng tối đa các tính năng hiện đại: `record`, Pattern Matching for `switch`, Sealed Interfaces, Text Blocks, Virtual Threads (nếu phù hợp).
- **Framework:** Spring Boot 3.3.x / 3.4.x, Spring Cloud 2023.x / 2024.x.
- **Build tool:** Gradle Multi-Project (v8.x hoặc v9.x).

## 2. Cấu Trúc Phân Lớp Sạch (Clean / Hexagonal Layering trong Service)
Bên trong mỗi microservice, mã nguồn phải được tổ chức theo các package tách bạch:
- `api/`: Controllers, Request/Response DTOs, Bean Validation (`@Valid`, `@NotNull`...). Không chứa logic tính toán giá hoặc truy vấn database.
- `application/`: Use Cases, Service orchestration, Saga coordinators, Transaction boundary (`@Transactional`).
- `domain/`: Domain Models, Business Rules, Invariants, Custom Domain Exceptions, Domain Event definitions. Độc lập với framework bên ngoài.
- `infrastructure/`: Spring Data JPA Entities & Repositories, Kafka Producers/Consumers, OpenFeign Clients, Cache Redis Adapter, Outbox Publisher.
- `config/`: SecurityConfig, OpenApiConfig, KafkaConfig, RedisConfig.

## 3. Quy Tắc Lập Trình Bắt Buộc
- **Không viết God Classes:** Tách nhỏ các service chuyên biệt, tuân thủ Single Responsibility Principle (SRP).
- **Bắt ngoại lệ toàn cục:** Sử dụng `@RestControllerAdvice` trả về định dạng chuẩn `ApiResponse<T>` hoặc `ErrorResponse` đồng nhất.
- **Không nuốt ngoại lệ:** Cấm viết `catch (Exception e) {}` để trống hoặc chỉ in `e.printStackTrace()`. Phải ghi log có ý nghĩa và ném ngoại lệ tầng trên xử lý.
