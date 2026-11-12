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

const SPRINT3_LABEL = '6aeae16d5ee40e0053676fbf'; // Sprint 03

const MEMBERS = {
  tung: '6930e5b55d3528613be81a82',
  huy: '69016d7a8c58ffe25753e224',
  loc: '68f0354b7e270498cfc5666a',
  khai: '6a6b571e2274ff581eb61742',
  khang: '692e4f13a054491cdb09b6f8'
};

const cardsToUpdate = [
  // Sprint Goal 03
  {
    id: '6af14329cb7ce2565da72505',
    name: '🎯 Sprint Goal 03: Hoàn thiện Quản lý Sàn (Admin/Merchant), Tích hợp Thanh toán & Đối soát, Tối ưu Hàng đợi ảo và Kiểm thử toàn diện E2E (154 STP)',
    member: null
  },

  // --- TÙNG (7 tasks - 19 STP - KHÓ: Bảo Mật, CI/CD, Hạ Tầng & Spatial Database) ---
  {
    id: '6aeb0dd7eb63b5ead21a7b83',
    name: '[SP-S3-SEC-01] [SECURITY] Tái cấu trúc cơ chế OTP: Chuyển sang SMTP Server Backend & Hủy bỏ trường otpCode trong DTO (GAP-09) (3 STP)',
    member: 'tung'
  },
  {
    id: '6aeae1b65751708e47a3e025',
    name: '[SP-S3-SEC-02] [Security] Rà soát và loại bỏ việc gửi mã OTP trong response demo (GAP-09) (2 STP)',
    member: 'tung'
  },
  {
    id: '6aeae18dfb37261374dcc65d',
    name: '[SP-S3-OPS-01] [CI/CD] Thiết lập pipeline GitHub Actions tự động kiểm tra chất lượng Frontend v2 (3 STP)',
    member: 'tung'
  },
  {
    id: '6aeb0e31ab56be8ab36cadb0',
    name: '[SP-S3-DAT-01] [DATABASE] Thiết lập Spatial Index (ST_Distance_Sphere) và bộ đệm Redis Cache-Aside cho API tìm bãi (3 STP)',
    member: 'tung'
  },
  {
    id: '6aeb0dc572d101c0e2d91da4',
    name: '[SP-S3-RT-01] [BE] Triển khai API Bật/Tắt Trạng thái Mở/Tạm dừng Bãi Đỗ Xe Thời Gian Thực (GAP-16) (3 STP)',
    member: 'tung'
  },
  {
    id: '6aeb0f9ca5b36ca5f1d8e306',
    name: '[SP-S3-SEC-03] [FE] Nhật ký Kiểm toán An ninh & Vết Quản trị (AdminAuditLogsPage) (SCR-051) (3 STP)',
    member: 'tung'
  },
  {
    id: '6aec0ae38abeb86974d3cd6d',
    name: '[SP-S3-SEC-04] [FE] Thay thế axios trần bằng apiClient để gắn Authorization Token cho Notification API (GAP-19) (2 STP)',
    member: 'tung'
  },

  // --- HUY (8 tasks - 34 STP - KHÓ: E2E Playwright, Dynamic Pricing, Quản Trị Sàn & Contract DTO) ---
  {
    id: '6aeb0ddcb7f015ffe8a907bf',
    name: '[SP-S3-INT-01] [QA] Xây dựng bộ kịch bản kiểm thử E2E tự động (Playwright) cho luồng Giữ chỗ 10 phút & Barie QR (5 STP)',
    member: 'huy'
  },
  {
    id: '6aec0ae11c7ed614eaf6b1ff',
    name: '[SP-S3-INT-02] [FE+BE] Sửa lỗi lệch Contract DTO và cơ chế Fallback Mock khi Đặt cọc giữ chỗ (GAP-17) (5 STP)',
    member: 'huy'
  },
  {
    id: '6aeae18e7ab30a12756e0b4d',
    name: '[SP-S3-PAY-01] [BE] Đồng bộ hóa hợp đồng API thu tiền mặt Barie confirm-cash (GAP-02) (3 STP)',
    member: 'huy'
  },
  {
    id: '6aeae18fcadfd8c144060950',
    name: '[SP-S3-BKG-01] [BE] Bổ sung API truy vấn chi tiết đơn đặt chỗ cho chủ bãi theo ID (GAP-06) (3 STP)',
    member: 'huy'
  },
  {
    id: '6aeb0f03d074be4e7f25299c',
    name: '[SP-S3-UI-01] [FE] Bảng Điều khiển Tổng quan Vận hành Chủ bãi (MerchantDashboardPage) (SCR-026) (5 STP)',
    member: 'huy'
  },
  {
    id: '6aeb0f66955d882f7f081609',
    name: '[SP-S3-UI-02] [FE] Trung tâm Điều hành Toàn sàn Super Admin (AdminDashboardPage) (SCR-042) (5 STP)',
    member: 'huy'
  },
  {
    id: '6aeb0f2c7bece0719e972573',
    name: '[SP-S3-PRC-01] [FE] Thiết lập Biểu phí Ma trận 4 Thông số (MerchantPricingPage) (SCR-033) (5 STP)',
    member: 'huy'
  },
  {
    id: '6aec0ae6e85b218f0abcd01b',
    name: '[SP-S3-INT-03] [FE] Loại bỏ Mock Data biểu đồ và tích hợp API chuỗi doanh thu ngày cho Merchant Dashboard (GAP-22) (3 STP)',
    member: 'huy'
  },

  // --- LỘC (7 tasks - 32 STP - KHÓ: Đối Soát Tài Chính Kép, Doanh Thu, Rút Tiền & Trọng Tài Bồi Thường) ---
  {
    id: '6aeae1b86d504b5a734dd19c',
    name: '[SP-S3-PAY-02] [QA] Kiểm thử toàn diện quy trình đối soát tài chính kép và duyệt rút tiền (5 STP)',
    member: 'loc'
  },
  {
    id: '6aeb0f4432b1657fe29a10d7',
    name: '[SP-S3-PAY-03] [FE] Phân tích Biểu đồ Doanh thu & Sổ cái Ví (MerchantRevenuePage) (SCR-037) (5 STP)',
    member: 'loc'
  },
  {
    id: '6aeb0f4bd41db630688a2103',
    name: '[SP-S3-PAY-04] [FE] Tạo Yêu cầu Rút tiền & Quản lý Giải ngân (MerchantWithdrawalsPage) (SCR-038) (5 STP)',
    member: 'loc'
  },
  {
    id: '6aeb0f84bd4fe57a3eb1a785',
    name: '[SP-S3-PAY-05] [FE] Sổ cái Giám sát Giao dịch Dòng tiền Toàn hệ thống (AdminTransactionsPage) (SCR-047) (5 STP)',
    member: 'loc'
  },
  {
    id: '6aeb0f8ab1cfb4f0f389da16',
    name: '[SP-S3-PAY-06] [FE] Phê duyệt & Quyết toán Lệnh Rút tiền Ví Chủ bãi (AdminWithdrawalsPage) (SCR-048) (5 STP)',
    member: 'loc'
  },
  {
    id: '6aeb0f9617aabd604a27a3bd',
    name: '[SP-S3-PAY-07] [FE] Phán quyết Trọng tài & Phân bổ Tiền bồi thường (AdminDisputeDetailPage) (SCR-050) (5 STP)',
    member: 'loc'
  },
  {
    id: '6aeb0f096c29d1cb11bdb011',
    name: '[SP-S3-PAY-08] [FE] Hồ sơ Chủ bãi & Thông tin Ngân hàng Rút tiền (MerchantProfilePage) (SCR-027) (2 STP)',
    member: 'loc'
  },

  // --- KHẢI (9 tasks - 32 STP - TRUNG BÌNH: Hàng Đợi Ảo, Trọng Tài Khiếu Nại, Kỷ Luật & SCR-046) ---
  {
    id: '6aec0ae4c015dee2c6cf8cc9',
    name: '[SP-S3-BKG-02] [BE+FE] Khắc phục đứt gãy luồng Hàng đợi ảo: Bổ sung trạng thái NOTIFIED và API Accept Slot (GAP-20) (5 STP)',
    member: 'khai'
  },
  {
    id: '6aeb0dadbb71853d80e2d9b0',
    name: '[SP-S3-GOV-01] [BE] Triển khai bộ API và Luồng Quản lý Khiếu nại cho Tài xế (Driver Dispute API - GAP-01) (5 STP)',
    member: 'khai'
  },
  {
    id: '6aeb0dbd8d9855b7f2cfb17b',
    name: '[SP-S3-GOV-02] [BE] Hoàn thiện API Cảnh báo Người dùng & Lịch sử Kỷ luật Tài khoản Admin (GAP-11) (3 STP)',
    member: 'khai'
  },
  {
    id: '6aeb0f7ec3e486772b73abb4',
    name: '[SP-S3-GOV-03] [FE] Hồ sơ Người dùng, Lịch sử Đỗ & Khóa tài khoản (AdminUserDetailPage) (SCR-046) (3 STP)',
    member: 'khai'
  },
  {
    id: '6aeb0f7879b01fca676fa830',
    name: '[SP-S3-GOV-04] [FE] Danh bạ Quản lý Tài khoản & Phân quyền Toàn sàn (AdminUsersPage) (SCR-045) (3 STP)',
    member: 'khai'
  },
  {
    id: '6aeb0f9041d30bcd02604e02',
    name: '[SP-S3-GOV-05] [FE] Bàn Trọng tài & Danh sách Tranh chấp Mở (AdminDisputesPage) (SCR-049) (3 STP)',
    member: 'khai'
  },
  {
    id: '6aeae1b2961d17df34ea9755',
    name: '[SP-S3-UI-03] [FE] Giao diện khởi tạo và theo dõi khiếu nại cho Tài xế (SCR-DRV-01) (5 STP)',
    member: 'khai'
  },
  {
    id: '6aeb0f51258c3f76952c34f0',
    name: '[SP-S3-GOV-06] [FE] Danh sách Khiếu nại từ Tài xế (MerchantDisputesPage) (SCR-039) (2 STP)',
    member: 'khai'
  },
  {
    id: '6aeb0f57c3e486772b73701c',
    name: '[SP-S3-GOV-07] [FE] Chi tiết Khiếu nại, Lời khai & Bằng chứng (MerchantDisputeDetailPage) (SCR-040) (3 STP)',
    member: 'khai'
  },

  // --- KHANG (13 tasks - 37 STP - DỄ / AN TOÀN: UI Cơ Bản, Component Stitch, Thông Báo, Tiện Ích Bãi Đỗ & Sửa Lỗi Nhẹ) ---
  {
    id: '6aeb0e6af81c012127035f0d',
    name: '[SP-S3-UI-04] [FE] Trang chủ Marketing & Live Hero Map (LandingPage) (SCR-001) (3 STP)',
    member: 'khang'
  },
  {
    id: '6aeafc32a05866d1f2b5e97a',
    name: '[SP-S3-UI-05] [FE] Xây dựng bộ UI Component dùng chung (Skeleton, Toast, EmptyState, Modal) theo chuẩn Stitch Design System (5 STP)',
    member: 'khang'
  },
  {
    id: '6aeb0efc0d35d62e6da9af95',
    name: '[SP-S3-UI-06] [FE] Trung tâm Thông báo Vận hành Tài xế (DriverNotificationsPage) (SCR-025) (2 STP)',
    member: 'khang'
  },
  {
    id: '6aeb0f5d33fd061e88f19572',
    name: '[SP-S3-UI-07] [FE] Trung tâm Thông báo Vận hành Bãi xe (MerchantNotificationsPage) (SCR-041) (2 STP)',
    member: 'khang'
  },
  {
    id: '6aeb0ef6e0fd652d8a6439c5',
    name: '[SP-S3-UI-08] [FE] Thông báo Thanh toán Bị hủy & Hướng dẫn Thử lại (PaymentCancelPage) (SCR-024) (2 STP)',
    member: 'khang'
  },
  {
    id: '6aec0ae2b584736e717c7d9e',
    name: '[SP-S3-UI-09] [FE] Sửa lỗi parse Response API danh sách đặt chỗ cá nhân MyBookings (GAP-18) (2 STP)',
    member: 'khang'
  },
  {
    id: '6aeb0f0fe79ce69670c43ac3',
    name: '[SP-S3-UI-10] [FE] Danh sách & Lưới Trạng thái Bãi đỗ xe (MerchantParkingLotsPage) (SCR-028) (3 STP)',
    member: 'khang'
  },
  {
    id: '6aeb0f1ad9ead4ce22829aca',
    name: '[SP-S3-UI-11] [FE] Chi tiết Vận hành & Doanh thu Bãi đỗ (MerchantParkingLotDetailPage) (SCR-030) (3 STP)',
    member: 'khang'
  },
  {
    id: '6aeb0f20c156365db84b421b',
    name: '[SP-S3-UI-12] [FE] Chỉnh sửa Thông tin & Tiện ích Bãi đỗ (EditMerchantParkingLotPage) (SCR-031) (3 STP)',
    member: 'khang'
  },
  {
    id: '6aeb0f32fdee4b86f9e1f67a',
    name: '[SP-S3-UI-13] [FE] Quản lý Lịch sử & Đơn Đặt chỗ Khách hàng (MerchantBookingsPage) (SCR-034) (3 STP)',
    member: 'khang'
  },
  {
    id: '6aeb0f383bda5951e9a49330',
    name: '[SP-S3-UI-14] [FE] Chi tiết Đơn Đặt chỗ & Nhật ký Barie (MerchantBookingDetailPage) (SCR-035) (3 STP)',
    member: 'khang'
  },
  {
    id: '6aec0ae5e05668d30f6c6996',
    name: '[SP-S3-DAT-02] [BE+FE] Đồng bộ hóa danh mục loại phương tiện TRAILER giữa FE và Backend (GAP-21) (3 STP)',
    member: 'khang'
  },
  {
    id: '6aec368f5ca048394a3bdb60',
    name: '[SP-S3-UI-15] [FE] Chuẩn hóa cơ chế xử lý lỗi API và cờ cấu hình Mock Data môi trường (3 STP)',
    member: 'khang'
  }
];

async function updateAllCards() {
  console.log(`Bắt đầu chuẩn hóa & phân công ${cardsToUpdate.length} thẻ trên bảng Smart Parking Hub - Delivery...`);

  for (let i = 0; i < cardsToUpdate.length; i++) {
    const item = cardsToUpdate[i];
    const url = `https://api.trello.com/1/cards/${item.id}?key=${key}&token=${token}`;

    const updateBody = {
      name: item.name
    };

    if (item.member && MEMBERS[item.member]) {
      updateBody.idMembers = [MEMBERS[item.member]];
    }

    try {
      const res = await fetch(url, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(updateBody)
      });

      if (res.ok) {
        console.log(`[${i + 1}/${cardsToUpdate.length}] ✅ Đã cập nhật: ${item.name.slice(0, 50)}...`);
      } else {
        const err = await res.text();
        console.error(`[${i + 1}/${cardsToUpdate.length}] ❌ Lỗi thẻ ${item.id}:`, err);
      }
    } catch (e) {
      console.error(`Lỗi mạng khi cập nhật thẻ ${item.id}:`, e.message);
    }

    await new Promise(r => setTimeout(r, 120));
  }

  console.log('\n🎉 ĐÃ CẬP NHẬT HOÀN TẤT TOÀN BỘ SPRINT 03 TRÊN SMART PARKING HUB - DELIVERY!');
}

updateAllCards();
