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
const LIST_ID = '6af1d8d22e332f85fb8260d0'; // Sprint Backlog 3

// Members
const MEMBERS = {
  tung: '6930e5b55d3528613be81a82',
  huy: '69016d7a8c58ffe25753e224',
  loc: '68f0354b7e270498cfc5666a',
  khai: '6a6b571e2274ff581eb61742',
  khang: '692e4f13a054491cdb09b6f8'
};

// Labels
const LABELS = {
  fe: '6adecae3ac6c046e8c80ed8c',
  be: '6adecae3ac6c046e8c80ed87',
  ai: '6adecae3ac6c046e8c80ed89',
  devops: '6adecae3ac6c046e8c80ed8b',
  qa: '6adecae3ac6c046e8c80ed88',
  doc: '6ae173fd319e73c2ad9067a7'
};

const tasks = [
  // Sprint Goal
  {
    name: '🎯 Sprint Goal 3 — Hoàn thiện AI ANPR, Gemini 2D Layout, Dynamic Pricing, Live Slot Matrix, Sổ cái kép & Nghiệm thu Toàn diện (126 STP)',
    desc: `## SPRINT GOAL 3
**Thời gian:** 10/11/2026 – 23/11/2026 (2 tuần)
**Tổng cam kết:** 126 Story Points (STP) ~ 392 Giờ công (Hours)

### Mục tiêu chiến lược:
1. **AI & Vision:** Hoàn thiện pipeline nhận diện biển số xe ANPR tại barie và API Gemini Multimodal bóc tách sơ đồ bãi đỗ 2D.
2. **Dynamic Pricing & Realtime:** Thuật toán định giá động (Surge Pricing) và STOMP WebSocket Live Slot Matrix.
3. **Core Financial Ledger:** Sổ cái kép (Double-Entry Ledger), cronjob quyết toán đối tác và Saga hoàn cọc tự động.
4. **Resilience & QA:** Cơ chế hàng đợi thông minh (Waitlist Queue), State Guards duyệt KYC, kiểm thử chịu tải 1,000 CCU bằng JMeter, đạt chuẩn SonarQube > 80%.
5. **Presentation & Acceptance:** Seed data thực tế, bộ trang tĩnh phụ trợ, slide pitch deck và video demo nghiệm thu đồ án.`,
    members: [MEMBERS.tung, MEMBERS.huy, MEMBERS.loc, MEMBERS.khai, MEMBERS.khang],
    labels: [LABELS.doc]
  },

  // --- TÙNG (30 STP) ---
  {
    name: '[SP-S3-ANPR-01] [AI/DevOps] Xây dựng Pipeline ANPR Nhận diện Biển số Xe từ Luồng Camera RTSP - (8 STP)',
    desc: `### Bối cảnh & Nghiệp vụ:
Tự động hóa cổng barie ra/vào bãi xe tải, tài xế không cần xuống xe trình vé giấy hay quét mã thủ công.

### Đặc tả kỹ thuật:
- Container Python AI xử lý luồng RTSP từ Camera tại cổng bãi đỗ.
- Sử dụng YOLOv8 trích xuất vùng biển số và PaddleOCR/Tesseract bóc tách ký tự biển kiểm soát xe cơ giới Việt Nam.
- Phát Kafka Event lên Topic: \`vehicle.plate.detected\` kèm payload: { lotId, gateId, licensePlate, detectedAt, imageUrl }.

### Tiêu chí nghiệm thu (DoD):
1. Nhận diện chính xác biển số xe trong vòng < 600ms/frame.
2. Độ chính xác đạt > 95% với ban ngày và > 90% ban đêm.
3. Snapshot được lưu trữ an toàn trên MinIO/S3.
- **Story Points:** 8 STP (~24 Hours)
- **Assignee:** Hoàng Đình Tùng (Lead DevOps & Infra)`,
    members: [MEMBERS.tung],
    labels: [LABELS.ai, LABELS.devops]
  },
  {
    name: '[SP-S3-SEC-01] [BE/DevOps] Triển khai Idempotency-Key Filter tại API Gateway Chống Trùng Thanh Toán - (5 STP)',
    desc: `### Bối cảnh & Nghiệp vụ:
Tránh tình trạng người dùng click liên tục do gián đoạn mạng dẫn đến trừ tiền nhiều lần cho cùng một lượt giữ chỗ.

### Đặc tả kỹ thuật:
- Global Filter trong Spring Cloud Gateway bắt header \`Idempotency-Key\` trên các endpoint nhạy cảm: \`POST /api/v1/payments/create-link\` và \`POST /api/v1/bookings/hold\`.
- Lưu trữ khóa idempotency trong Redis với TTL 60 giây và cờ trạng thái: \`PROCESSING\`, \`COMPLETED\`.
- Trả về \`409 Conflict\` nếu đang xử lý, hoặc trả kết quả đã cache nếu đã hoàn tất.

### Tiêu chí nghiệm thu (DoD):
1. Gửi 10 request đồng thời với cùng 1 key chỉ xử lý duy nhất 1 giao dịch.
2. Không sinh duplicate payment link trên PayOS.
- **Story Points:** 5 STP (~16 Hours)
- **Assignee:** Hoàng Đình Tùng (Lead DevOps & Infra)`,
    members: [MEMBERS.tung],
    labels: [LABELS.devops, LABELS.be]
  },
  {
    name: '[SP-S3-SEC-02] [BE/DevOps] Cấu hình Distributed Rate-Limiting Chống Spam API bằng Redis Token Bucket - (5 STP)',
    desc: `### Bối cảnh & Nghiệp vụ:
Bảo vệ hệ thống khỏi các cuộc tấn công quét bãi đỗ hoặc bot cào dữ liệu slot đỗ xe.

### Đặc tả kỹ thuật:
- Sử dụng Spring Cloud Gateway RequestRateLimiter kết hợp Redis KeyResolver.
- Phân tầng: Anonymous IP tối đa 20 req/s (burst 40 req); Authenticated JWT tối đa 100 req/s (burst 150 req).
- Trả về chuẩn RFC 7807 \`429 Too Many Requests\` kèm header \`Retry-After\`.

### Tiêu chí nghiệm thu (DoD):
1. Bắn 50 req/s từ 1 IP chưa đăng nhập -> Gateway lập tức chặn từ request thứ 21.
2. Không ảnh hưởng lưu lượng người dùng hợp lệ.
- **Story Points:** 5 STP (~14 Hours)
- **Assignee:** Hoàng Đình Tùng (Lead DevOps & Infra)`,
    members: [MEMBERS.tung],
    labels: [LABELS.devops, LABELS.be]
  },
  {
    name: '[SP-S3-OPS-01] [DevOps] Cấu hình Prometheus Alertmanager & Kênh Cảnh Báo Tự Động Telegram - (5 STP)',
    desc: `### Bối cảnh & Nghiệp vụ:
Phát hiện và cảnh báo sự cố kỹ thuật ngay lập tức trước khi người dùng phản ánh.

### Đặc tả kỹ thuật:
- Định nghĩa tập luật cảnh báo trong file \`alert.rules.yml\` cho Prometheus: CPU > 85%, Pod restart, Latency P99 > 800ms, Error 5xx > 2%.
- Cấu hình Alertmanager kết nối Webhook Bot Telegram gửi tin nhắn Markdown theo cấp độ: WARNING, CRITICAL.

### Tiêu chí nghiệm thu (DoD):
1. Tắt thử 1 container microservice -> Bot Telegram bắn thông báo trong vòng 30s.
2. Dashboard Grafana hiển thị trực quan live RPS & Error rate.
- **Story Points:** 5 STP (~14 Hours)
- **Assignee:** Hoàng Đình Tùng (Lead DevOps & Infra)`,
    members: [MEMBERS.tung],
    labels: [LABELS.devops]
  },
  {
    name: '[SP-S3-OPS-02] [DevOps] Tối Ưu Hóa Multi-stage Docker Staging & Cấu Hình Nginx Caching - (4 STP)',
    desc: `### Bối cảnh & Nghiệp vụ:
Tối ưu hóa dung lượng container, tăng tốc độ triển khai staging và thời gian tải trang cho người dùng.

### Đặc tả kỹ thuật:
- Refactor toàn bộ Dockerfile backend sang multi-stage build (Eclipse Temurin JRE 21 Alpine), giảm dung lượng từ 800MB xuống < 220MB.
- Cấu hình Nginx: Gzip/Brotli, cache static asset 30 ngày, bảo mật header (CSP, HSTS).

### Tiêu chí nghiệm thu (DoD):
1. Image Docker build nhanh hơn 40%, dung lượng giảm > 65%.
2. Điểm Google Lighthouse Performance của Frontend đạt > 90.
- **Story Points:** 4 STP (~12 Hours)
- **Assignee:** Hoàng Đình Tùng (Lead DevOps & Infra)`,
    members: [MEMBERS.tung],
    labels: [LABELS.devops]
  },
  {
    name: '[SP-S3-SEC-03] [BE] Tích Hợp SMS Brandname OTP & Cơ Chế Auto-Fallback Sang Email OTP - (3 STP)',
    desc: `### Bối cảnh & Nghiệp vụ:
Xử lý bỏ chặn task [SP-BLK-01], đảm bảo tài xế luôn nhận được mã xác thực OTP khi đăng ký hoặc quên mật khẩu.

### Đặc tả kỹ thuật:
- Kết nối API nhà mạng gửi tin nhắn SMS Brandname xác thực 6 số.
- Thiết lập Circuit Breaker / Timeout: Nếu cổng SMS timeout quá 10 giây, tự động kích hoạt Kafka event gửi mã OTP qua Email.

### Tiêu chí nghiệm thu (DoD):
1. Người dùng nhận mã OTP qua SMS trong < 8 giây.
2. Nếu cổng SMS down, mã OTP lập tức được gửi vào email sau 10 giây.
- **Story Points:** 3 STP (~10 Hours)
- **Assignee:** Hoàng Đình Tùng (Lead DevOps & Infra)`,
    members: [MEMBERS.tung],
    labels: [LABELS.be]
  },

  // --- HUY (30 STP) ---
  {
    name: '[SP-S3-PRC-01] [BE] Thiết Kế & Phát Triển Động Cơ Dynamic Surge Pricing Theo Khung Giờ & Sức Chứa - (8 STP)',
    desc: `### Bối cảnh & Nghiệp vụ:
Tối ưu doanh thu cho Chủ bãi xe và điều tiết mật độ phương tiện theo thời gian thực.

### Đặc tả kỹ thuật:
- Xây dựng công thức tính giá động đa biến trong \`parking-lot-service\`: FinalPrice = BasePrice * Alpha(Occupancy) * Beta(PeakHour) * Gamma(VehicleType).
- Alpha = 1.0 (>30% trống), 1.2 (15-30%), 1.5 (<15% trống).
- Beta = 1.25 (khung giờ 07:00–09:00 và 17:00–19:00).
- Viết API \`POST /api/v1/pricing/calculate-fare\`.

### Tiêu chí nghiệm thu (DoD):
1. Giá cước phản ánh chính xác theo thuật toán, có log giải trình công thức.
2. Thời gian phản hồi tính giá < 45ms dưới tải 200 req/s.
- **Story Points:** 8 STP (~24 Hours)
- **Assignee:** Huyy Gia (Tech Lead & Fullstack)`,
    members: [MEMBERS.huy],
    labels: [LABELS.be, LABELS.ai]
  },
  {
    name: '[SP-S3-RT-01] [FE/BE] Xây Dựng Hệ Thống WebSocket / STOMP Cập Nhật Ô Đỗ Real-Time (Live Slot Matrix) - (6 STP)',
    desc: `### Bối cảnh & Nghiệp vụ:
Tài xế và Chủ bãi không cần nhấn F5 vẫn thấy ngay lập tức ô đỗ nào vừa có xe vào đỗ hoặc vừa rời đi.

### Đặc tả kỹ thuật:
- Dựng STOMP WebSocket Broker trên Spring Boot, subscribe Kafka Topic \`slot.status.changed\`.
- Frontend React kết nối qua \`@stomp/stompjs\` tới endpoint \`/ws-parking\`.
- Giao diện sơ đồ 2D lập tức chuyển màu mượt mà khi nhận sự kiện đổi trạng thái slot.

### Tiêu chí nghiệm thu (DoD):
1. Độ trễ từ khi phát sinh event đến khi Frontend đổi màu ô đỗ < 250ms.
2. Tự động reconnect khi kết nối mạng chập chờn.
- **Story Points:** 6 STP (~18 Hours)
- **Assignee:** Huyy Gia (Tech Lead & Fullstack)`,
    members: [MEMBERS.huy],
    labels: [LABELS.fe, LABELS.be]
  },
  {
    name: '[SP-S3-INT-01] [Fullstack] Tích Hợp Toàn Trình (E2E): Đặt Giữ Chỗ -> VietQR PayOS -> Tự Động Mở Barie - (5 STP)',
    desc: `### Bối cảnh & Nghiệp vụ:
Khớp nối luồng xương sống quan trọng nhất của toàn bộ nền tảng SmartParkingHub.

### Đặc tả kỹ thuật:
- Khớp nối luồng:
  1. Driver chọn bãi xe trên Map -> Bấm Giữ chỗ 10p (Redis Lock).
  2. Gọi PayOS sinh link thanh toán VietQR động.
  3. Webhook PayOS xác nhận tiền về -> Chuyển trạng thái Booking thành PAID và sinh vé QR.
  4. Xe đến cổng -> Camera ANPR nhận diện biển số mở barie tự động.

### Tiêu chí nghiệm thu (DoD):
1. Luồng chạy trơn tru 10 lần liên tục trên môi trường Staging.
2. Không phát sinh lỗi lệch trạng thái (status mismatch).
- **Story Points:** 5 STP (~16 Hours)
- **Assignee:** Huyy Gia (Tech Lead & Fullstack)`,
    members: [MEMBERS.huy],
    labels: [LABELS.fe, LABELS.be]
  },
  {
    name: '[SP-S3-AI-01] [FE/AI] Tích Hợp Trợ Lý AI Chatbot Gemini Copilot Vào Giao Diện Frontend V2 - (4 STP)',
    desc: `### Bối cảnh & Nghiệp vụ:
Trải nghiệm tìm kiếm bãi xe thông minh bằng ngôn ngữ tự nhiên cho tài xế đường dài.

### Đặc tả kỹ thuật:
- Đấu nối component Chatbot Widget với backend \`ai-service\` (Gemini 1.5 Flash Function Calling).
- Hỗ trợ các intent: Tìm bãi đỗ gần nhất, tra cứu giá xe container, kiểm tra chỗ trống.
- Render Interactive Card ngay trong chat kèm nút "Đặt chỗ ngay".

### Tiêu chí nghiệm thu (DoD):
1. Chatbot phản hồi thông tin chính xác trong < 1.5s.
2. Click vào thẻ bãi xe trong khung chat mở ngay modal đặt giữ chỗ.
- **Story Points:** 4 STP (~12 Hours)
- **Assignee:** Huyy Gia (Tech Lead & Fullstack)`,
    members: [MEMBERS.huy],
    labels: [LABELS.fe, LABELS.ai]
  },
  {
    name: '[SP-S3-UI-01] [FE] Hoàn Thiện Màn Hình Điều Khiển Barie Khẩn Cấp & Quét QR Check-in (SCR-36) - (4 STP)',
    desc: `### Bối cảnh & Nghiệp vụ:
Phục vụ bảo vệ tại cổng bãi xe xử lý các ca bất thường (biển số mờ, camera hỏng, tài xế không dùng smartphone).

### Đặc tả kỹ thuật:
- Màn hình SCR-36 (\`MerchantQrScannerPage\`):
  - Tích hợp quét mã QR vé từ webcam.
  - Form nhập biển số xe thủ công.
  - Nút bấm "Mở Barie Khẩn Cấp" (yêu cầu phân quyền và ghi log lý do).

### Tiêu chí nghiệm thu (DoD):
1. Quét mã QR vé phản hồi trong < 800ms.
2. Ghi nhận đầy đủ thông tin mở barie khẩn cấp vào Audit Log.
- **Story Points:** 4 STP (~12 Hours)
- **Assignee:** Huyy Gia (Tech Lead & Fullstack)`,
    members: [MEMBERS.huy],
    labels: [LABELS.fe]
  },
  {
    name: '[SP-S3-INT-02] [FE/BE] Tích Hợp Đồng Bộ Bộ 33 API GAP B vào Frontend V2 - (3 STP)',
    desc: `### Bối cảnh & Nghiệp vụ:
Triệt tiêu hoàn toàn mock data còn sót lại, đưa 100% 53 màn hình Frontend V2 chạy trên dữ liệu thật của Backend.

### Đặc tả kỹ thuật:
- Kết nối các API: Sinh mô tả bãi xe bằng AI (\`/api/v1/ai/generate-description\`), Đánh dấu thông báo đã đọc, Quản lý thư viện ảnh bãi xe, Yêu cầu sửa hồ sơ bãi xe.

### Tiêu chí nghiệm thu (DoD):
1. 100% màn hình sử dụng dữ liệu thực từ backend.
2. Không còn cảnh báo console về mismatch hợp đồng API.
- **Story Points:** 3 STP (~10 Hours)
- **Assignee:** Huyy Gia (Tech Lead & Fullstack)`,
    members: [MEMBERS.huy],
    labels: [LABELS.fe, LABELS.be]
  },

  // --- LỘC (28 STP) ---
  {
    name: '[SP-S3-PAY-01] [BE] Thiết Kế Hệ Thống Sổ Cái Kép (Double-Entry Ledger) & Chống Thất Thoát Ví - (8 STP)',
    desc: `### Bối cảnh & Nghiệp vụ:
Quản lý dòng tiền cọc và doanh thu bãi đỗ với độ chính xác kế toán tài chính tuyệt đối.

### Đặc tả kỹ thuật:
- Thiết kế bảng \`ledger_entries\` và \`wallet_accounts\`.
- Mọi giao dịch luôn có cặp tài khoản Nợ (Debit) và Có (Credit) với Sum(Debit) = Sum(Credit).
- Bảng sổ cái là bất biến (Append-Only), nghiêm cấm UPDATE/DELETE.
- Áp dụng Pessimistic Locking (\`SELECT FOR UPDATE\`) triệt tiêu race condition số dư ví.

### Tiêu chí nghiệm thu (DoD):
1. Tổng số dư tài khoản luôn khớp 100% với lịch sử sổ cái.
2. Bắn 50 request nạp/rút đồng thời vào 1 ví: Số dư cuối cùng chính xác tuyệt đối.
- **Story Points:** 8 STP (~24 Hours)
- **Assignee:** Huỳnh La Tiến Lộc (Senior Backend Architect)`,
    members: [MEMBERS.loc],
    labels: [LABELS.be]
  },
  {
    name: '[SP-S3-PAY-02] [BE] Xây Dựng Cronjob Quyết Toán Doanh Thu Đối Tác Tự Động (Daily Settlement Job) - (6 STP)',
    desc: `### Bối cảnh & Nghiệp vụ:
Tự động hóa phân chia doanh thu giữa Sàn và Chủ bãi xe mỗi ngày mà không cần kế toán làm thủ công.

### Đặc tả kỹ thuật:
- Scheduled Job chạy lúc 23:59:59 hàng ngày:
  1. Quét các đơn booking hoàn tất trong ngày của từng bãi xe.
  2. Khấu trừ phí hoa hồng sàn (Platform Fee).
  3. Kết chuyển 90% doanh thu thuần vào tài khoản khả dụng của Chủ bãi.
  4. Ghi nhận doanh thu sàn.

### Tiêu chí nghiệm thu (DoD):
1. Batch quyết toán xử lý 10,000 giao dịch trong < 30 giây.
2. Xuất bảng kê đối soát doanh thu chi tiết cho Merchant.
- **Story Points:** 6 STP (~18 Hours)
- **Assignee:** Huỳnh La Tiến Lộc (Senior Backend Architect)`,
    members: [MEMBERS.loc],
    labels: [LABELS.be]
  },
  {
    name: '[SP-S3-AI-02] [AI/BE] Xây Dựng API Trích Xuất Sơ Đồ Bãi Xe 2D Bằng Gemini Multimodal Vision - (6 STP)',
    desc: `### Bối cảnh & Nghiệp vụ:
Chủ bãi chỉ cần tải lên ảnh chụp bản vẽ thiết kế bãi đỗ, AI sẽ tự động dựng thành lưới ô đỗ 2D tương tác.

### Đặc tả kỹ thuật:
- API \`POST /api/v1/ai/extract-layout\` trong \`ai-service\`.
- Gửi ảnh mặt bằng bãi xe tới Google Gemini 1.5 Flash kèm Structured Prompt & System Instruction.
- Trả về JSON ma trận ô đỗ ({ slotNumber, slotType, row, col, status }, lối vào/ra, trụ sạc pin).
- Lưu trữ vào schema 2D trong \`parking-lot-service\`.

### Tiêu chí nghiệm thu (DoD):
1. Trích xuất thành công ảnh sơ đồ thành JSON lưới ô đỗ hợp lệ.
2. Phân loại đúng tối thiểu 85% các loại slot đỗ xe tải nặng/nhẹ.
- **Story Points:** 6 STP (~18 Hours)
- **Assignee:** Huỳnh La Tiến Lộc (Senior Backend Architect)`,
    members: [MEMBERS.loc],
    labels: [LABELS.ai, LABELS.be]
  },
  {
    name: '[SP-S3-SAGA-01] [BE] Xây Dựng Luồng Hủy Vé Hoàn Tiền Tự Động (Refund Saga & Transactional Outbox) - (5 STP)',
    desc: `### Bối cảnh & Nghiệp vụ:
Xử lý hoàn tiền vé hủy theo chính sách minh bạch mà không bị treo tiền khi mất kết nối mạng.

### Đặc tả kỹ thuật:
- Transactional Outbox Pattern: Khi hủy vé, trạng thái hủy và lệnh refund được ghi cùng 1 transaction trong \`booking-service\`.
- Outbox worker đẩy event sang Kafka topic \`booking.refund.requested\`.
- \`payment-service\` tiêu thụ event, gọi PayOS hoàn tiền hoặc cộng tiền vào ví hoàn cọc.

### Tiêu chí nghiệm thu (DoD):
1. Giả lập ngắt kết nối mạng khi hủy vé: Hệ thống tự động retry và hoàn tiền thành công sau khi phục hồi mạng.
2. Không bị mất mát giao dịch hay chênh lệch số dư.
- **Story Points:** 5 STP (~16 Hours)
- **Assignee:** Huỳnh La Tiến Lộc (Senior Backend Architect)`,
    members: [MEMBERS.loc],
    labels: [LABELS.be]
  },
  {
    name: '[SP-S3-DAT-01] [BE] Viết Flyway Migration V4 & Tối Ưu Hóa Khóa Ngoại Ngân Hàng Rút Tiền - (3 STP)',
    desc: `### Bối cảnh & Nghiệp vụ:
Đồng bộ hóa cơ sở dữ liệu môi trường Staging/Production phục vụ rút tiền ngân hàng của Chủ bãi.

### Đặc tả kỹ thuật:
- Viết script Flyway \`V4__add_merchant_bank_and_withdrawal_tables.sql\`: Bổ sung bảng \`merchant_bank_accounts\`, \`withdrawal_requests\`, đánh index trên \`(merchant_id, created_at)\`.

### Tiêu chí nghiệm thu (DoD):
1. Lệnh flywayMigrate chạy thành công 100%.
2. Không làm gián đoạn hay mất dữ liệu hiện hữu.
- **Story Points:** 3 STP (~10 Hours)
- **Assignee:** Huỳnh La Tiến Lộc (Senior Backend Architect)`,
    members: [MEMBERS.loc],
    labels: [LABELS.be]
  },

  // --- KHẢI (22 STP) ---
  {
    name: '[SP-S3-BKG-01] [BE] Xây Dựng Cơ Chế Hàng Đợi Giữ Chỗ Thông Minh (Waitlist Queue) Bằng Redis - (6 STP)',
    desc: `### Bối cảnh & Nghiệp vụ:
Khi bãi xe đã kín chỗ, tài xế có thể xếp hàng vào danh sách chờ; khi có xe ra bãi, người xếp hàng đầu tiên được ưu tiên nhận chỗ ngay.

### Đặc tả kỹ thuật:
- Áp dụng Redis Sorted Set: Key \`waitlist:lot:{lotId}:{slotType}\`, Score là Unix Timestamp đăng ký.
- Khi có event \`vehicle.checked_out\`, Worker gọi \`ZPOPMIN\` lấy tài xế đầu hàng đợi.
- Khóa giữ slot ưu tiên trong 5 phút (TTL 300s) và gửi thông báo. Quá 5 phút không đặt cọc -> Tự động chuyển người kế tiếp.

### Tiêu chí nghiệm thu (DoD):
1. Hàng đợi hoạt động đúng nguyên tắc FIFO.
2. Tự động hủy quyền ưu tiên sau đúng 5 phút nếu không đặt cọc.
- **Story Points:** 6 STP (~18 Hours)
- **Assignee:** Hồ Quốc Khải (Backend Logic & QA Specialist)`,
    members: [MEMBERS.khai],
    labels: [LABELS.be]
  },
  {
    name: '[SP-S3-GOV-01] [BE] Triển Khai State Machine Guards Cho Quy Trình Duyệt Bãi Xe & Xác Thực KYC - (5 STP)',
    desc: `### Bối cảnh & Nghiệp vụ:
Ngăn chặn bãi xe "ma" hoặc tài khoản Merchant chưa xác thực thông tin ngân hàng lừa đảo nhận tiền cọc.

### Đặc tả kỹ thuật:
- Bãi xe chỉ được chuyển \`ACTIVE\` khi: Trạng thái hiện tại là \`APPROVED\`, có ít nhất 1 slot và đã cấu hình biểu phí hợp lệ.
- Merchant chỉ được tạo lệnh rút tiền khi: Hồ sơ KYC ở trạng thái \`VERIFIED\` và tài khoản ngân hàng đã được xác thực OTP.

### Tiêu chí nghiệm thu (DoD):
1. Ném BusinessException rõ ràng khi cố tình vi phạm luồng duyệt.
2. Chặn 100% bãi xe chưa duyệt hiển thị trên màn hình tìm kiếm.
- **Story Points:** 5 STP (~16 Hours)
- **Assignee:** Hồ Quốc Khải (Backend Logic & QA Specialist)`,
    members: [MEMBERS.khai],
    labels: [LABELS.be]
  },
  {
    name: '[SP-S3-QA-01] [QA] Xây Dựng Kịch Bản Đo Kiểm Chịu Tải 1,000 CCU Bằng Apache JMeter - (4 STP)',
    desc: `### Bối cảnh & Nghiệp vụ:
Chứng minh năng lực chịu tải đồng thời của hệ thống trước hội đồng phản biện đồ án.

### Đặc tả kỹ thuật:
- Thiết kế kịch bản kiểm thử hiệu năng với Apache JMeter:
  - Giả lập 1,000 người dùng ảo đồng thời (1,000 CCU) tranh chấp 10 chỗ đỗ xe cuối cùng tại 1 bãi xe trong 10 giây.
  - Đo lường Throughput (RPS), Response Time (P95, P99), Error Rate.

### Tiêu chí nghiệm thu (DoD):
1. Tuyệt đối không xảy ra hiện tượng bán vượt chỗ (Zero Overbooking) — Chỉ có đúng 10 người giữ chỗ thành công.
2. Tỷ lệ lỗi hệ thống (Crash/500 Internal Error) < 0.1%.
- **Story Points:** 4 STP (~14 Hours)
- **Assignee:** Hồ Quốc Khải (Backend Logic & QA Specialist)`,
    members: [MEMBERS.khai],
    labels: [LABELS.qa]
  },
  {
    name: '[SP-S3-QA-02] [QA] Nâng Độ Bao Phủ Unit Test Toàn Hệ Thống Vượt Ngưỡng 80% SonarQube - (4 STP)',
    desc: `### Bối cảnh & Nghiệp vụ:
Giải quyết dứt điểm task tồn đọng [SP-REV-03], đảm bảo mã nguồn đạt chuẩn bàn giao công nghiệp.

### Đặc tả kỹ thuật:
- Viết bổ sung Unit Test và Slice Test cho tầng Service và Controller của \`booking-service\`, \`payment-service\`, \`parking-lot-service\`.
- Mockito mock các kết nối ngoại vi (PayOS, MinIO, Redis, Kafka).

### Tiêu chí nghiệm thu (DoD):
1. SonarQube quét toàn bộ microservices đạt Code Coverage > 80%.
2. 0 Security Vulnerabilities, 0 Blocker Bugs.
- **Story Points:** 4 STP (~14 Hours)
- **Assignee:** Hồ Quốc Khải (Backend Logic & QA Specialist)`,
    members: [MEMBERS.khai],
    labels: [LABELS.qa]
  },
  {
    name: '[SP-S3-QA-03] [QA] Xây Dựng Bộ Kiểm Thử Tự Động Toàn Diện Bằng Postman Collection - (3 STP)',
    desc: `### Bối cảnh & Nghiệp vụ:
Tự động hóa kiểm thử hồi quy toàn bộ 91 API endpoints chỉ với 1 click chuột trên CI/CD.

### Đặc tả kỹ thuật:
- Soạn thảo Postman Collection đầy đủ các kịch bản Success/Error, tự động trích xuất Bearer Token gán vào biến môi trường.
- Tích hợp chạy tự động qua công cụ Newman CLI.

### Tiêu chí nghiệm thu (DoD):
1. Chạy toàn bộ 91 endpoints tự động qua Newman CLI thành công 100%.
2. Xuất báo cáo HTML kết quả kiểm thử rõ ràng.
- **Story Points:** 3 STP (~10 Hours)
- **Assignee:** Hồ Quốc Khải (Backend Logic & QA Specialist)`,
    members: [MEMBERS.khai],
    labels: [LABELS.qa]
  },

  // --- KHANG (16 STP) ---
  {
    name: '[SP-S3-UI-02] [FE] Hoàn Thiện Nhóm Trang Tĩnh Tiếp Thị & Tuyển Dụng (About, Careers, Solutions) - (4 STP)',
    desc: `### Bối cảnh & Nghiệp vụ:
Tạo diện mạo chuyên nghiệp, tin cậy cho nền tảng đối với khách hàng doanh nghiệp và đối tác vận tải.

### Đặc tả kỹ thuật:
- Hoàn thiện giao diện các trang: Giới thiệu (/about), Tuyển dụng (/careers), Giải pháp Chủ bãi (/owner-solutions), Giải pháp Tài xế (/driver-solutions).
- Áp dụng chuẩn Design Tokens (mã màu #d95f00, font Sora/Inter), không can thiệp logic state hay API giao dịch.

### Tiêu chí nghiệm thu (DoD):
1. Không có lỗi layout; hình ảnh hiển thị sắc nét.
2. Responsive 100% trên cả Desktop và Mobile; liên kết điều hướng mượt mà.
- **Story Points:** 4 STP (~12 Hours)
- **Assignee:** Võ Thành Khang (Frontend Content & Demo Artifact)`,
    members: [MEMBERS.khang],
    labels: [LABELS.fe]
  },
  {
    name: '[SP-S3-UI-03] [FE] Hoàn Thiện Nhóm Trang Tin Tức Logistics, FAQ & Điều Khoản Dịch Vụ - (3 STP)',
    desc: `### Bối cảnh & Nghiệp vụ:
Cung cấp thông tin hỗ trợ tài xế tra cứu quy định và cập nhật kiến thức ngành logistics.

### Đặc tả kỹ thuật:
- Xây dựng giao diện Blog tin tức (/blog), Chi tiết bài viết (/blog/:id), Câu hỏi thường gặp (/faq), Điều khoản bảo mật (/terms).
- Tích hợp bộ lọc bài viết theo chủ đề và ô tìm kiếm câu hỏi FAQ dạng Accordion mở rộng/thu gọn.

### Tiêu chí nghiệm thu (DoD):
1. Đầy đủ 6 bài viết mẫu và 12 câu hỏi giải đáp chi tiết.
2. Hiệu ứng mở Accordion mượt mà, chuẩn accessibility.
- **Story Points:** 3 STP (~10 Hours)
- **Assignee:** Võ Thành Khang (Frontend Content & Demo Artifact)`,
    members: [MEMBERS.khang],
    labels: [LABELS.fe]
  },
  {
    name: '[SP-S3-DAT-02] [Chore] Xây Dựng Bộ Script Dữ Liệu Mẫu (Mock Seed Data) Đa Dạng Cho 4 Vai Trò - (3 STP)',
    desc: `### Bối cảnh & Nghiệp vụ:
Phục vụ dữ liệu phong phú, sống động cho buổi nghiệm thu và quay video demo đồ án.

### Đặc tả kỹ thuật:
- Viết script SQL/JSON nạp dữ liệu mẫu:
  - 10 bãi xe container lớn tại TP.HCM kèm tọa độ GPS thật và ảnh bãi xe đẹp.
  - 4 tài khoản thử nghiệm tương ứng 4 role (Admin, Merchant, Driver, Guard).
  - 20 đơn đặt chỗ mẫu với đầy đủ các trạng thái (Đã thanh toán, Đang đỗ, Đã hủy, v.v.).

### Tiêu chí nghiệm thu (DoD):
1. Chạy script nạp dữ liệu hoàn tất trong 5 giây.
2. Dữ liệu trực quan, chân thực trên bản đồ bãi xe.
- **Story Points:** 3 STP (~10 Hours)
- **Assignee:** Võ Thành Khang (Frontend Content & Demo Artifact)`,
    members: [MEMBERS.khang],
    labels: [LABELS.doc]
  },
  {
    name: '[SP-S3-DOC-01] [Chore] Thiết Kế Bộ Slide Báo Cáo Nghiệm Thu Đồ Án Chuyên Nghiệp (Pitch Deck) - (3 STP)',
    desc: `### Bối cảnh & Nghiệp vụ:
Trình bày thành quả công việc của toàn đội ngũ một cách thuyết phục trước hội đồng đánh giá.

### Đặc tả kỹ thuật:
- Thiết kế Slide 18–22 trang trên Canva/PowerPoint:
  - Đặt vấn đề ùn tắc bãi đỗ & giải pháp SmartParkingHub.
  - Sơ đồ kiến trúc Microservices & Hạ tầng phân tán (Kafka, Redis, Docker, Gateway).
  - Các tính năng đột phá: Nhận diện biển số ANPR, Thuật toán giá động, Trích xuất sơ đồ 2D bằng Gemini AI.
  - Kết quả kiểm thử chịu tải 1,000 CCU và chuẩn chất lượng SonarQube.

### Tiêu chí nghiệm thu (DoD):
1. Slide hiện đại, bố cục rõ ràng, súc tích.
2. Minh họa đầy đủ sơ đồ kiến trúc và biểu đồ kết quả đo kiểm hiệu năng.
- **Story Points:** 3 STP (~10 Hours)
- **Assignee:** Võ Thành Khang (Frontend Content & Demo Artifact)`,
    members: [MEMBERS.khang],
    labels: [LABELS.doc]
  },
  {
    name: '[SP-S3-DOC-02] [Chore] Xây Dựng Kịch Bản Trình Diễn & Sản Xuất Video Demo Toàn Trình Hệ Thống - (3 STP)',
    desc: `### Bối cảnh & Nghiệp vụ:
Đảm bảo buổi nghiệm thu diễn ra suôn sẻ, có video minh chứng lưu trữ và dự phòng rủi ro kỹ thuật lúc bảo vệ trực tiếp.

### Đặc tả kỹ thuật:
- Viết kịch bản Demo chi tiết từng bước (Step-by-step Demo Script):
  1. Chủ bãi đăng ký, AI Gemini đọc ảnh sơ đồ bãi xe, Admin duyệt bãi.
  2. Tài xế mở app, chat hỏi Gemini tìm bãi, chọn slot và quét VietQR PayOS.
  3. Xe đến cổng: Camera ANPR quét biển số, barie tự động nâng lên.
  4. Chủ bãi theo dõi doanh thu realtime và tạo lệnh rút tiền ngân hàng.
- Quay video Full HD 1080p, thời lượng 5–7 phút có phụ đề/thuyết minh.

### Tiêu chí nghiệm thu (DoD):
1. Video quay sắc nét, liền mạch, âm thanh rõ ràng.
2. Thể hiện trọn vẹn giá trị cốt lõi của dự án.
- **Story Points:** 3 STP (~10 Hours)
- **Assignee:** Võ Thành Khang (Frontend Content & Demo Artifact)`,
    members: [MEMBERS.khang],
    labels: [LABELS.doc]
  }
];

async function createCards() {
  console.log(`Bắt đầu tạo ${tasks.length} thẻ trên Trello trong cột 'Sprint Backlog 3'...`);

  for (let i = 0; i < tasks.length; i++) {
    const task = tasks[i];
    const url = `https://api.trello.com/1/cards?key=${key}&token=${token}`;
    const body = {
      name: task.name,
      desc: task.desc,
      idList: LIST_ID,
      idMembers: task.members,
      idLabels: task.labels,
      pos: (i + 1) * 1024
    };

    try {
      const res = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
      });

      if (res.ok) {
        const data = await res.json();
        console.log(`[${i + 1}/${tasks.length}] ✅ Đã tạo thẻ: "${task.name.slice(0, 40)}..." (ID: ${data.id.slice(-6)})`);
      } else {
        const err = await res.text();
        console.error(`[${i + 1}/${tasks.length}] ❌ Lỗi tạo thẻ: ${task.name}`, err);
      }
    } catch (e) {
      console.error(`Lỗi mạng khi tạo thẻ ${task.name}:`, e.message);
    }

    // Delay 120ms to respect rate limit
    await new Promise(r => setTimeout(r, 120));
  }

  console.log('\n🎉 ĐÃ TẠO TOÀN BỘ 28 THẺ THÀNH CÔNG TRONG CỘT SPRINT BACKLOG 3 TRÊN TRELLO!');
}

createCards();
