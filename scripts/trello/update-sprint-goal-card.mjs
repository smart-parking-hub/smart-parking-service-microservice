import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const envPath = path.resolve(__dirname, '..', '..', '.trello.env');
const envContent = fs.readFileSync(envPath, 'utf8');
const key = envContent.match(/TRELLO_API_KEY=(.+)/)?.[1]?.trim();
const token = envContent.match(/TRELLO_API_TOKEN=(.+)/)?.[1]?.trim();

const GOAL_CARD_ID = '6af9c711c03fc42b36b3f865';

const newName = '[SPRINT-GOAL] Sprint 04: Bắt Lỗi Toàn Diện, E2E Testing, Bàn Trọng Tài & Hoàn Thiện Nghiệp Vụ (53 Tasks | 156 STP)';

const newDesc = `## 🎯 MỤC TIÊU CHIẾN LƯỢC SPRINT 04 (15/11/2026 - 29/11/2026)
- **Tổng dung lượng:** 53 Actionable Tasks | 156 Story Points (STP)
- **Cơ cấu nguồn việc:**
  + **29 Tasks (100 STP):** Chuyển tiếp & Hoàn thiện tính năng sâu từ Sprint 03 (Sổ cái kép, Bàn trọng tài, Firebase 2D, Hàng đợi ảo, Pricing matrix, Webhooks DevPortal...).
  + **24 Tasks (56 STP):** Bắt lỗi & Cải tiến toàn diện theo Biên bản rà soát 15/11 (Kiểm tra trùng CCCD, SĐT OTP, Chặn đặt chồng slot, Chặn sinh QR trước cọc, Khắc phục tính trùng cước checkout, Tách bước duyệt rút tiền...).

---

### 🚀 5 TRỌNG TÂM CHIẾN LƯỢC CỦA SPRINT 04:
1. **Financial Integrity & Double-Entry Ledger (14 Tasks | 54 STP):**
   - Hoàn thiện Sổ cái kép & Quyết toán doanh thu chủ bãi (SP-S3-L01 - 8 STP), Sổ cái dòng tiền toàn hệ thống (SP-S3-PAY-05) và Sổ cái ví (SP-S3-PAY-03).
   - Tự động hóa Đối soát SAGA Auto-Refund hoàn tiền tranh chấp (SP-S3-SAGA-01) và Phán quyết trọng tài (SP-S3-PAY-07).
   - Minh bạch phí sàn trên cọc (SP-PAY-01), khắc phục tính trùng cước giờ ranh giới (SP-CHECKOUT-01), bộ đếm thời gian thực (SP-CHECKOUT-02), hoàn cọc thừa (SP-CHECKOUT-03), chi tiết phạt quá giờ (SP-CHECKOUT-04) và tách bước duyệt/chi tiền rút (SP-WITHDRAW-01).

2. **Parking Operations & AI Vision (9 Tasks | 30 STP):**
   - Tích hợp Firebase lưu trữ sơ đồ bãi đỗ 2D (SP-S3-L04 - 8 STP) và Thiết lập biểu phí ma trận 4 thông số (SP-S3-PRC-01 - 5 STP).
   - Khôi phục tính năng Gemini AI sinh mô tả bãi xe & nội quy PCCC (SP-LOT-01 - 3 STP).
   - Tối ưu hiệu năng bản đồ (SP-MAP-01, SP-MAP-02), giới hạn tải ảnh bãi đỗ (SP-MEDIA-01) và API đóng/mở bãi real-time (SP-S3-RT-01).

3. **Identity, Auth & Vehicle Safety (10 Tasks | 24 STP):**
   - Bịt lỗi nghiêm trọng SQL 500 khi trùng CCCD/CMND (SP-AUTH-03) và chuẩn hóa kiểm tra SĐT ngay từ bước gửi OTP (SP-AUTH-04).
   - Chuẩn hóa định dạng số điện thoại (SP-AUTH-01), cơ chế Remember Me (SP-AUTH-02) và reset form dữ liệu tạm (SP-AUTH-05).
   - Chặn xóa/ngừng sử dụng phương tiện khi đang đỗ hoặc nợ tiền (SP-VEH-02) và validate dải tải trọng theo loại xe (SP-VEH-01).

4. **Booking, Smart Gate & Resilient Queue (5 Tasks | 15 STP):**
   - Chặn xe đang trong bãi tiếp tục đặt slot mới gây xung đột (SP-BOOK-01 - 3 STP) và chặn tạo/hiển thị QR Barie khi chưa thanh toán cọc (SP-TICKET-01 - 2 STP).
   - Khắc phục đứt gãy luồng Hàng đợi ảo: Bổ sung trạng thái NOTIFIED và API Accept Slot (SP-S3-BKG-02 - 5 STP).
   - Tích hợp quét mã QR Barie camera qua html5-qrcode trên mobile HTTPS (SP-S3-INT-04 - 3 STP).

5. **Governance, Public Experience & E2E Testing (15 Tasks | 33 STP):**
   - Xây dựng Bàn Trọng tài & Danh sách Tranh chấp Mở, Lời khai & Bằng chứng (SP-S3-GOV-05, 06, 07 - 8 STP).
   - Bộ trang phụ trợ: Tuyển dụng, Liên hệ 24/7, Tài liệu tích hợp DevPortal, Error 403 UX, Marketing Hero Map (SP-S3-UI-04..20).
   - Chuẩn hóa cờ cấu hình Mock Data môi trường (SP-S3-UI-15) và hoàn thiện kiểm thử E2E Zero-Mock.

---

### 👥 ĐỀ XUẤT PHÂN BỔ TẢI CÔNG VIỆC (TEAM CAPACITY):
- **Huỳnh La Tiến Lộc (Core Payment & Settlement):** 10 tasks đã gán (46 STP) + Đề xuất nhận tiếp nhóm Defect Thanh toán/Sổ cái (SP-PAY-01, SP-CHECKOUT-01..03) ~ 57 STP.
- **Hồ Quốc Khải (Gate, Queue & Operations):** 7 tasks đã gán (20 STP) + Đề xuất nhận tiếp nhóm Defect Booking/Vehicle (SP-BOOK-01, SP-TICKET-01, SP-VEH-01..02) ~ 31 STP.
- **Lâm Gia Huy (Pricing, Map & Driver UI/UX):** 6 tasks đã gán (19 STP) + Đề xuất nhận tiếp nhóm Defect Map/UI (SP-MAP-01..02, SP-UI-01..03, SP-MEDIA-01) ~ 28 STP.
- **Hoàng Đình Tùng (Governance, AI & DevOps):** 6 tasks đã gán (15 STP) + Đề xuất nhận tiếp nhóm Defect Auth/AI/Withdrawal (SP-AUTH-01..05, SP-LOT-01, SP-WITHDRAW-01) ~ 30 STP.
*(Lưu ý: 24 thẻ Defect mới tạo sẽ được chốt Assignee chính thức trong buổi họp Sprint Planning)*.`;

async function updateGoalCard() {
  console.log(`Đang cập nhật thẻ SPRINT-GOAL: ${GOAL_CARD_ID}...`);
  const url = `https://api.trello.com/1/cards/${GOAL_CARD_ID}?key=${key}&token=${token}`;
  
  const res = await fetch(url, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      name: newName,
      desc: newDesc
    })
  });

  if (res.ok) {
    const data = await res.json();
    console.log(`✅ Cập nhật thành công thẻ Goal: ${data.name}`);
    console.log(`🔗 Link: ${data.shortUrl}`);
  } else {
    const err = await res.text();
    console.error(`❌ Lỗi cập nhật:`, res.status, err);
  }
}

updateGoalCard();
