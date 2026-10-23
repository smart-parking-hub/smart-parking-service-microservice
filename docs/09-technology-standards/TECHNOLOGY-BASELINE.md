# Technology Baseline & Version Verification Standards

Tài liệu này xác lập đường cơ sở công nghệ (Baseline) cho SmartParkingHub và quy tắc bắt buộc Agent phải đối chiếu với tài liệu chính thức trước khi lựa chọn phiên bản.

---

## 1. Triết Lý Cốt Lõi: Phù Hợp > Mới Nhất
> **"Công nghệ mới nhất, kiến trúc tốt nhất và kiến trúc phù hợp nhất không phải lúc nào cũng là một."**
> AI Agent phải biết đề xuất công nghệ mới khi có lợi ích cụ thể, đồng thời phải dứt khoát từ chối những công nghệ làm hệ thống phức tạp hơn mà không đem lại giá trị đo lường được.

---

## 2. Đường Cơ Sở Công Nghệ (Technology Baseline)

### 2.1. Nền tảng Cốt lõi: Java & Spring Boot
- **Dự án Hiện tại (Brownfield / Migration):**
  - **Runtime:** Java 21 LTS (`build.gradle`: `toolchain { languageVersion = JavaLanguageVersion.of(21) }`).
  - **Framework:** Spring Boot 3.3.x / 3.4.x (hiện tại 3.3.5) với Spring Cloud tương thích `2023.0.x`.
  - **Build tool:** Gradle 8.x / 9.x qua Gradle Wrapper (`gradlew`).
- **Đánh giá Greenfield tương lai:**
  - Khi bắt đầu một module/service mới độc lập hoặc nâng cấp toàn diện, Agent có thể đánh giá Java 25 + Spring Boot 4.x (nếu phiên bản đã đạt chuẩn GA ổn định và Spring Cloud có release train tương thích).
  - **Cấm Tuyệt Đối:** Không được tự ý nâng cấp major version (ví dụ: từ Spring Boot 3 lên 4) trong lúc đang sửa một bug nghiệp vụ thông thường.

### 2.2. Cơ Sở Dữ Liệu: MySQL vs PostgreSQL
- **Nguyên tắc chọn:** Dự án hiện tại đang dùng MySQL 8.4 LTS, ưu tiên giữ đúng phiên bản đang vận hành ổn định.
- **Dự án/Module mới:** Có thể đánh giá PostgreSQL (16.x / 17.x / 18.x) nếu cần các tính năng chuyên sâu như JSONB indexing mạnh mẽ, Geolocation (PostGIS) cho định vị bãi xe.
- **Luật:** Không bao giờ đổi Database chỉ vì muốn dùng công nghệ mới.

### 2.3. Event Streaming: Apache Kafka & Transactional Outbox
- **Ứng dụng:** Event-driven architecture cho các luồng xử lý bất đồng bộ (Thanh toán Webhook PayOS, Xác nhận giữ chỗ, Gửi email/SMS/Push notification).
- **Nguyên tắc phiên bản:** Chọn broker và client tương thích (nhánh 3.7+ hoặc 4.x KRaft mode).
- **Yêu cầu bắt buộc:** Đi kèm Transactional Outbox Pattern ở phía Publisher và Idempotency (bảng `processed_events`) ở phía Consumer.

### 2.4. Khả Năng Quan Sát: OpenTelemetry + Micrometer + Prometheus + Grafana
- **Nguyên tắc:** OpenTelemetry là chuẩn thu thập telemetry trung lập; cần kết hợp Prometheus làm kho lưu trữ time-series metrics và Grafana để trực quan hóa + cảnh báo theo ngưỡng (Alerting).
- **Log định dạng cấu trúc:** Mọi log đều chứa `X-Correlation-Id`, `service_name`, `timestamp`.

### 2.5. Bảo Mật: Spring Security + OAuth2/OIDC
- Xác thực JWT stateless tại API Gateway, chuyển tiếp thông tin định danh qua internal headers (`X-User-Id`, `X-User-Role`, `X-Correlation-Id`).
- Phân quyền tại Backend (`@PreAuthorize`), kiểm tra quyền sở hữu tài nguyên (User chỉ được truy cập dữ liệu của chính mình).
- Tuân thủ khuyến nghị từ **OWASP API Security Top 10**.

---

## 3. Quy Trình Kiểm Tra Tính Tương Thích Của Agent
Trước khi đề xuất thêm bất kỳ thư viện hoặc đổi phiên bản, Agent phải:
1. Kiểm tra tài liệu chính thức của Spring Boot System Requirements.
2. Kiểm tra ma trận tương thích giữa Spring Boot và Spring Cloud (Spring Cloud Release Train).
3. Đánh giá tác động đến kích thước Docker image và dung lượng RAM tiêu thụ trên môi trường VPS R4.
