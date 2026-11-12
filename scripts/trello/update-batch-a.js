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

const batchACards = [
  // 06. To Do (8 cards)
  { id: '6aeb0e37edffa306ef88ff11', name: '[SP-S3-OPS-02] [ARCH] Dọn dẹp nợ kỹ thuật: Xóa bỏ Ant Design Sider chết và trang mồ côi (GAP-08, GAP-16) (2 STP)' },
  { id: '6aeb0dcbfd4829d28a5fbc8c', name: '[SP-S3-SAGA-01] [BE] Tự động hóa Đối soát và Xử lý Hoàn tiền Tranh chấp (Auto-Refund Settlement - GAP-13) (5 STP)' },
  { id: '6aeb0ef091047129466afe98', name: '[SP-S3-PAY-09] [FE] Kết quả Thanh toán PayOS Thành công (PaymentSuccessPage) (SCR-023) (3 STP)' },
  { id: '6aeb0fa18fbf9f87906f1768', name: '[SP-S3-UI-16] [FE] Trung tâm Phát sóng & Cảnh báo Điều hành (AdminNotificationsPage) (SCR-052) (2 STP)' },
  { id: '6aeb0e81796cb8e86163ed8a', name: '[SP-S3-UI-17] [FE] Tài liệu Tích hợp API & Webhooks (ApiIntegrationPage) (SCR-005) (2 STP)' },
  { id: '6aeb0e8ef14ba0aee24fe3cc', name: '[SP-S3-UI-18] [FE] Cơ hội Nghề nghiệp & Tuyển dụng (CareersPage) (SCR-007) (2 STP)' },
  { id: '6aeb0e9b9b05af63d8b7df57', name: '[SP-S3-UI-19] [FE] Liên hệ Hợp tác & Hỗ trợ Kỹ thuật 24/7 (ContactPage) (SCR-009) (3 STP)' },
  { id: '6aeb0eb378ada3de72fcb490', name: '[SP-S3-UI-20] [FE] Thông báo Lỗi Truy cập 403 Forbidden (ForbiddenPage) (SCR-013) (1 STP)' },

  // 07. In Progress (3 cards)
  { id: '6aeb0e7b5444efd54d87a05f', name: '[SP-S3-UI-21] [FE] Bảng giá Dịch vụ & Gói Bãi xe (PricingPage) (SCR-004) (2 STP)' },
  { id: '6aeae1b59847688c91687384', name: '[SP-S3-AI-01] [FE] Tích hợp widget trợ lý ảo AI Copilot tra cứu bãi đỗ (SCR-MOD-01) (5 STP)' },
  { id: '6aeb0ed8cf41eb6b7287a249', name: '[SP-S3-PAY-10] [FE] Sổ cái Lịch sử Thanh toán & Đối soát Cọc (DriverPaymentsPage) (SCR-019) (2 STP)' },

  // 08. Code Review (2 cards)
  { id: '6aeb0e3dd2a2c3c19ba8e889', name: '[SP-S3-OPS-03] [CI/CD] Xây dựng GitHub Actions Pipeline kiểm soát chất lượng tự động Zero-Error trước khi merge PR (3 STP)' },
  { id: '6aeb0e2ac8951ab59827a1c0', name: '[SP-S3-OPS-04] [DEV-OPS] Cấu hình Docker Compose đa môi trường và Nginx Reverse Proxy tối ưu bộ đệm Frontend v2 (3 STP)' },

  // 11. Blocked (1 card)
  { id: '6aeae1a4b87ce1894eaa842f', name: '[SP-S3-INT-04] [FE] Tích hợp thư viện quét mã Barie camera html5-qrcode trên Mobile HTTPS (3 STP)' },

  // Future Enhancements (2 cards)
  { id: '6aeb0dd15bd1e31f85aa5a69', name: '[SP-S3-PAY-11] [BE] Tối ưu hóa API Thống kê Doanh thu Độc lập Từng Bãi Đỗ Xe (GAP-12) (3 STP)' },
  { id: '6aeb0db6432d6bb22552d4cf', name: '[SP-S3-PAY-12] [BE] Chuẩn hóa Hợp đồng API Barie Thu Tiền Mặt confirm-cash & Khắc phục Lệch DTO (GAP-02) (3 STP)' }
];

async function updateBatchA() {
  console.log(`Bắt đầu chuẩn hóa BATCH A (${batchACards.length} thẻ trong To Do, In Progress, Review, Blocked, Future)...`);
  for (let i = 0; i < batchACards.length; i++) {
    const card = batchACards[i];
    const url = `https://api.trello.com/1/cards/${card.id}?key=${key}&token=${token}`;
    try {
      const res = await fetch(url, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: card.name })
      });
      if (res.ok) {
        console.log(`[${i + 1}/${batchACards.length}] ✅ Đã cập nhật: ${card.name}`);
      } else {
        console.error(`[${i + 1}/${batchACards.length}] ❌ Lỗi thẻ ${card.id}:`, await res.text());
      }
    } catch (e) {
      console.error(`Lỗi mạng:`, e.message);
    }
    await new Promise(r => setTimeout(r, 120));
  }
  console.log('\n🎉 HOÀN TẤT BATCH A!');
}

updateBatchA();
