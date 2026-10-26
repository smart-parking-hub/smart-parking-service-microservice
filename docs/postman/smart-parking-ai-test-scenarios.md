# TÀI LIỆU KỊCH BẢN KIỂM THỬ POSTMAN (TEST SCENARIOS)
## PHÂN HỆ: AI INTEGRATION & SMART PARKING HYBRID COPILOT

> **Phiên bản:** 1.0.0 | **Dự án:** SmartParkingHub (OJT-K24)  
> **Người biên soạn:** Senior QA Engineer & Tech Lead  
> **Tệp Collection đính kèm:** [`smart-parking-ai-postman-collection.json`](./smart-parking-ai-postman-collection.json)

---

## 1. THIẾT LẬP MÔI TRƯỜNG KIỂM THỬ (TEST ENVIRONMENT)

### 1.1. Các biến môi trường trong Postman (Variables)
| Tên biến | Giá trị mặc định | Giải thích |
| :--- | :--- | :--- |
| `base_url` | `http://localhost:8088` | Địa chỉ **API Gateway** (Khuyến nghị test qua cổng này để kiểm tra cả định tuyến) |
| `ai_direct_url` | `http://localhost:8086` | Địa chỉ trực tiếp của microservice `ai-service` |
| `user_id` | `1024` | Mã tài xế giả lập (truyền qua header `X-User-Id`) |

### 1.2. Hướng dẫn nạp Collection vào Postman (3 Bước)
1. Mở ứng dụng **Postman** trên máy tính của bạn.
2. Bấm nút **Import** (góc trên bên trái) $\rightarrow$ Chọn tệp tin [`smart-parking-ai-postman-collection.json`](./smart-parking-ai-postman-collection.json).
3. Sau khi nạp, toàn bộ thư mục 4 nhóm API và 12 kịch bản test tự động sẽ sẵn sàng để chạy bằng tay hoặc chạy hàng loạt qua **Collection Runner**.

---

## 2. MA TRẬN KỊCH BẢN KIỂM THỬ (TEST SCENARIO MATRIX)

| Mã Test | Tên Kịch Bản | Phân Loại | Endpoint | Kỳ Vọng (Expected Result) |
| :---: | :--- | :---: | :--- | :--- |
| **TC-01** | Tool Calling: Tìm bãi xe ô tô con | Happy Path | `POST /api/v1/ai/chat` | HTTP 200, `source: "REALTIME_SLOTS"`, trả về danh sách `suggestedLots[]` từ Database |
| **TC-02** | Tool Calling: Nhận diện xe tải nhỏ | Happy Path | `POST /api/v1/ai/chat` | HTTP 200, nhận diện đúng `LIGHT_TRUCK`, trả về bãi xe có slot tải |
| **TC-03** | In-Memory RAG: Hỏi quy định giữ chỗ 10m | Happy Path | `POST /api/v1/ai/chat` | HTTP 200, `source: "POLICY_FAQ"`, trích dẫn điều khoản giữ chỗ 10 phút |
| **TC-04** | In-Memory RAG: Hỏi phí phạt quá giờ | Happy Path | `POST /api/v1/ai/chat` | HTTP 200, `source: "POLICY_FAQ"`, trích dẫn hệ số phạt 1.5x |
| **TC-05** | General: Chào hỏi mở đầu | Happy Path | `POST /api/v1/ai/chat` | HTTP 200, `source: "GENERAL"`, phản hồi giới thiệu SmartParking AI Copilot |
| **TC-06** | Validation: Gửi tin nhắn rỗng | Exception | `POST /api/v1/ai/chat` | HTTP 400 Bad Request, bắt lỗi `@NotBlank` |
| **TC-07** | Sinh mô tả bãi xe cho Merchant | Happy Path | `POST /api/v1/ai/generate-description` | HTTP 200, sinh đủ 3 phần: Marketing, Quy định PCCC, Chỉ dẫn đường đi |
| **TC-08** | Validation: Thiếu tên bãi xe | Exception | `POST /api/v1/ai/generate-description` | HTTP 400 Bad Request, báo lỗi tên bãi xe không được để trống |
| **TC-09** | Sinh Synthetic Mock Data bãi xe | Happy Path | `POST /api/v1/ai/generate-mock-data` | HTTP 200, trả về mảng 5 bãi xe với tọa độ GPS thực tế |
| **TC-10** | Validation: Count vượt quá giới hạn (>20) | Exception | `POST /api/v1/ai/generate-mock-data` | HTTP 400 Bad Request, bắt lỗi `@Max(20)` |
| **TC-11** | Health Check Actuator | Observability| `GET /actuator/health` | HTTP 200, `status: "UP"` |
| **TC-12** | Prometheus Metrics Actuator | Observability| `GET /actuator/prometheus` | HTTP 200, chứa các chỉ số JVM và HTTP requests |

---

## 3. CHI TIẾT REQUEST & CURL MẪU TỪNG KỊCH BẢN

### Kịch bản 1: Tìm bãi đỗ xe thông qua Tool Calling (Tài xế)
* **Mục đích:** Kiểm tra khả năng AI tự phân tích câu hỏi tự nhiên để gọi Tool nội bộ sang `parking-lot-service`.
* **Method:** `POST`
* **URL:** `http://localhost:8088/api/v1/ai/chat`
* **Headers:**
  ```http
  Content-Type: application/json
  X-User-Id: 1024
  ```
* **Request Body:**
  ```json
  {
    "message": "Tìm bãi xe ô tô gần Landmark 81 còn slot trống giúp tôi",
    "userLat": 10.795000,
    "userLon": 106.721000
  }
  ```
* **cURL tương đương:**
  ```bash
  curl -X POST http://localhost:8088/api/v1/ai/chat \
    -H "Content-Type: application/json" \
    -H "X-User-Id: 1024" \
    -d "{\"message\":\"Tìm bãi xe ô tô gần Landmark 81 còn slot trống giúp tôi\",\"userLat\":10.795,\"userLon\":106.721}"
  ```

---

### Kịch bản 2: Tra cứu chính sách trong kho In-Memory Knowledge Store
* **Mục đích:** Kiểm tra khả năng quét kho văn bản chính sách `parking-policies-faq.md` ngay trên RAM.
* **Method:** `POST`
* **URL:** `http://localhost:8088/api/v1/ai/chat`
* **Request Body:**
  ```json
  {
    "message": "Quy định giữ chỗ 10 phút và hủy vé trước giờ check in thế nào?"
  }
  ```
* **cURL tương đương:**
  ```bash
  curl -X POST http://localhost:8088/api/v1/ai/chat \
    -H "Content-Type: application/json" \
    -d "{\"message\":\"Quy định giữ chỗ 10 phút và hủy vé trước giờ check in thế nào?\"}"
  ```

---

### Kịch bản 3: Tự động sinh mô tả bãi xe (Chủ bãi Merchant)
* **Mục đích:** Kiểm tra tính năng hỗ trợ Merchant tạo hồ sơ bãi đỗ xe chuẩn SEO và đúng quy chuẩn PCCC.
* **Method:** `POST`
* **URL:** `http://localhost:8088/api/v1/ai/generate-description`
* **Request Body:**
  ```json
  {
    "lotName": "Bãi Đỗ Xe An Khánh Thủ Đức",
    "address": "120 Song Hành, P. An Phú, TP. Thủ Đức, TP.HCM",
    "totalCapacity": 75,
    "supportedVehicleTypes": ["SEDAN_SUV", "LIGHT_TRUCK"],
    "amenities": ["Camera 24/7", "Có mái che", "Trạm sạc EV"],
    "nearbyLandmarks": "Đối diện Mega Market An Phú, cách cầu Sài Gòn 1km"
  }
  ```
* **cURL tương đương:**
  ```bash
  curl -X POST http://localhost:8088/api/v1/ai/generate-description \
    -H "Content-Type: application/json" \
    -d "{\"lotName\":\"Bãi Đỗ Xe An Khánh Thủ Đức\",\"address\":\"120 Song Hành, TP. Thủ Đức\",\"totalCapacity\":75,\"supportedVehicleTypes\":[\"SEDAN_SUV\"],\"amenities\":[\"Camera 24/7\",\"Trạm sạc EV\"]}"
  ```

---

### Kịch bản 4: Tự động sinh Synthetic Mock Data (Admin & Tester)
* **Mục đích:** Tạo ra danh sách bãi xe có tọa độ GPS thật để nạp vào cơ sở dữ liệu hoặc hiển thị lên bản đồ Leaflet.
* **Method:** `POST`
* **URL:** `http://localhost:8088/api/v1/ai/generate-mock-data`
* **Request Body:**
  ```json
  {
    "targetArea": "Quận 1, TP.HCM",
    "count": 5,
    "vehicleTypes": ["SEDAN_SUV", "LIGHT_TRUCK"]
  }
  ```
* **cURL tương đương:**
  ```bash
  curl -X POST http://localhost:8088/api/v1/ai/generate-mock-data \
    -H "Content-Type: application/json" \
    -d "{\"targetArea\":\"Quận 1, TP.HCM\",\"count\":5}"
  ```
