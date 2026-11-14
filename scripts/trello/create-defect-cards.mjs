import { key, token, boardId, MEMBERS } from './trello-client.mjs';

const NEW_CARDS = [
  {
    name: '[SP-DEF-UI-01] Chuẩn hóa Breadcrumbs & Nút Back Navigation Thống Nhất Trên 38 Trang Chi Tiết (3 STP)',
    listId: '6aeadb6464596a21a7015039', // Project Improvements
    memberId: MEMBERS.KHAI,
    desc: `### 📌 Vấn đề UI/UX (ISSUE-NAV-04 & NAV-05)
Hiện tại 38 trang chi tiết cấp 2 và cấp 3 của Merchant và Driver Portal thiếu thanh Breadcrumbs và nút Back Navigation thống nhất, khiến người dùng trên Mobile bị kẹt màn hình hoặc phải lùi bằng nút trình duyệt làm mất state form.

### 🎯 Giải pháp kỹ thuật:
- Tích hợp component \`Breadcrumbs.tsx\` và \`BackButton.tsx\` vào Page Header của 38 trang chi tiết.
- Cấu hình fallback route an toàn (tránh back về trang login hoặc vòng lặp vô tận).

### ✅ Tiêu chí nghiệm thu (DoD):
1. 100% trang chi tiết (Booking Detail, Lot Edit, Dispute Detail...) có nút Back và Breadcrumb.
2. Bấm Back lùi đúng 1 cấp thư mục cha hoặc trang danh sách tương ứng.
3. Hỗ trợ phím Alt+Left Arrow và vuốt cạnh màn hình trên mobile.`
  },
  {
    name: '[SP-DEF-UI-02] Đăng Ký Merchant Domain Color Palette Vào @theme Tailwind CSS v4 (2 STP)',
    listId: '6aeadb6464596a21a7015039',
    memberId: MEMBERS.TUNG,
    desc: `### 📌 Vấn đề UI/UX (ISSUE-TOK-02)
Hơn 200 vị trí trong các component Merchant (\`src/features/merchant/*\`) đang sử dụng mã hex thô hardcoded: \`#0d1c2e\` (xanh đen navy), \`#455f87\` (slate blue), \`#eff4ff\` (light blue tint), \`#131b2e\` (surface dark) chưa được khai báo vào token hệ thống.

### 🎯 Giải pháp kỹ thuật:
- Đăng ký các biến màu ngữ nghĩa vào \`@theme\` trong \`src/app/styles/theme.css\`:
  - \`--color-merchant-navy: #0d1c2e\`
  - \`--color-merchant-slate: #455f87\`
  - \`--color-merchant-tint: #eff4ff\`
  - \`--color-merchant-surface: #131b2e\`
- Thay thế toàn bộ mã hex thô bằng class Tailwind token chuẩn.

### ✅ Tiêu chí nghiệm thu (DoD):
1. Không còn mã hex thô trôi nổi trong các component Merchant.
2. Hỗ trợ chuyển đổi giao diện sáng/tối đồng bộ.`
  },
  {
    name: '[SP-DEF-A11Y-01] Khắc Phục Lỗi Trợ Năng WCAG 2.1: Bổ Sung aria-label Cho Icon Buttons & Focus-Visible Ring (3 STP)',
    listId: '6aeadb6464596a21a7015039',
    memberId: MEMBERS.HUY,
    desc: `### 📌 Vấn đề Trợ Năng (ISSUE-A11Y-01 & A11Y-02)
- Hơn 40 nút Icon-only (nút đóng [X] modal, menu ba chấm, nút chuyển view bản đồ) không có thuộc tính \`aria-label\`, khiến Screen Reader chỉ đọc 'Button' vô nghĩa.
- Một số form input bị đặt class \`outline-none\` mà không bù \`focus-visible:ring-2\`, gây mất dấu con trỏ khi duyệt bàn phím bằng phím Tab.

### 🎯 Giải pháp kỹ thuật:
- Thêm \`aria-label\` có nghĩa mô tả hành động (ví dụ: 'Đóng hộp thoại', 'Tùy chọn tác vụ').
- Áp dụng token \`focus-visible:ring-2 focus-visible:ring-brand-primary/50 focus-visible:outline-none\` cho toàn bộ interactive elements.

### ✅ Tiêu chí nghiệm thu (DoD):
1. Chạy Lighthouse Accessibility đạt điểm từ 95/100 trở lên.
2. Kiểm tra bằng bàn phím phím Tab thấy rõ viền sáng cam trên mọi nút bấm.`
  },
  {
    name: '[SP-DEF-STA-01] Đồng Bộ Delta Countdown Giữ Chỗ 10 Phút Với expiresAt Server Sau F5 (2 STP)',
    listId: '6aeadb6464596a21a7015039',
    memberId: MEMBERS.HUY,
    desc: `### 📌 Vấn đề Trải Nghiệm (ISSUE-STA-02)
Bộ đếm ngược 10 phút giữ chỗ trên giao diện \`BookingHoldingTimer.tsx\` hiện đang dựa một phần vào state cục bộ của React. Khi người dùng bấm F5 / Reload trang, đồng hồ có thể bị nhảy hoặc lệch so với thời gian khóa chỗ thực tế tại Redis / MySQL.

### 🎯 Giải pháp kỹ thuật:
- Lấy chính xác trường \`expiresAt\` từ response của \`HoldSlotResponse\` hoặc Booking API.
- Tính toán \`remainingSeconds = Math.max(0, Math.floor((expiresAt.getTime() - Date.now()) / 1000))\`.
- Tự động hiển thị modal hết hạn và thu hồi slot khi đồng hồ chạm 0.

### ✅ Tiêu chí nghiệm thu (DoD):
1. F5 hoặc đóng/mở lại tab trình duyệt thì số giây còn lại vẫn khớp tuyệt đối với server.
2. Không còn hiện tượng đếm ngược lại từ 10:00 khi reload.`
  },
  {
    name: '[SP-DEF-BE-01] Ràng Buộc Nghiệp Vụ: Chặn Phê Duyệt Bãi Xe Có Slot Nhưng Thiếu Bảng Giá PricingRule (3 STP)',
    listId: '6ae95a51c9db9736f84f051b', // Product Backlog
    memberId: MEMBERS.TUNG,
    desc: `### 📌 Lỗ Hổng Nghiệp Vụ (BUG-01)
Hiện tại chủ bãi có thể tạo slot cho loại xe (ví dụ: CONTAINER, HEAVY_TRUCK) nhưng không bắt buộc tạo biểu phí PricingRule. Khi Admin bấm duyệt, bãi xe hiển thị giá 0đ hoặc backend tự fallback giá ngầm 20.000đ gây tranh chấp với tài xế.

### 🎯 Giải pháp kỹ thuật:
- Trong \`ParkingLotServiceImpl.approveParkingLot()\`, bổ sung kiểm tra:
  Mọi loại xe có \`totalCapacity > 0\` trong bảng \`parking_slots\` bắt buộc phải tồn tại bản ghi trong \`pricing_rules\` với \`base_price_per_hour > 0\`.
- Nếu thiếu, ném ngoại lệ \`AppException(ErrorCode.BAD_REQUEST, 'Bãi xe chưa thiết lập biểu phí cho loại xe...')\`.

### ✅ Tiêu chí nghiệm thu (DoD):
1. Admin không thể bấm duyệt bãi xe nếu có loại xe chưa được cấu hình bảng giá.
2. Trả về thông báo lỗi chi tiết nêu rõ loại xe còn thiếu giá.`
  },
  {
    name: '[SP-DEF-PAY-01] Xây Dựng Endpoint Tra Cứu Số Dư Ví Nội Bộ Tài Xế GET /api/v1/wallets/me (5 STP)',
    listId: '6ae95a51c9db9736f84f051b',
    memberId: MEMBERS.LOC,
    desc: `### 📌 Khoảng Trống API (GAP-A-01 / PROPOSED-API-01)
Màn hình Ví tài xế (\`/driver/wallet\`) hiện tại hiển thị dữ liệu tĩnh hoặc suy luận từ lịch sử giao dịch do Backend \`payment-service\` chưa cung cấp endpoint quản lý tài khoản ví cho tài xế.

### 🎯 Giải pháp kỹ thuật:
- Tạo entity \`DriverWallet\` với các trường: \`driverId\`, \`balance\`, \`availableBalance\`, \`holdBalance\`, \`updatedAt\`.
- Mở endpoint \`GET /api/v1/wallets/me\` trong \`payment-service\` trả về số dư khả dụng và số tiền cọc đang bị tạm giữ (hold).
- Kết nối hooks \`useDriverWalletQuery\` tại Frontend.

### ✅ Tiêu chí nghiệm thu (DoD):
1. Tài xế đăng nhập xem được số dư thực tế được bảo chứng bởi sổ cái kép.
2. Tự động cập nhật số dư sau khi thanh toán cọc hoặc nhận tiền hoàn tranh chấp.`
  },
  {
    name: '[SP-DEF-PAY-02] Xây Dựng Cơ Chế Xuất Hóa Đơn VAT Hàng Loạt Dạng File Nén ZIP POST /api/v1/merchant/invoices/bulk-export (3 STP)',
    listId: '6ae95a51c9db9736f84f051b',
    memberId: MEMBERS.LOC,
    desc: `### 📌 Khoảng Trống API (GAP-A-02 / PROPOSED-API-02)
Chủ bãi xe (Merchant) cuối tháng cần xuất toàn bộ hóa đơn VAT điện tử của các đơn đỗ xe trong tháng ra file nén ZIP để nộp kế toán và đối soát thuế, hiện tại hệ thống chỉ cho phép tải lẻ từng đơn.

### 🎯 Giải pháp kỹ thuật:
- Tạo endpoint \`POST /api/v1/merchant/invoices/bulk-export\` trong \`payment-service\`.
- Nhận tham số \`{ month, year, format: 'PDF' | 'XML' }\`, dùng luồng \`ZipOutputStream\` gom các hóa đơn và trả về stream nhị phân \`application/zip\`.
- Frontend hiển thị nút 'Xuất toàn bộ hóa đơn (.ZIP)' kèm thanh loading.

### ✅ Tiêu chí nghiệm thu (DoD):
1. Tải về file ZIP chứa đầy đủ các hóa đơn trong tháng đã chọn.
2. Dung lượng nén tối ưu, không tràn bộ nhớ heap trên server khi xuất trên 500 hóa đơn.`
  },
  {
    name: '[SP-DEF-DTO-01] Mở Rộng DTO Bổ Sung Cờ hasEvCharger Và gateName Cho Chi Tiết Vé Đỗ Xe (2 STP)',
    listId: '6ae95a51c9db9736f84f051b',
    memberId: MEMBERS.KHAI,
    desc: `### 📌 Khoảng Trống DTO (GAP-D-01 & D-02)
- Khi tìm kiếm slot xe điện hoặc xe tải hybrid, tài xế không biết slot nào có hỗ trợ trụ sạc nhanh EV do DTO \`SlotResponse\` thiếu trường \`hasEvCharger\`.
- Chi tiết vé đỗ xe điện tử chưa hiển thị tên cổng Barie check-in tương ứng (\`gateName\`) làm tài xế lúng túng khi đến bãi có nhiều cổng vào.

### 🎯 Giải pháp kỹ thuật:
- Bổ sung trường \`Boolean hasEvCharger\` vào \`SlotResponse.java\` và \`Slot.java\` tại \`parking-lot-service\`.
- Bổ sung trường \`String gateName\` vào \`BookingDetailResponse.java\` tại \`booking-service\`.
- Cập nhật giao diện vé đỗ xe hiển thị biểu tượng sạc EV và tên cổng Barie hướng dẫn.

### ✅ Tiêu chí nghiệm thu (DoD):
1. DTO trả về đầy đủ \`hasEvCharger\` và \`gateName\`.
2. Frontend hiển thị badge trụ sạc và chỉ dẫn cổng vào rõ ràng.`
  }
];

async function run() {
  console.log(`Creating ${NEW_CARDS.length} detailed defect & UX cards on Trello...`);
  for (const card of NEW_CARDS) {
    try {
      const url = `https://api.trello.com/1/cards?idList=${card.listId}&name=${encodeURIComponent(card.name)}&desc=${encodeURIComponent(card.desc)}&idMembers=${card.memberId}&key=${key}&token=${token}`;
      const res = await fetch(url, { method: 'POST' });
      const created = await res.json();
      console.log(`[CREATED] Card: ${created.name} (id: ${created.id}) in list ${card.listId}`);
    } catch (err) {
      console.error(`[ERROR] Failed to create card ${card.name}:`, err);
    }
  }
  console.log('All defect & UI/UX cards successfully created!');
}

run();
