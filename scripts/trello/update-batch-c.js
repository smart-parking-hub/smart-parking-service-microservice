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

const batchCCards = [
  { id: '6aeb0f14e6d179d73f6f53fd', name: '[SP-S2-UI-029] [FE] Đăng ký Tạo mới Bãi đỗ & Tải ảnh Mặt bằng (CreateMerchantParkingLotPage) (SCR-029) (5 STP)' },
  { id: '6aeb0eeafb8b907aa3046284', name: '[SP-S2-UI-022] [FE] Thay đổi Mật khẩu Bảo mật Tài khoản (ChangePasswordPage) (SCR-022) (2 STP)' },
  { id: '6aeb0ea15a6e4cea680e9300', name: '[SP-S2-UI-010] [FE] Đăng nhập Tài khoản Đa vai trò (LoginPage) (SCR-010) (3 STP)' },
  { id: '6aeb0ead466359dca27a5c66', name: '[SP-S2-UI-012] [FE] Khôi phục Mật khẩu qua Mã xác thực OTP (ForgotPasswordPage) (SCR-012) (3 STP)' },
  { id: '6aeb0f6c7bbcd923cee00895', name: '[SP-S2-UI-043] [FE] Danh sách Kiểm duyệt Bãi đỗ Toàn hệ thống (AdminParkingLotsPage) (SCR-043) (3 STP)' },
  { id: '6aeb0f726c8780b5f0237824', name: '[SP-S2-UI-044] [FE] Thẩm định Hồ sơ Mặt bằng & Ra quyết định Duyệt (AdminParkingLotDetailPage) (SCR-044) (5 STP)' },
  { id: '6aeb0ea71ca263e4ef89d87e', name: '[SP-S2-UI-011] [FE] Đăng ký Tài khoản Tài xế / Chủ bãi (RegisterPage) (SCR-011) (3 STP)' },
  { id: '6aeb0f26c03ea989160b603e', name: '[SP-S2-UI-032] [FE] Cấu hình Sức chứa Sơ đồ 5 Loại xe (MerchantSlotsPage) (SCR-032) (5 STP)' },
  { id: '6aeb0e95aa0ae005525cae0f', name: '[SP-S2-UI-008] [FE] Tin tức, Kiến thức Logistics & Báo cáo (BlogPage) (SCR-008) (2 STP)' },
  { id: '6aeb0fa79fec8d23f60e26c4', name: '[SP-S2-UI-053] [FE] Hồ sơ Quản trị viên & Phiên Đăng nhập Bảo mật (AdminProfilePage) (SCR-053) (2 STP)' },
  { id: '6aeb0e883482f4ec29b94f74', name: '[SP-S2-UI-006] [FE] Giới thiệu Doanh nghiệp & Đội ngũ (AboutUsPage) (SCR-006) (2 STP)' },
  { id: '6aeb0f3e959aa413a7f11b5a', name: '[SP-S2-UI-036] [FE] Camera Quét QR Barie Ra/Vào & Thu tiền mặt (MerchantQrScannerPage) (SCR-036) (5 STP)' },
  { id: '6aeb0ed2689b364a4fc74084', name: '[SP-S2-UI-018] [FE] Theo dõi Vị trí Hàng đợi Ảo Thông minh (DriverQueuesPage) (SCR-018) (5 STP)' },
  { id: '6aeb0ee45f5c71e221bc8c9a', name: '[SP-S2-UI-021] [FE] Hồ sơ Cá nhân & Cài đặt Tài xế (ProfilePage) (SCR-021) (2 STP)' },
  { id: '6aeb0ede53b12121d7877a0d', name: '[SP-S2-UI-020] [FE] Quản lý Đội xe & Phương tiện Tài xế (VehiclesPage) (SCR-020) (3 STP)' },
  { id: '6aeb0eb9b689c09ebb21bd6f', name: '[SP-S2-UI-014] [FE] Bảng điều khiển Tổng quan Tài xế (DriverDashboardPage) (SCR-014) (3 STP)' },
  { id: '6aeb0ebfabcc145945dbb3a6', name: '[SP-S2-UI-015] [FE] Bản đồ Tương tác & Tìm kiếm Bãi đỗ Xe tải (SearchParkingPage) (SCR-015) (8 STP)' },
  { id: '6aeb0ec55a7a06589d22c186', name: '[SP-S2-UI-016] [FE] Chi tiết Bãi đỗ & Modal Đặt giữ chỗ 10p (ParkingDetailPage) (SCR-016) (8 STP)' },
  { id: '6aeb0eccf02dd5bdb47a8f41', name: '[SP-S2-UI-017] [FE] Quản lý Danh sách Đơn Đặt chỗ của tôi (MyBookingsPage) (SCR-017) (5 STP)' },
  { id: '6aeb0e6f77f16906077bf6fa', name: '[SP-S2-UI-002] [FE] Giải pháp Dành cho Tài xế Xe tải (DriverSolutionsPage) (SCR-002) (2 STP)' },
  { id: '6aeb0e75432d6bb225541bf8', name: '[SP-S2-UI-003] [FE] Giải pháp Dành cho Chủ bãi Đỗ (OwnerSolutionsPage) (SCR-003) (2 STP)' }
];

async function updateBatchC() {
  console.log(`Bắt đầu chuẩn hóa BATCH C (${batchCCards.length} thẻ trong 10. Sprint 02 Done)...`);
  for (let i = 0; i < batchCCards.length; i++) {
    const card = batchCCards[i];
    const url = `https://api.trello.com/1/cards/${card.id}?key=${key}&token=${token}`;
    try {
      const res = await fetch(url, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: card.name })
      });
      if (res.ok) {
        console.log(`[${i + 1}/${batchCCards.length}] ✅ Đã cập nhật: ${card.name}`);
      } else {
        console.error(`[${i + 1}/${batchCCards.length}] ❌ Lỗi thẻ ${card.id}:`, await res.text());
      }
    } catch (e) {
      console.error(`Lỗi mạng:`, e.message);
    }
    await new Promise(r => setTimeout(r, 120));
  }
  console.log('\n🎉 HOÀN TẤT BATCH C!');
}

updateBatchC();
