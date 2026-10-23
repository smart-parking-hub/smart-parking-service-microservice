# ADR 0001: Phân rã Hệ thống theo Bounded Context (Microservices Decomposition)

## Trạng thái (Status)
Đã chấp thuận (Accepted) - 2026-10-23

## Bối cảnh (Context)
Hệ thống quản lý bãi đỗ xe thông minh SmartParkingHub trước đây được phát triển dưới dạng monolith (`smart-parking-service`). Khi mở rộng quy mô phục vụ nhiều bãi xe thời gian thực với lượng tài xế đặt chỗ và quét mã thanh toán tăng cao, hệ thống nguyên khối gặp khó khăn về kiểm soát lock đồng thời, scale độc lập và triển khai không gián đoạn.

## Quyết định (Decision)
Tách hệ thống thành 5 core microservices và 1 API Gateway dựa trên Bounded Context (DDD):
1. `api-gateway`: Điều hướng, bảo mật biên, rate limit.
2. `identity-service`: Quản lý danh tính, hồ sơ tài xế và chủ bãi xe.
3. `parking-lot-service`: Quản lý danh mục bãi đỗ xe, sơ đồ chỗ và trạng thái thực tế.
4. `booking-service`: Quản lý vòng đời đặt chỗ, giữ chỗ và điều phối check-in/out.
5. `payment-service`: Quản lý thanh toán trực tuyến, ví và sổ cái tài chính kép.
6. `notification-service`: Xử lý thông báo bất đồng bộ đa kênh.

## Hệ quả (Consequences)
- **Tích cực**: Mỗi service triển khai và mở rộng quy mô độc lập; domain nghiệp vụ tách bạch; lỗi ở 1 service không làm sập toàn bộ hệ thống.
- **Tiêu cực**: Tăng chi phí vận hành hạ tầng; cần giải quyết bài toán nhất quán dữ liệu phân tán (Eventual Consistency) qua Kafka và Saga.
