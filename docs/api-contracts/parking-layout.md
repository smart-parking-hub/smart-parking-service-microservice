# Parking Layout & Physical Slot Booking API Contract

- **Services**: `parking-lot-service` (Layout management, assets, physical slots, availability) & `booking-service` (Physical slot hold/snapshot)
- **Base Paths**:
  - Merchant: `/api/v1/merchant/parking-lots/{id}/layout`
  - Public: `/api/v1/parking-lots/{id}/layout`
  - Internal RPC: `/api/v1/internal/parking-lots/{id}/slots`
- **Sprint / Task**: Sprint 3 / `SP-S3-L04-BE`
- **Data Owners**:
  - `parking-lot-service`: `parking_layouts`, `parking_layout_assets`, `parking_layout_publications`, `physical_parking_slots`, `slot_holds`, `current_slot_allocations`, private filesystem volume (source images & versioned JSON layouts; ADR 0006). API paths/payloads are unchanged by the provider switch.
  - `booking-service`: `bookings` (physical slot snapshot columns: `physical_slot_id`, `physical_slot_code`, `layout_publication_id`).

---

## 1. Định danh & Mô hình Dữ liệu (Key Identifiers)

| Tên trường | Kiểu dữ liệu | Ý nghĩa & Quy tắc |
| :--- | :--- | :--- |
| `slotGroupId` | `Long` | Khóa chính của `slots` (nhóm sức chứa hiện hành của một `vehicleType`, `totalCapacity >= 1`). Giữ nguyên ý nghĩa của `Slot.id` cũ. |
| `physicalSlotId` | `UUID` (String) | Định danh bất biến của một vị trí đỗ vật lý cụ thể trong bãi do server cấp. Không thay đổi khi chỉnh sửa hình học/tọa độ. Không tái sử dụng cho ô khác. |
| `elementId` | `String` | Định danh client-side của phần tử đồ họa trong layout document (do editor hoặc AI sinh, độc lập với tọa độ `(row, col)`). |
| `code` | `String` | Nhãn hiển thị của ô đỗ (ví dụ "A-01", "B-02"). Unique trong bãi sau khi trim và so sánh không phân biệt hoa thường (`case-insensitive`). Không dùng `code` làm khóa ngoại booking. |
| `sourceAssetId` | `UUID` (String) | Định danh asset ảnh nguồn do server quản lý trong private file storage. Quyền truy cập qua endpoint xác thực của BE, không nhận URL tùy ý. |
| `layoutVersion` | `Integer` | Phiên bản số của bản nháp (optimistic concurrency control). Tăng dần mỗi khi cập nhật nháp hoặc công bố. |
| `publicationId` | `UUID` (String) | Định danh snapshot công bố bất biến (`parking_layout_publications`). Snapshot JSON được lưu qua StoragePort, mặc định trên private filesystem volume. |

### Các loại ô đỗ và phần tử (Cell Types)
- **Có tính sức chứa đỗ xe (`PARKING_SLOT`)**: Chỉ các element có `cellType = "PARKING_SLOT"` mới được gán `slotGroupId`, `vehicleType`, `code`, và `physicalSlotId`.
- **Phần tử phụ trợ / Không tính sức chứa**: `CHARGING_PILLAR`, `EMERGENCY_EXIT`, `ENTRANCE`, `EXIT`, `DRIVEWAY`, `OBSTACLE`. Các phần tử này **bắt buộc** có `slotGroupId = null`, `physicalSlotId = null`, `vehicleType = null`, `code = null`.

---

## 2. Merchant Layout APIs

### 2.1. Upload ảnh nguồn mặt bằng
- **Route**: `POST /api/v1/merchant/parking-lots/{id}/layout/sources`
- **Security**: Bearer JWT (`ROLE_MERCHANT`), kiểm tra quyền sở hữu bãi xe (`lot.merchantId == principal.userId`).
- **Content-Type**: `multipart/form-data`
- **Request Part**: `file` (MultipartFile)
  - Giới hạn kích thước: tối đa 5 MiB.
  - Định dạng hợp lệ: PNG (`image/png`), JPEG (`image/jpeg`).
  - Kiểm tra MIME type, magic bytes (file signature), và kích thước ảnh tối đa 4096 x 4096 pixel.
  - Từ chối file rỗng, file không đúng magic bytes, hoặc vượt kích thước.

#### Response `201 Created`
```json
{
  "success": true,
  "message": "Tải ảnh nguồn thành công",
  "code": "SUCCESS",
  "data": {
    "sourceAssetId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
    "parkingLotId": 12,
    "storedFileName": "parking-layouts/12/sources/a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d.png",
    "originalFileName": "mat-bang-bai-xe-a.png",
    "contentType": "image/png",
    "sizeBytes": 1048576,
    "width": 1920,
    "height": 1080,
    "createdAt": "2026-11-12T14:00:00Z"
  },
  "timestamp": "2026-11-12T14:00:00Z"
}
```

---

### 2.2. Lấy bản nháp sơ đồ bãi xe (Draft)
- **Route**: `GET /api/v1/merchant/parking-lots/{id}/layout/draft`
- **Security**: Bearer JWT (`ROLE_MERCHANT`), kiểm tra quyền sở hữu bãi.

#### Response `200 OK`
```json
{
  "success": true,
  "message": "Lấy bản nháp thành công",
  "code": "SUCCESS",
  "data": {
    "parkingLotId": 12,
    "layoutVersion": 2,
    "activePublicationId": "f0e1d2c3-b4a5-9876-5432-10fedcba9876",
    "sourceAssetId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
    "sourceImageUrl": "/api/v1/merchant/parking-lots/12/layout/sources/a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d/content",
    "grid": {
      "rows": 10,
      "cols": 12,
      "indexBase": 0
    },
    "elements": [
      {
        "elementId": "elem-slot-01",
        "cellType": "PARKING_SLOT",
        "row": 0,
        "col": 0,
        "code": "A-01",
        "vehicleType": "SEDAN_SUV",
        "slotGroupId": 101,
        "physicalSlotId": "8f7e6d5c-4b3a-2109-8765-43210fedcba9",
        "dimensionsMeters": null
      },
      {
        "elementId": "elem-driveway-01",
        "cellType": "DRIVEWAY",
        "row": 0,
        "col": 1,
        "code": null,
        "vehicleType": null,
        "slotGroupId": null,
        "physicalSlotId": null,
        "dimensionsMeters": null
      }
    ],
    "validationSummary": {
      "valid": false,
      "errors": [
        "Số ô xe SEDAN_SUV hiện có (1) chưa khớp với tổng sức chứa đã cấu hình (10)"
      ],
      "configuredCapacities": {
        "SEDAN_SUV": 10,
        "HEAVY_TRUCK": 5
      },
      "layoutSlotCounts": {
        "SEDAN_SUV": 1,
        "HEAVY_TRUCK": 0
      }
    },
    "updatedAt": "2026-11-12T14:05:00Z"
  },
  "timestamp": "2026-11-12T14:05:00Z"
}
```

---

### 2.3. Cập nhật bản nháp (Save Draft)
- **Route**: `PUT /api/v1/merchant/parking-lots/{id}/layout/draft`
- **Security**: Bearer JWT (`ROLE_MERCHANT`), kiểm tra quyền sở hữu bãi.
- **Request Body**:
```json
{
  "expectedVersion": 2,
  "sourceAssetId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
  "grid": {
    "rows": 10,
    "cols": 12,
    "indexBase": 0
  },
  "elements": [
    {
      "elementId": "elem-slot-01",
      "cellType": "PARKING_SLOT",
      "row": 0,
      "col": 0,
      "code": "A-01",
      "vehicleType": "SEDAN_SUV",
      "slotGroupId": 101,
      "physicalSlotId": "8f7e6d5c-4b3a-2109-8765-43210fedcba9",
      "dimensionsMeters": null
    },
    {
      "elementId": "elem-driveway-01",
      "cellType": "DRIVEWAY",
      "row": 0,
      "col": 1,
      "code": null,
      "vehicleType": null,
      "slotGroupId": null,
      "physicalSlotId": null,
      "dimensionsMeters": null
    }
  ]
}
```

#### Quy tắc lưu nháp:
1. `expectedVersion`: Phải khớp với `layoutVersion` hiện tại của bãi. Nếu lệch -> trả `409 Conflict`.
2. Kiểm tra tính toàn vẹn hình học: `rows` và `cols` phải > 0 (tối đa 100x100). Tọa độ `(row, col)` phải nằm trong phạm vi `[0, rows-1]` và `[0, cols-1]`.
3. Không được có 2 element trùng tọa độ `(row, col)`.
4. Không bắt buộc số ô `PARKING_SLOT` phải khớp ngay với tổng sức chứa (cho phép lưu tiến độ vẽ). `validationSummary` sẽ trả cảnh báo nếu chưa đủ điều kiện công bố.

#### Response `200 OK`
Trả về LayoutDraftResponse kèm `layoutVersion` đã được tăng lên (ví dụ: `3`).

---

### 2.4. Công bố sơ đồ (Publish Layout)
- **Route**: `POST /api/v1/merchant/parking-lots/{id}/layout/publish`
- **Security**: Bearer JWT (`ROLE_MERCHANT`), kiểm tra quyền sở hữu bãi.
- **Request Body**:
```json
{
  "expectedVersion": 3,
  "requestId": "req-pub-12345678-abcd"
}
```

#### Quy tắc công bố nghiêm ngặt:
1. **Idempotency**: Nếu `requestId` đã được xử lý thành công trước đó cho bãi xe này, trả về kết quả publication đã công bố tương ứng mà không tạo thêm snapshot mới. Nếu cùng `requestId` nhưng `expectedVersion` khác -> trả `409 Conflict`.
2. **Khớp sức chứa (Capacity Invariant)**:
   - Tổng số ô `PARKING_SLOT` theo từng `vehicleType` trong nháp phải **chính xác bằng** `Slot.totalCapacity` của loại xe đó trong bảng `slots`.
   - Tổng tất cả ô `PARKING_SLOT` phải bằng tổng `totalCapacity` của bãi.
   - Các phần tử phụ trợ (`DRIVEWAY`, `OBSTACLE`, ...) không được tính vào sức chứa.
3. **Cấp phát & Ánh xạ Physical Slot ID**:
   - Ô mới (`physicalSlotId == null`) sẽ được server sinh UUID duy nhất một lần.
   - Ô đã có `physicalSlotId` từ lần publish trước phải được giữ nguyên ID, không được tái sinh ID hoặc hoán đổi ID sang vị trí xe khác.
   - Nếu một ô đang có booking hiệu lực (`active allocation`), ô đó không được phép bị xóa hoặc đổi `vehicleType`/`slotGroupId`. Việc dịch chuyển tọa độ hình học `(row, col)` vẫn được phép và giữ nguyên `physicalSlotId`.
4. **Mã ô unique**: Mọi ô `PARKING_SLOT` phải có `code` không rỗng; sau khi trim và chuẩn hóa case-insensitive, không được trùng mã trong cùng một bãi.
5. **Đồng bộ file storage & DB**:
   - Server sinh snapshot JSON chuẩn tắc (canonical layout document).
   - Lưu JSON qua StoragePort tại object key bất biến: `parking-layouts/{parkingLotId}/revisions/{publicationId}.json`; mặc định là file riêng tư dưới root volume do operator cấu hình, không phải URL công khai.
   - Ghi record `parking_layout_publications`, cập nhật `parking_layouts.active_publication_id`, đồng bộ danh sách `physical_parking_slots`.

#### Response `200 OK`
```json
{
  "success": true,
  "message": "Công bố sơ đồ bãi xe thành công",
  "code": "SUCCESS",
  "data": {
    "publicationId": "e1f2a3b4-c5d6-7e8f-9a0b-1c2d3e4f5a6b",
    "parkingLotId": 12,
    "layoutVersion": 4,
    "publishedAt": "2026-11-12T14:10:00Z",
    "totalSlotsCount": 15,
    "slotsSummary": {
      "SEDAN_SUV": 10,
      "HEAVY_TRUCK": 5
    },
    "storageKey": "parking-layouts/12/revisions/e1f2a3b4-c5d6-7e8f-9a0b-1c2d3e4f5a6b.json"
  },
  "timestamp": "2026-11-12T14:10:00Z"
}
```

---

### 2.5. Tải nội dung ảnh nguồn (Private Content)
- **Route**: `GET /api/v1/merchant/parking-lots/{id}/layout/sources/{assetId}/content`
- **Security**: Bearer JWT (`ROLE_MERCHANT`), kiểm tra quyền sở hữu bãi.
- **Response**: Trả về binary stream ảnh nguồn kèm `Content-Type: image/png` hoặc `image/jpeg`.

---

## 3. Public Layout APIs (Tài xế / Khách vãng lai)

### 3.1. Lấy sơ đồ công bố hiện hành
- **Route**: `GET /api/v1/parking-lots/{id}/layout`
- **Security**: Public (`permitAll`).
- **Quy tắc**:
  - Chỉ trả về layout nếu bãi xe có trạng thái `APPROVED` và đã công bố layout hợp lệ (`active_publication_id != null`).
  - Nếu bãi chưa công bố sơ đồ: trả về `404 Not Found` (`code: PARK_004`, `message: "Bãi đỗ xe chưa công bố sơ đồ"`).
  - Không để lộ đường dẫn asset nội bộ private, không để lộ thông tin nháp.
  - Tích hợp biểu phí chính thức (`pricingOverlay`) lấy từ `pricing_rules` do server quản lý.

#### Response `200 OK`
```json
{
  "success": true,
  "message": "Success",
  "code": "SUCCESS",
  "data": {
    "parkingLotId": 12,
    "publicationId": "e1f2a3b4-c5d6-7e8f-9a0b-1c2d3e4f5a6b",
    "grid": {
      "rows": 10,
      "cols": 12,
      "indexBase": 0
    },
    "elements": [
      {
        "elementId": "elem-slot-01",
        "cellType": "PARKING_SLOT",
        "row": 0,
        "col": 0,
        "code": "A-01",
        "vehicleType": "SEDAN_SUV",
        "slotGroupId": 101,
        "physicalSlotId": "8f7e6d5c-4b3a-2109-8765-43210fedcba9",
        "dimensionsMeters": null
      },
      {
        "elementId": "elem-driveway-01",
        "cellType": "DRIVEWAY",
        "row": 0,
        "col": 1,
        "code": null,
        "vehicleType": null,
        "slotGroupId": null,
        "physicalSlotId": null,
        "dimensionsMeters": null
      }
    ],
    "pricingOverlay": [
      {
        "vehicleType": "SEDAN_SUV",
        "basePricePerHour": 25000.00,
        "currency": "VND",
        "billingUnit": "HOUR",
        "surgeMultiplier": 1.00,
        "surgeStartTime": null,
        "surgeEndTime": null,
        "overtimePenaltyMultiplier": 1.50
      }
    ],
    "hasBackgroundImage": true,
    "backgroundUrl": "/api/v1/parking-lots/12/layout/background",
    "publishedAt": "2026-11-12T14:10:00Z"
  },
  "timestamp": "2026-11-12T14:15:00Z"
}
```

---

### 3.2. Lấy trạng thái chỗ đỗ thời gian thực (Availability Overlay)
- **Route**: `GET /api/v1/parking-lots/{id}/layout/availability`
- **Security**: Public (`permitAll`).
- **Quy tắc an toàn dữ liệu**:
  - **Tuyệt đối không** để lộ `bookingId`, `driverId`, biển số xe hoặc thông tin định danh cá nhân (PII).
  - Trạng thái hợp lệ gồm:
    - `AVAILABLE`: Ô đỗ đang trống và sẵn sàng nhận đặt chỗ.
    - `HELD`: Đang có giữ chỗ hoặc đặt chỗ còn hiệu lực (không giả định xe đã vào bãi).
    - `UNAVAILABLE`: Ô đỗ tạm thời bị khóa/ngưng hoạt động.
  - Phải có trường `updatedAt` thể hiện thời điểm dữ liệu được tổng hợp.

#### Response `200 OK`
```json
{
  "success": true,
  "message": "Success",
  "code": "SUCCESS",
  "data": {
    "parkingLotId": 12,
    "publicationId": "e1f2a3b4-c5d6-7e8f-9a0b-1c2d3e4f5a6b",
    "updatedAt": "2026-11-12T14:15:30Z",
    "slots": [
      {
        "physicalSlotId": "8f7e6d5c-4b3a-2109-8765-43210fedcba9",
        "code": "A-01",
        "vehicleType": "SEDAN_SUV",
        "status": "AVAILABLE"
      },
      {
        "physicalSlotId": "9a8b7c6d-5e4f-3210-9876-543210fedcba",
        "code": "A-02",
        "vehicleType": "SEDAN_SUV",
        "status": "HELD"
      }
    ]
  },
  "timestamp": "2026-11-12T14:15:30Z"
}
```

---

### 3.3. Tải ảnh nền layout công khai
- **Route**: `GET /api/v1/parking-lots/{id}/layout/background`
- **Security**: Public (`permitAll`).
- **Quy tắc**: Chỉ phục vụ ảnh nguồn gắn liền với bản publication hiện hành của bãi xe đã được duyệt (`APPROVED`). Nếu chưa công bố hoặc bãi không ở trạng thái `APPROVED` -> trả `404 Not Found`.

---

## 4. Internal RPC APIs (Dành riêng cho BookingService)

### 4.1. Giữ chỗ ô đỗ (`holdSlot`)
- **Route**: `POST /api/v1/internal/parking-lots/{id}/slots/hold`
- **Security**: Yêu cầu header `X-Internal-Service-Token`.

#### Request Body
```json
{
  "parkingLotId": 12,
  "vehicleType": "SEDAN_SUV",
  "bookingId": 1001,
  "physicalSlotId": "8f7e6d5c-4b3a-2109-8765-43210fedcba9"
}
```
> *Lưu ý*: `physicalSlotId` là trường **tùy chọn (nullable)**:
> - Nếu client gửi `physicalSlotId`: Server xác thực ô thuộc bãi, đúng `vehicleType`, đang `AVAILABLE` (dưới row-level lock) -> giữ ô, tạo record `current_slot_allocations`, trừ 1 `available_capacity` của nhóm `Slot`, lưu `slot_holds`.
> - Nếu client **không gửi** `physicalSlotId`:
>   - Nếu bãi ở chế độ `PHYSICAL` (đã bật sơ đồ): Server tự động phân bổ nguyên tử (atomic auto-allocate) một ô `AVAILABLE` của loại xe đó và trả về `physicalSlotId` cùng `physicalSlotCode`.
>   - Nếu bãi ở chế độ `GROUP` (chưa có sơ đồ hoặc chưa kích hoạt): Giữ chỗ theo bucket nhóm sức chứa truyền thống (`physicalSlotId = null`).
> - **Idempotency**: Retry cùng `bookingId` và cùng thông tin xe/ô -> trả về cùng kết quả giữ chỗ thành công trước đó. Nếu cùng `bookingId` nhưng thay đổi ô hoặc thông tin xe -> trả `409 Conflict`.

#### Response `200 OK`
```json
{
  "success": true,
  "message": "Giữ chỗ thành công",
  "code": "SUCCESS",
  "data": {
    "success": true,
    "slotId": 101,
    "parkingLotId": 12,
    "vehicleType": "SEDAN_SUV",
    "remainingCapacity": 9,
    "baseHourlyPrice": 25000.00,
    "merchantId": 4,
    "parkingLotName": "Bãi Xe Cát Lái",
    "parkingLotAddress": "Số 789 Nguyễn Thị Định, TP. Thủ Đức",
    "surgeMultiplier": 1.00,
    "surgeStartTime": null,
    "surgeEndTime": null,
    "overtimePenaltyMultiplier": 1.50,
    "physicalSlotId": "8f7e6d5c-4b3a-2109-8765-43210fedcba9",
    "physicalSlotCode": "A-01",
    "layoutPublicationId": "e1f2a3b4-c5d6-7e8f-9a0b-1c2d3e4f5a6b",
    "message": "Giữ chỗ thành công"
  },
  "timestamp": "2026-11-12T14:20:00Z"
}
```

---

### 4.2. Giải phóng giữ chỗ ô đỗ (`releaseSlot`)
- **Route**: `POST /api/v1/internal/parking-lots/{id}/slots/release`
- **Security**: Yêu cầu header `X-Internal-Service-Token`.
- **Request Body**:
```json
{
  "parkingLotId": 12,
  "vehicleType": "SEDAN_SUV",
  "bookingId": 1001
}
```
- **Quy tắc**:
  - Giải phóng allocation của `bookingId` trong `current_slot_allocations`.
  - Tăng 1 `available_capacity` của nhóm `Slot`.
  - Đánh dấu `slot_holds.released = true`.
  - Thao tác là **idempotent**: gọi lặp lại nhiều lần không cộng trùng số chỗ trống.

---

## 5. Bảng mã lỗi đặc thù (Error Codes)

| HTTP Status | Error Code | Mô tả |
| :--- | :--- | :--- |
| `400 Bad Request` | `ERR_400` | Cấu trúc dữ liệu yêu cầu không hợp lệ (tọa độ ngoài lưới, kích thước ảnh vượt 5MB/4096px, MIME không phải PNG/JPEG). |
| `400 Bad Request` | `PARK_003` | Không khớp sức chứa: số lượng ô `PARKING_SLOT` không khớp với `totalCapacity` của loại xe hoặc tổng sức chứa bãi. |
| `401 Unauthorized` | `ERR_401` | Chưa đăng nhập hoặc token hết hạn. |
| `403 Forbidden` | `ERR_403` | Không có quyền thao tác trên bãi xe này (không phải chủ bãi). |
| `404 Not Found` | `PARK_001` | Bãi đỗ xe không tồn tại. |
| `404 Not Found` | `PARK_004` | Bãi xe chưa công bố sơ đồ hoặc chưa được duyệt hoạt động. |
| `409 Conflict` | `ERR_409` | Xung đột phiên bản (`expectedVersion` không khớp), mã ô bị trùng, hoặc ô đang có đặt chỗ hiệu lực bị xóa. |
| `409 Conflict` | `PARK_002` | Ô đỗ vật lý đã có người khác giữ chỗ hoặc bãi đã hết chỗ trống. |
| `500 Internal Error`| `ERR_500` | Lỗi lưu trữ file hoặc lỗi kết nối cơ sở dữ liệu. |
