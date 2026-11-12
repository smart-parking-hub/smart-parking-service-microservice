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

const updates = [
  {
    id: "6aeae68c49a48143101a2ecd",
    name: "[SP-S1-UI-01] [FE] Trang chủ Marketing Landing Page & Giới thiệu Nền tảng (LandingPage) (SCR-001) (5 STP)"
  },
  {
    id: "6aeae65317d7e2845ac6541e",
    name: "[SP-S1-DAT-01] [DB] Thiết kế Schema Phân tán & Flyway Migration cho 5 Dịch vụ (5 STP)"
  },
  {
    id: "6aeae64dfca42856ade308ef",
    name: "[SP-S1-SEC-01] [BE] Khởi tạo Spring Cloud Gateway & Bộ lọc Xác thực JWT (5 STP)"
  },
  {
    id: "6aeae6586004eb42769ddd2f",
    name: "[SP-S1-SEC-02] [BE] Xây dựng Identity Service Xác thực & Phân quyền RBAC (8 STP)"
  },
  {
    id: "6aeae669c694c2e16b888267",
    name: "[SP-S1-BKG-01] [BE] Cơ chế Hàng đợi ảo Thông minh & Đánh giá Bãi đỗ (5 STP)"
  },
  {
    id: "6aeae6741cc628b5e0a57767",
    name: "[SP-S1-SAGA-01] [BE] Hệ thống Phát sự kiện Kafka & Transactional Outbox (5 STP)"
  },
  {
    id: "6aeae65f62b808225ce35890",
    name: "[SP-S1-BKG-02] [BE] Parking Lot Service Tra cứu Không gian & Quản lý Slot (5 STP)"
  },
  {
    id: "6aeae679b08a2090d94e74a9",
    name: "[SP-S1-OPS-01] [OPS] Docker Compose Đa hồ sơ Phân tách Local Dev & Local Lab (5 STP)"
  },
  {
    id: "6aeae67e87c4bedbbf4d9892",
    name: "[SP-S1-OPS-02] [OPS] GitHub Actions Tự động Kiểm thử & Biên dịch Backend JDK 21 (3 STP)"
  },
  {
    id: "6aeae97e28f9270c46d61f29",
    name: "[SP-S1-OPS-03] [OPS] Docker Compose Phục vụ Triển khai VPS Cluster (5 STP)"
  },
  {
    id: "6aeae983535518f3faec87e8",
    name: "[SP-S1-OPS-04] [OPS] GitHub Actions Build & Xuất bản Docker Images lên GHCR (5 STP)"
  },
  {
    id: "6aeae98757318f228a36f560",
    name: "[SP-S1-OPS-05] [OPS] Tự động hóa Triển khai CD Đồng bộ Cụm 3 VPS qua SSH (8 STP)"
  },
  {
    id: "6aeae98c634e4cac608ecf21",
    name: "[SP-S1-OPS-06] [OPS] Nginx Reverse Proxy, Tường lửa An ninh & Script Sao lưu DB (5 STP)"
  },
  {
    id: "6aeae6834fd9955375f77d1d",
    name: "[SP-S1-UI-02] [FE] Đăng nhập & Xác thực Phiên làm việc (LoginPage) (SCR-010) (5 STP)"
  },
  {
    id: "6aeae6886efecf7dd499900c",
    name: "[SP-S1-UI-03] [FE] Đăng ký Tài khoản & Khôi phục Mật khẩu OTP (RegisterPage) (SCR-011) (5 STP)"
  },
  {
    id: "6aeae696c0f0a5ddcf300121",
    name: "[SP-S1-UI-04] [FE] Bản đồ Tìm kiếm Bãi đỗ Trực quan Leaflet Map (SearchParkingPage) (SCR-015) (5 STP)"
  },
  {
    id: "6aeae6a5d1b13e322778e7c0",
    name: "[SP-S1-UI-05] [FE] Chi tiết Bãi đỗ & Modal Đặt chỗ Giữ chỗ (ParkingDetailPage) (SCR-016) (5 STP)"
  },
  {
    id: "6aeae6917f64d6ef2169bf9b",
    name: "[SP-S1-UI-06] [FE] Bảng điều khiển Tổng quan Tài xế (DriverDashboardPage) (SCR-014) (5 STP)"
  },
  {
    id: "6aeae6b0646b2c747c2e7329",
    name: "[SP-S1-UI-07] [FE] Quản lý Phương tiện & Đội xe của tôi (VehiclesPage) (SCR-020) (5 STP)"
  },
  {
    id: "6aeae69f5ce521b96b61c45e",
    name: "[SP-S1-UI-08] [FE] Bảng Đặt chỗ Nhanh Inline Quick Book Giữ chỗ 10 Phút (5 STP)"
  },
  {
    id: "6aeae664e1b95bae2c21d190",
    name: "[SP-S1-BKG-03] [BE] Booking Service & Động cơ Khóa Giữ chỗ 10 Phút Hold TTL (8 STP)"
  },
  {
    id: "6aeae6aa2871a9813d80f9ac",
    name: "[SP-S1-UI-09] [FE] Quản lý Đơn Đặt chỗ & QR Terminal Pass (MyBookingsPage) (SCR-017) (5 STP)"
  },
  {
    id: "6aeae6be8567e4cebe9783ad",
    name: "[SP-S1-UI-10] [FE] Theo dõi Vị trí Hàng đợi Ảo Thông minh (DriverQueuesPage) (SCR-018) (3 STP)"
  },
  {
    id: "6aeae66edabeab0350817cc4",
    name: "[SP-S1-PAY-01] [BE] Payment Service Tích hợp PayOS & Sổ cái Kép Ledger (8 STP)"
  },
  {
    id: "6aeae6b9e747ae7367bcb012",
    name: "[SP-S1-UI-11] [FE] Thông báo Hủy Thanh toán & Bảo lưu Vị trí Đỗ (PaymentCancelPage) (SCR-019) (3 STP)"
  },
  {
    id: "6aeae6b49b3eae985eed7b7d",
    name: "[SP-S1-UI-12] [FE] Tiếp nhận Kết quả Thanh toán PayOS Thành công (PaymentSuccessPage) (SCR-019B) (3 STP)"
  },
  {
    id: "6aeae6c3acff9832e1b37aa0",
    name: "[SP-S1-UI-13] [FE] Lịch sử Giao dịch & Lịch sử Đỗ xe Cá nhân (PaymentHistoryPage) (SCR-023) (5 STP)"
  }
];

async function run() {
  console.log(`Bắt đầu cập nhật BATCH E (${updates.length} thẻ trong 10. Sprint 01 Done)...`);
  for (let i = 0; i < updates.length; i++) {
    const card = updates[i];
    const url = `https://api.trello.com/1/cards/${card.id}?key=${key}&token=${token}`;
    try {
      const res = await fetch(url, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: card.name })
      });
      if (res.ok) {
        console.log(`[${i + 1}/${updates.length}] OK: ${card.name}`);
      } else {
        const text = await res.text();
        console.error(`[${i + 1}/${updates.length}] FAIL ${card.id}: ${res.status} - ${text}`);
      }
      await new Promise(r => setTimeout(r, 150));
    } catch (e) {
      console.error(`Error updating ${card.id}:`, e.message);
    }
  }
  console.log("Hoàn thành Batch E!");
}

run();
