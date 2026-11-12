import fs from 'node:fs';

const envContent = fs.readFileSync('smart-parking-microservice/.trello.env', 'utf-8');
const env = {};
for (const line of envContent.split('\n')) {
  const trimmed = line.trim();
  if (trimmed && !trimmed.startsWith('#') && trimmed.includes('=')) {
    const idx = trimmed.indexOf('=');
    env[trimmed.substring(0, idx).trim()] = trimmed.substring(idx + 1).trim();
  }
}

const key = env.TRELLO_API_KEY;
const token = env.TRELLO_API_TOKEN;

// 1. Enriched descriptions for 7 short cards
const enrichedDescriptions = {
  // SP-S3-DAT-01
  "6aeb0e31ab56be8ab36cadb0": `### 💾 Nhiệm vụ Cơ Sở Dữ Liệu & Hiệu Năng
- **Service:** \`parking-lot-service\` | **Schema:** \`smart_parking_lot_db\`
- **Độ ưu tiên:** P1 - High | **Ước lượng:** 3 STP
- **Người phụ trách:** Hoàng Đình Tùng (Database/Infra)

#### 🎯 Mục Tiêu Nghiệp Vụ & Kỹ Thuật
1. Tạo Spatial Index trên tọa độ \`latitude\` và \`longitude\` bảng \`parking_lots\`:
   \`\`\`sql
   CREATE SPATIAL INDEX idx_parking_lots_coordinates ON parking_lots (location);
   \`\`\`
2. Tối ưu hóa truy vấn tìm bãi đỗ xe trong bán kính $R$ bằng hàm không gian MySQL \`ST_Distance_Sphere\`:
   \`\`\`sql
   SELECT id, name, address, latitude, longitude,
          ST_Distance_Sphere(point(longitude, latitude), point(:driverLng, :driverLat)) AS distance_in_meters
   FROM parking_lots
   WHERE status = 'ACTIVE' AND operating_status = 'OPEN'
     AND ST_Distance_Sphere(point(longitude, latitude), point(:driverLng, :driverLat)) <= :radiusInMeters
   ORDER BY distance_in_meters ASC
   LIMIT :limit;
   \`\`\`
3. Triển khai kiến trúc **Cache-Aside trên Redis** cho danh sách bãi xe theo Geo-hash:
   - Key: \`geo:parking_lots:{geohash_prefix_5}\` (bán kính ~4.9km)
   - TTL: 10 phút, tự động Invalidate khi có Merchant cập nhật trạng thái bãi hoặc Admin kiểm duyệt.
   - Giảm tải 85% truy vấn database khi hàng nghìn tài xế đồng thời mở radar tìm bãi.

#### 🧪 Tiêu Chí Nghiệm Thu (Acceptance Criteria)
- [ ] Response time của API tìm bãi giảm xuống < 120ms với tập dữ liệu 100,000 bãi đỗ.
- [ ] Redis Cache hit ratio đạt tối thiểu 70% trong kịch bản test tải JMeter.
- [ ] Tự động xóa cache tương ứng ngay khi nhận event Kafka \`lot-status-changed\`.`,

  // SP-S3-INT-01
  "6aeb0ddcb7f015ffe8a907bf": `### 🧪 Nhiệm vụ Kiểm Thử Chất Lượng (Quality Assurance)
- **Phân hệ:** Toàn hệ thống (Driver Portal + Merchant Barie + Payment Gate)
- **Độ ưu tiên:** P1 - High | **Ước lượng:** 5 STP
- **Người phụ trách:** Huy (Huyy Gia) (E2E Integration)

#### 🎯 Mục Tiêu Kịch Bản Kiểm Thử Playwright
Xây dựng bộ test suite tự động E2E (\`smart-parking-app-v2/e2e/core-booking-gate-flow.spec.ts\`) bao phủ trọn vẹn 3 luồng giao dịch cốt lõi:
1. **Flow 1: Giữ chỗ 10 phút (Hold TTL 10m)**:
   - Tài xế tìm kiếm bãi xe trên Leaflet Map -> Xem chi tiết bãi.
   - Chọn loại xe đầu kéo (\`TRAILER\`) -> Bấm "Giữ chỗ 10 phút".
   - Kiểm tra hiển thị đồng hồ đếm ngược 10:00 và trạng thái slot chuyển thành \`HOLD\`.
   - Kết nối cổng thanh toán PayOS Sandbox -> Nhận mã xác nhận đặt chỗ và QR Terminal Pass.
2. **Flow 2: Check-in Barie Tự Động / Thủ Công**:
   - Camera cổng Barie quét QR Pass của tài xế.
   - Hệ thống xác thực mã đặt cọc -> Đối soát biển số xe -> Ghi nhận thời gian xe vào (\`check_in_time\`).
   - Mở barie tự động và cập nhật trạng thái đơn thành \`IN_PARKING\`.
3. **Flow 3: Check-out, Tính Giá Lũy Tiến & Thu Tiền Mặt**:
   - Xe ra cổng -> Camera quét QR Check-out.
   - Động cơ tính giá lũy tiến tự động khấu trừ tiền cọc và tính số tiền quá giờ phải trả thêm.
   - Chủ bãi bấm "Xác nhận thu tiền mặt" -> Cập nhật sổ cái kép và giải phóng slot xe.

#### 🧪 Tiêu Chí Nghiệm Thu (Acceptance Criteria)
- [ ] Toàn bộ 3 flows chạy pass 100% không flaky trên môi trường Chromium headless.
- [ ] Thời gian thực thi toàn bộ kịch bản E2E < 90 giây.
- [ ] Báo cáo video và screenshot được lưu tự động vào thư mục test-results khi có lỗi.`,

  // SP-S3-SEC-01
  "6aeb0dd7eb63b5ead21a7b83": `### 🛡️ Nhiệm vụ Bảo Mật & Kiến Trúc
- **Mã Gap:** \`GAP-09\` (Lỗ hổng an ninh thông tin nghiêm trọng)
- **Độ ưu tiên:** P0 - Critical | **Ước lượng:** 3 STP
- **Người phụ trách:** Hoàng Đình Tùng (Security/Gateway)

#### 🎯 Mục Tiêu Khắc Phục Lỗ Hổng
1. **Loại bỏ hoàn toàn \`otpCode\` khỏi Network Response**:
   - DTO \`SendOtpResponse.java\` trong \`identity-service\` chỉ chứa:
     \`\`\`java
     public class SendOtpResponse {
         private int expiresInSeconds;
         private int resendAfterSeconds;
     }
     \`\`\`
   - Tuyệt đối không trả mã OTP trong response body (chống nguy cơ lộ mã khi hacker chặn bắt gói tin hoặc view qua Network tab).
2. **Loại bỏ thư viện phụ thuộc phía Client**:
   - Gỡ bỏ hoàn toàn package \`@emailjs/browser\` khỏi \`package.json\` của Frontend v2.
   - Thu hồi toàn bộ public API keys và service IDs của EmailJS trong mã nguồn.
3. **Cấu hình SMTP Server An Toàn từ Backend**:
   - Cấu hình Spring Boot Mail trong \`notification-service\` với Gmail SMTP / SendGrid qua TLS port 587.
   - Sử dụng Kafka topic \`otp-dispatch-events\` để phát lệnh gửi email/SMS bất đồng bộ.
4. **Cấu hình Rate Limit Redis Chống Brute-Force**:
   - Giới hạn 1 mã OTP mỗi 60 giây cho 1 email / số điện thoại.
   - Khóa tạm thời 15 phút nếu nhập sai quá 5 lần liên tiếp.

#### 🧪 Tiêu Chí Nghiệm Thu (Acceptance Criteria)
- [ ] Network tab trình duyệt kiểm tra response của \`/api/v1/auth/otp/send\` không chứa chuỗi OTP.
- [ ] Email OTP gửi về hòm thư thật trong vòng < 5 giây qua SMTP Server.
- [ ] Gửi yêu cầu OTP thứ 2 trong vòng 60 giây nhận lỗi HTTP 429 Too Many Requests.`,

  // SP-S3-RT-01
  "6aeb0dc572d101c0e2d91da4": `### 📌 Thông Tin Nhiệm Vụ Backend
- **Mã Gap:** \`GAP-16\` (Tính năng thiết yếu cho vận hành thực tế của Chủ bãi xe)
- **Service xử lý:** \`parking-lot-service\` (\`ParkingLotController.java\` & \`ParkingLotService.java\`)
- **Độ ưu tiên:** P1 - High | **Ước lượng:** 3 STP
- **Người phụ trách:** Hoàng Đình Tùng (Realtime/Status)

#### 🎯 Mục Tiêu Nghiệp Vụ
Cho phép Merchant chuyển đổi nhanh trạng thái vận hành bãi đỗ (\`OPEN\`, \`TEMPORARILY_CLOSED\`, \`MAINTENANCE\`) khi có sự cố ngập nước, cúp điện, nâng cấp mặt bằng mà không cần phải xóa bãi xe.

#### 🔌 Hợp Đồng API & DTO
- **Endpoint:** \`PATCH /api/v1/merchant/parking-lots/{id}/operating-status\`
- **Quyền hạn:** \`@PreAuthorize("hasRole('MERCHANT')")\`
- **Request Body:**
  \`\`\`json
  {
    "status": "TEMPORARILY_CLOSED",
    "reason": "Bảo trì trạm sạc điện và thi công mặt bằng khoang C",
    "reopenEstimatedAt": "2026-11-15T08:00:00Z"
  }
  \`\`\`
- **Xử lý Thời Gian Thực (Real-Time):**
  1. Cập nhật cột \`operating_status\` và \`status_reason\` trong bảng \`parking_lots\`.
  2. Bắn sự kiện WebSocket STOMP tới topic \`/topic/lots/{id}/status\` để cập nhật giao diện tài xế tức thì.
  3. Gửi event Kafka \`lot-status-changed\` để Invalidate Redis Cache tìm bãi quanh khu vực.
  4. Ngăn chặn tài xế mới tạo đơn giữ chỗ tại bãi đang tạm đóng.

#### 🧪 Tiêu Chí Nghiệm Thu (Acceptance Criteria)
- [ ] Merchant đổi trạng thái bãi sang TEMPORARILY_CLOSED thì bãi biến mất khỏi radar tìm bãi của tài xế.
- [ ] Các đơn đã đặt trước đó vẫn được bảo lưu thời gian check-in.
- [ ] Đổi lại OPEN thì bãi xuất hiện lại trên bản đồ trong vòng < 1 giây.`,

  // SP-S3-GOV-02
  "6aeb0dbd8d9855b7f2cfb17b": `### 📌 Thông Tin Nhiệm Vụ Backend
- **Mã Gap:** \`GAP-11\` (Kiểm soát kỷ luật & Quản trị tài khoản)
- **Service xử lý:** \`identity-service\` (\`AdminUserController.java\` & \`UserServiceImpl.java\`)
- **Độ ưu tiên:** P2 - Medium | **Ước lượng:** 3 STP
- **Người phụ trách:** Hồ Quốc Khải (Governance)

#### 🎯 Mục Tiêu Nghiệp Vụ
Hoàn thiện cơ chế cảnh cáo và chế tài phân tầng cho Quản trị viên đối với người dùng (Tài xế / Chủ bãi) có hành vi vi phạm điều khoản nền tảng (bùng cọc, cố tình không mở barie, thái độ tiêu cực).

#### 🔌 Hợp Đồng API & DTO
1. **API Gửi Cảnh Cáo:** \`POST /api/v1/admin/users/{id}/warnings\`
   - **Quyền hạn:** \`@PreAuthorize("hasRole('ADMIN')")\`
   - **Request Body:**
     \`\`\`json
     {
       "reason": "Tài xế bùng đơn giữ chỗ quá 3 lần trong vòng 24 giờ",
       "penaltyType": "OFFICIAL_WARNING",
       "notifyUser": true,
       "evidenceUrl": "https://cdn.smartparkinghub.com/evidence/warn-882.jpg"
     }
     \`\`\`
   - **Business Rules:**
     - Tăng bộ đếm \`warning_count\` của User.
     - Nếu \`warning_count >= 3\`: Tự động kích hoạt cơ chế khóa tài khoản tạm thời 7 ngày (\`status = 'SUSPENDED'\`).
2. **API Tra Cứu Lịch Sử Cảnh Cáo:** \`GET /api/v1/admin/users/{id}/warnings\`
   - Trả về danh sách thời gian, lý do, bằng chứng và Quản trị viên ra quyết định cảnh cáo.

#### 🧪 Tiêu Chí Nghiệm Thu (Acceptance Criteria)
- [ ] Admin gửi cảnh cáo thành công, hệ thống tự động bắn email thông báo vi phạm cho user.
- [ ] Đến lần vi phạm thứ 3, user bị đăng xuất cưỡng chế và không thể đăng nhập trong 7 ngày.
- [ ] Toàn bộ thao tác cảnh cáo được ghi vết vào \`admin_audit_logs\`.`,

  // SP-S3-OPS-02 (trong 06. To Do)
  "6aeb0e37edffa306ef88ff11": `### 🧹 Nhiệm Vụ Dọn Dẹp Nợ Kỹ Thuật (Technical Debt)
- **Mã Gap:** \`GAP-08\` & \`GAP-16\`
- **Phân hệ:** Frontend \`smart-parking-app-v2\`
- **Độ ưu tiên:** P2 - Medium | **Ước lượng:** 2 STP

#### 🎯 Mục Tiêu Dọn Dẹp Mã Nguồn Rác
1. **Loại bỏ hoàn toàn Ant Design Sider chết**:
   - Xóa bỏ các block code không bao giờ chạy (unreachable code) trong \`DashboardLayout.tsx\`.
   - Gỡ bỏ 3 mảng menu Ant Design legacy còn sót lại (\`driverMenuItems\`, \`merchantMenuItems\`, \`adminMenuItems\`).
2. **Xóa bỏ các file mồ côi (Orphaned Files)**:
   - Xóa file \`MerchantPlaceholderPage.tsx\` không có tuyến đường (Route) trong router.
3. **Chuẩn hóa kiến trúc 4 lớp (Constitution Rule 15)**:
   - Đảm bảo tất cả các trang đều sử dụng chung Design Tokens Tailwind CSS v4, không còn style nội dòng CSS inline dư thừa.

#### 🧪 Tiêu Chí Nghiệm Thu (Acceptance Criteria)
- [ ] Chạy \`npm run build\` và \`npm run lint\` vượt qua 100% không có cảnh báo unused variables.
- [ ] Giao diện Dashboard Sidebar hiển thị mượt mà trên cả Mobile và Desktop.`,

  // SP-S3-SAGA-01 (trong 06. To Do)
  "6aeb0dcbfd4829d28a5fbc8c": `### 📌 Thông Tin Nhiệm Vụ Backend Phân Tán
- **Mã Gap:** \`GAP-13\` (Tự động hóa hoàn tiền tranh chấp)
- **Service xử lý:** \`payment-service\` (\`DisputeServiceImpl.java\` & \`PaymentServiceImpl.java\`)
- **Độ ưu tiên:** P1 - High | **Ước lượng:** 5 STP

#### 🎯 Nghiệp Vụ Saga Hoàn Tiền Tự Động
Khi Super Admin ban hành phán quyết tranh chấp (SCR-050), hệ thống phải kích hoạt chuỗi giao dịch phân tán tự động:
1. **Phán Quyết Hoàn Cọc Cho Tài Xế (\`REFUND_TO_DRIVER\`):**
   - Tự động gọi PayOS Refund API để hoàn tiền cọc vào tài khoản ngân hàng của tài xế (nếu thanh toán qua PayOS).
   - Trường hợp thanh toán qua ví nội bộ: Cộng trực tiếp số tiền \`refundAmount\` vào số dư khả dụng của Tài xế.
   - Trừ số dư khả dụng của Chủ bãi xe tương ứng.
2. **Cơ Chế Khóa Chống Trùng Lặp (Idempotency Guard):**
   - Sinh Idempotency Key định dạng: \`DISPUTE_REFUND_{disputeId}\`.
   - Đảm bảo không xảy ra sự cố hoàn tiền 2 lần ngay cả khi Admin bấm nút nhiều lần hoặc mạng bị retry.
3. **Ghi Sổ Cái Kép (Double-Entry Ledger):**
   - Sinh 2 bản ghi đối ứng trong \`wallet_transactions\` với mã loại \`DISPUTE_COMPENSATION\`.

#### 🧪 Tiêu Chí Nghiệm Thu (Acceptance Criteria)
- [ ] Sau khi Admin bấm "Ban hành phán quyết", tài xế nhận tiền hoàn trong vòng < 3 giây.
- [ ] Số dư ví của Merchant bị khấu trừ chính xác số tiền bồi thường.
- [ ] Nếu PayOS trả lỗi 5xx, Saga kích hoạt bù trừ (Compensating Transaction) và thông báo cho kế toán.`
};

// 2. DoD Checklists for 7 GAP cards without checklists
const gapChecklists = [
  {
    cardId: "6aec0ae11c7ed614eaf6b1ff", // GAP-17
    name: "Checklist Thực Hiện (Definition of Done)",
    items: [
      "1. Phân tích schema DTO HoldSlotRequest và đối soát field estimatedDurationHours",
      "2. Cấu hình cơ chế Fallback Mock an toàn khi API Gateway trả lỗi 5xx",
      "3. Triển khai Unit test kiểm thử luồng đặt cọc giữ chỗ 10 phút",
      "4. Kiểm thử tích hợp E2E luồng tạo booking từ giao diện bản đồ"
    ]
  },
  {
    cardId: "6aec0ae2b584736e717c7d9e", // GAP-18
    name: "Checklist Thực Hiện (Definition of Done)",
    items: [
      "1. Kiểm tra format response của GET /api/v1/bookings/my (Object vs Array)",
      "2. Cập nhật mapper bóc tách content trong Page Response an toàn",
      "3. Xử lý UI State hiển thị empty state khi danh sách đơn rỗng",
      "4. Viết Unit test xác nhận không xảy ra TypeError undefined is not iterable"
    ]
  },
  {
    cardId: "6aec0ae38abeb86974d3cd6d", // GAP-19
    name: "Checklist Thực Hiện (Definition of Done)",
    items: [
      "1. Rà soát toàn bộ cuộc gọi Axios trần trong notificationsApi.ts",
      "2. Thay thế bằng instance apiClient có đính kèm Bearer JWT và X-App-Key",
      "3. Kiểm tra tự động gắn refresh token khi token hết hạn (401 Interceptor)",
      "4. Viết test giả lập cuộc gọi API thông báo có đủ Authentication Header"
    ]
  },
  {
    cardId: "6aec0ae4c015dee2c6cf8cc9", // GAP-20
    name: "Checklist Thực Hiện (Definition of Done)",
    items: [
      "1. Bổ sung trạng thái NOTIFIED vào enum QueueStatusEnum",
      "2. Thiết kế endpoint POST /api/v1/queues/{id}/accept-slot cho tài xế",
      "3. Xử lý bộ đếm ngược TTL 5 phút để tài xế xác nhận nhận vị trí đỗ",
      "4. Viết kịch bản kiểm thử tự động giải phóng vị trí khi tài xế bỏ qua"
    ]
  },
  {
    cardId: "6aec0ae5e05668d30f6c6996", // GAP-21
    name: "Checklist Thực Hiện (Definition of Done)",
    items: [
      "1. Bổ sung enum TRAILER vào VehicleTypeEnum ở common-library",
      "2. Cập nhật frontend constants và type definition khớp 100% với Backend",
      "3. Cập nhật migration database hỗ trợ cấu hình giá và slot cho xe đầu kéo",
      "4. Kiểm thử tích hợp lưu và hiển thị cấu hình bãi đỗ chấp nhận rơ-moóc"
    ]
  },
  {
    cardId: "6aec0ae6e85b218f0abcd01b", // GAP-22
    name: "Checklist Thực Hiện (Definition of Done)",
    items: [
      "1. Loại bỏ dữ liệu biểu đồ hardcode trong MerchantDashboardPage",
      "2. Đấu nối API GET /api/v1/merchant/revenue/daily-series từ payment-service",
      "3. Xử lý format tiền tệ VND và hiển thị tooltip biểu đồ SVG/Recharts",
      "4. Viết kiểm thử giao diện xác nhận biểu đồ render đúng số liệu API trả về"
    ]
  },
  {
    cardId: "6aec368f5ca048394a3bdb60", // GAP-23
    name: "Checklist Thực Hiện (Definition of Done)",
    items: [
      "1. Thêm cờ môi trường VITE_ENABLE_MSW_MOCK trong file .env",
      "2. Cấu hình Axios Global Interceptor bắt lỗi 5xx hiển thị Toast cảnh báo",
      "3. Tự động chuyển đổi Fallback Mock khi cờ môi trường được bật",
      "4. Viết test kiểm tra Toast hiển thị đúng thông điệp thân thiện khi Backend sập"
    ]
  }
];

// Helper to update card description
async function updateDesc(cardId, desc) {
  const url = `https://api.trello.com/1/cards/${cardId}?key=${key}&token=${token}`;
  const res = await fetch(url, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ desc })
  });
  return res.ok;
}

// Helper to create checklist
async function createChecklist(cardId, name, items) {
  const url = `https://api.trello.com/1/cards/${cardId}/checklists?key=${key}&token=${token}`;
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name })
  });
  if (!res.ok) return false;
  const checklist = await res.json();
  for (const item of items) {
    const itemUrl = `https://api.trello.com/1/checklists/${checklist.id}/checkItems?key=${key}&token=${token}`;
    await fetch(itemUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: item })
    });
    await new Promise(r => setTimeout(r, 60));
  }
  return true;
}

// Helper to update check item state
async function setCheckItemState(cardId, checkItemId, state) {
  const url = `https://api.trello.com/1/cards/${cardId}/checkItem/${checkItemId}?state=${state}&key=${key}&token=${token}`;
  const res = await fetch(url, { method: 'PUT' });
  return res.ok;
}

async function run() {
  console.log("=== BƯỚC 1: Bổ sung mô tả chi tiết cho 7 thẻ ngắn ===");
  for (const [id, desc] of Object.entries(enrichedDescriptions)) {
    const ok = await updateDesc(id, desc);
    console.log(`Updated desc for ${id}: ${ok ? 'OK' : 'FAIL'}`);
    await new Promise(r => setTimeout(r, 100));
  }

  console.log("\n=== BƯỚC 2: Tạo Definition of Done Checklist cho 7 thẻ GAP ===");
  for (const g of gapChecklists) {
    const ok = await createChecklist(g.cardId, g.name, g.items);
    console.log(`Created checklist for ${g.cardId}: ${ok ? 'OK' : 'FAIL'}`);
    await new Promise(r => setTimeout(r, 100));
  }

  console.log("\n=== BƯỚC 3: Tích hoàn thành các checklist item đã làm xong trong codebase ===");
  const auditReport = JSON.parse(fs.readFileSync('smart-parking-microservice/scripts/trello/card-audit-report.json', 'utf8'));

  // Define which cards & item patterns to mark as complete
  const itemsToComplete = [
    // SP-S3-SEC-01 (OTP)
    { cardName: 'SP-S3-SEC-01', patterns: ['Xóa bỏ trường otpCode', 'Cấu hình Spring Boot Starter Mail', 'Gỡ bỏ @emailjs/browser'] },
    // SP-S3-SEC-02 (OTP demo)
    { cardName: 'SP-S3-SEC-02', patterns: ['Phân tích yêu cầu', 'Triển khai mã nguồn logic'] },
    // SP-S3-UI-04 (LandingPage SCR-001)
    { cardName: 'SP-S3-UI-04', patterns: ['Kiểm thử tự động & Vượt qua cổng'] },
    // SP-S3-UI-21 (PricingPage SCR-004)
    { cardName: 'SP-S3-UI-21', patterns: ['Kiểm thử tự động & Vượt qua cổng'] },
    // SP-S3-UI-08 (PaymentCancelPage SCR-024)
    { cardName: 'SP-S3-UI-08', patterns: ['Phân tích Stitch HTML/CSS', 'Cấu hình Route', 'Zod/React Hook Form', '4 trạng thái giao diện', 'Kiểm thử tự động'] },
    // SP-S3-UI-03 (DisputesPage SCR-DRV-01)
    { cardName: 'SP-S3-UI-03', patterns: ['Phân tích Stitch', 'Cấu hình Route', 'Zod/React Hook Form', '4 trạng thái giao diện', 'Kiểm thử tự động'] },
    // SP-S3-UI-02 (AdminDashboardPage SCR-042)
    { cardName: 'SP-S3-UI-02', patterns: ['Phân tích Stitch', 'Cấu hình Route', '4 trạng thái giao diện', 'Kiểm thử tự động'] },
    // SP-S3-GOV-03 (AdminUserDetailPage SCR-046)
    { cardName: 'SP-S3-GOV-03', patterns: ['Phân tích Stitch', 'Cấu hình Route', 'Zod/React Hook Form', '4 trạng thái giao diện', 'Kiểm thử tự động'] },
    // SP-S3-GOV-04 (AdminUsersPage SCR-045)
    { cardName: 'SP-S3-GOV-04', patterns: ['Phân tích Stitch', 'Cấu hình Route', '4 trạng thái giao diện', 'Kiểm thử tự động'] },
    // SP-S3-GOV-05 (AdminDisputesPage SCR-049)
    { cardName: 'SP-S3-GOV-05', patterns: ['Phân tích Stitch', 'Cấu hình Route', '4 trạng thái giao diện', 'Kiểm thử tự động'] },
    // SP-S3-PAY-07 (AdminDisputeDetailPage SCR-050)
    { cardName: 'SP-S3-PAY-07', patterns: ['Phân tích Stitch', 'Cấu hình Route', '4 trạng thái giao diện', 'Kiểm thử tự động'] },
    // SP-S3-PAY-05 (AdminTransactionsPage SCR-047)
    { cardName: 'SP-S3-PAY-05', patterns: ['Phân tích Stitch', 'Cấu hình Route', '4 trạng thái giao diện', 'Kiểm thử tự động'] },
    // SP-S3-PAY-06 (AdminWithdrawalsPage SCR-048)
    { cardName: 'SP-S3-PAY-06', patterns: ['Phân tích Stitch', 'Cấu hình Route', '4 trạng thái giao diện', 'Kiểm thử tự động'] },
    // SP-S3-SEC-03 (AdminAuditLogsPage SCR-051)
    { cardName: 'SP-S3-SEC-03', patterns: ['Phân tích Stitch', 'Cấu hình Route', '4 trạng thái giao diện', 'Kiểm thử tự động'] },
    // In To Do: SP-S3-UI-16 (AdminNotificationsPage SCR-052)
    { cardName: 'SP-S3-UI-16', patterns: ['Phân tích Stitch', 'Cấu hình Route', '4 trạng thái giao diện', 'Kiểm thử tự động'] },
    // In To Do: SP-S3-UI-18 (CareersPage SCR-007)
    { cardName: 'SP-S3-UI-18', patterns: ['Phân tích Stitch', 'Cấu hình Route', 'Zod/React Hook Form', '4 trạng thái giao diện', 'Kiểm thử tự động'] },
    // In To Do: SP-S3-UI-19 (ContactPage SCR-009)
    { cardName: 'SP-S3-UI-19', patterns: ['Phân tích Stitch', 'Cấu hình Route', 'Zod/React Hook Form', '4 trạng thái giao diện', 'Kiểm thử tự động'] }
  ];

  for (const target of itemsToComplete) {
    const card = auditReport.find(c => c.name.includes(target.cardName));
    if (!card || !card.checklists || card.checklists.length === 0) continue;
    for (const cl of card.checklists) {
      for (const item of cl.checkItems) {
        const matches = target.patterns.some(p => item.name.toLowerCase().includes(p.toLowerCase()));
        if (matches && item.state !== 'complete') {
          const ok = await setCheckItemState(card.id, item.id, 'complete');
          console.log(`[TICK DONE] ${target.cardName} -> "${item.name}": ${ok ? 'OK' : 'FAIL'}`);
          await new Promise(r => setTimeout(r, 80));
        }
      }
    }
  }

  console.log("\nHoàn tất toàn bộ việc cập nhật mô tả, checklist và tick các mục đã hoàn thành!");
}

run();
