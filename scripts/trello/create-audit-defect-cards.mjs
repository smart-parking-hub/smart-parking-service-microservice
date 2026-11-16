/**
 * Script tự động tạo 24 Thẻ Trello Rà Soát Lỗi & Cải Tiến (Biên Bản Họp 15/11/2026)
 * Danh sách đích: "Sprint 04 - Backlog" (id: 6af80755e8b450a479eac591)
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Đọc credentials từ .trello.env
function getEnvConfig() {
  const envPath = path.resolve(__dirname, '..', '..', '.trello.env');
  const content = fs.readFileSync(envPath, 'utf8');
  const key = content.match(/TRELLO_API_KEY=(.+)/)?.[1]?.trim();
  const token = content.match(/TRELLO_API_TOKEN=(.+)/)?.[1]?.trim();
  const boardId = content.match(/TRELLO_ACTIVE_BOARD_ID=(.+)/)?.[1]?.trim();
  return { key, token, boardId };
}

const { key, token, boardId } = getEnvConfig();
const TARGET_LIST_ID = '6af80755e8b450a479eac591'; // Sprint 04 - Backlog

// Bản đồ nhãn ID trên Board
const LABELS = {
  FE: '6aeadb6ef14cc3d7e8aefd13',
  BE: '6aeadb6f3b6a671626024a5a',
  BUG: '6aeadb75d3558d93821f76d9',
  IMPROVEMENT: '6aeadb752e2cd1cf9389ffef',
  SECURITY: '6aeadb72858eca14a79c0260',
  P1_HIGH: '6aeadb7902d6f618b2264ffe',
  P2_MEDIUM: '6aeadb7a8c6b564dd6c08169',
  P3_LOW: '6aeadb7b6948c7c5f890c4cc',
  USER_MGMT: '6aeadb80ef45b317a8e8fef8',
  UI_UX: '6aead13a38deffc4cbaf26e6',
  PARKING: '6aeadb7c02993de2ad5340e3',
  NOTIFICATION: '6aeadb80c574da3e69e4fd99',
  AI: '6aeadb815eeddc634d7a94dc',
  VEHICLE: '6aeadb7d9a2fac4538dfd624',
  BOOKING: '6aeadb7d54bb8b1366ae8092',
  PAYMENT: '6aeadb7ee39c45a014b93a1d',
};

const CARDS_DATA = [
  {
    code: 'AUTH-01',
    name: '[SP-AUTH-01] Chuẩn hóa và validate số điện thoại đăng ký (khoảng trắng, đầu số +84/0) (2 STP)',
    labels: [LABELS.BE, LABELS.FE, LABELS.USER_MGMT, LABELS.BUG, LABELS.P2_MEDIUM],
    desc: `### 📌 Vấn Đề Kỹ Thuật (AUTH-01)
- Backend: \`RegisterRequest.java#L15\` dùng regex \`^(0|\\+84)[35789][0-9]{8}$\`. Setter \`setPhone\` chỉ thay \`+84\` thành \`0\` nhưng không loại bỏ khoảng trắng hoặc ký tự đặc biệt (\`0912 345 678\`).
- Frontend: \`RegisterPage.tsx#L356, L570\` áp validate regex trên chuỗi thô của input, người dùng copy-paste số có khoảng trắng từ danh bạ bị chặn báo lỗi "Số điện thoại không hợp lệ".

### 🎯 Giải Pháp Đề Xuất
1. Frontend: Tự động loại bỏ khoảng trắng (\`replace(/\\s/g, '')\`) ngay khi blur hoặc paste số điện thoại.
2. Backend: Chuẩn hóa chuỗi số điện thoại về dạng \`0xxxxxxxxx\` trước khi chạy Hibernate Validation và kiểm tra DB.
3. Hỗ trợ đầy đủ các đầu số di động Việt Nam (10 chữ số).

### ✅ Tiêu Chí Nghiệm Thu (Acceptance Criteria)
- [ ] Nhập \`0912 345 678\` hoặc \`+84 912 345 678\` hệ thống tự động làm sạch thành \`0912345678\` và gửi OTP thành công.
- [ ] Báo lỗi rõ ràng nếu thiếu số hoặc nhập sai quy chuẩn nhà mạng viễn thông.`
  },
  {
    code: 'AUTH-02',
    name: '[SP-AUTH-02] Sửa cơ chế "Nhớ tôi" (Remember Me) phân tách giữa lưu session và nhớ số điện thoại (2 STP)',
    labels: [LABELS.FE, LABELS.USER_MGMT, LABELS.IMPROVEMENT, LABELS.P2_MEDIUM],
    desc: `### 📌 Vấn Đề Kỹ Thuật (AUTH-02)
- Frontend: \`LoginForm.tsx#L95\` nhãn checkbox ghi "Ghi nhớ số điện thoại", chỉ lưu \`fields.phone\` vào \`localStorage\`.
- \`session.ts#L25-L28\`: Token phiên làm việc luôn luôn lưu cứng trong \`localStorage.setItem('access_token', token)\` bất kể người dùng có tick chọn "Ghi nhớ" hay không.
- Tắt trình duyệt mở lại người dùng vẫn ở trạng thái đăng nhập, gây nhầm lẫn giữa "Nhớ số điện thoại" và "Duy trì phiên đăng nhập".

### 🎯 Giải Pháp Đề Xuất
1. Đổi nhãn thành "Duy trì đăng nhập (Nhớ tôi)".
2. Nếu không tick chọn: Lưu Access Token trong \`sessionStorage\` (tự đăng xuất khi tắt trình duyệt).
3. Nếu tick chọn: Lưu trong \`localStorage\` và ghi nhớ số điện thoại.
4. Đăng xuất: Xóa token nhưng giữ lại số điện thoại đã nhớ.

### ✅ Tiêu Chí Nghiệm Thu (Acceptance Criteria)
- [ ] Bỏ chọn "Nhớ tôi" -> Đóng trình duyệt mở lại -> Bắt buộc đăng nhập lại.
- [ ] Chọn "Nhớ tôi" -> Đóng trình duyệt mở lại -> Vẫn duy trì phiên làm việc.
- [ ] Tuyệt đối không lưu mật khẩu dạng văn bản thuần.`
  },
  {
    code: 'AUTH-03',
    name: '[SP-AUTH-03] Bổ sung kiểm tra trùng CCCD/CMND trước khi lưu chủ bãi xe (tránh lỗi SQL 500) (2 STP)',
    labels: [LABELS.BE, LABELS.FE, LABELS.USER_MGMT, LABELS.BUG, LABELS.P1_HIGH],
    desc: `### 📌 Vấn Đề Kỹ Thuật (AUTH-03)
- Database: Cột \`citizen_id\` trong bảng \`merchants\` có ràng buộc \`unique = true\` (\`Merchant.java#L25\`).
- Backend: \`AuthServiceImpl.java#L208-L223\` kiểm tra rỗng nhưng **không hề gọi \`existsByCitizenId\`** trước khi lưu.
- Hậu quả: Khi đăng ký trùng CCCD, lệnh \`merchantRepository.save()\` ném lỗi \`DataIntegrityViolationException\`, trả về HTTP 500 thô làm người dùng hoang mang.

### 🎯 Giải Pháp Đề Xuất
1. Khai báo \`boolean existsByCitizenId(String citizenId);\` trong \`MerchantRepository\`.
2. Kiểm tra trước trong \`AuthServiceImpl.register()\`: nếu trùng, ném \`AppException(ErrorCode.CONFLICT, "CCCD/CMND đã tồn tại trong hệ thống")\`.
3. Frontend bắt lỗi 409 và focus con trỏ vào ô nhập CCCD.

### ✅ Tiêu Chí Nghiệm Thu (Acceptance Criteria)
- [ ] Đăng ký với CCCD đã tồn tại trả về lỗi HTTP 409 Conflict với thông báo rõ ràng, không xuất hiện lỗi 500.
- [ ] Frontend hiển thị thông báo lỗi ngay dưới ô nhập CCCD.`
  },
  {
    code: 'AUTH-04',
    name: '[SP-AUTH-04] Chuẩn hóa kiểm tra trùng số điện thoại ngay từ bước gửi OTP (2 STP)',
    labels: [LABELS.BE, LABELS.FE, LABELS.USER_MGMT, LABELS.BUG, LABELS.P1_HIGH],
    desc: `### 📌 Vấn Đề Kỹ Thuật (AUTH-04)
- Backend: \`SendOtpRequest.java#L12\` trường \`phone\` không có \`@NotBlank\`.
- Luồng đăng ký: Bước 1 gửi OTP qua email, bước 2 mới submit số điện thoại. Người dùng nhập số điện thoại bị trùng vẫn nhận được OTP email; hoàn tất nhập OTP bấm Đăng ký mới bị báo lỗi số điện thoại đã tồn tại.

### 🎯 Giải Pháp Đề Xuất
1. Bắt buộc kiểm tra tính duy nhất của số điện thoại ngay từ bước gửi OTP ban đầu.
2. Chuẩn hóa chuỗi số điện thoại trước khi so khớp cơ sở dữ liệu.
3. Thông báo rõ: "Số điện thoại đã liên kết với tài khoản khác. Vui lòng đăng nhập hoặc dùng số khác."

### ✅ Tiêu Chí Nghiệm Thu (Acceptance Criteria)
- [ ] Nhập số điện thoại đã tồn tại ở bước 1 -> Bị chặn ngay lập tức, không gửi mã OTP lãng phí.
- [ ] Chuẩn hóa \`+84901234567\` và \`0901234567\` được nhận diện là một số duy nhất.`
  },
  {
    code: 'AUTH-05',
    name: '[SP-AUTH-05] Xóa dữ liệu tạm form và lỗi validate sau đăng ký thành công hoặc khi đổi vai trò (2 STP)',
    labels: [LABELS.FE, LABELS.USER_MGMT, LABELS.BUG, LABELS.P2_MEDIUM],
    desc: `### 📌 Vấn Đề Kỹ Thuật (AUTH-05)
- Frontend: \`RegisterPage.tsx#L534-L538\`: Khi người dùng đổi vai trò \`DRIVER <-> MERCHANT\`, code chỉ đổi giá trị \`role\` mà không reset các trường đặc thù của Merchant (\`citizenId\`, \`bankName\`, \`bankAccountNum\`).
- Khi submit thành công, \`RegisterPage.tsx#L254-L263\` chỉ xóa \`sessionStorage\` nhưng không gọi \`form.reset()\`. Chuyển đổi tab Đăng nhập / Đăng ký dữ liệu cũ vẫn tồn tại trong React state.

### 🎯 Giải Pháp Đề Xuất
1. Khi chuyển vai trò từ Chủ bãi sang Tài xế: Xóa sạch dữ liệu và các lỗi kiểm tra của CCCD và tài khoản ngân hàng.
2. Sau khi đăng ký thành công: Reset toàn bộ form về rỗng.
3. Chuyển tab Đăng ký -> Đăng nhập: Làm sạch các thông báo lỗi cũ.

### ✅ Tiêu Chí Nghiệm Thu (Acceptance Criteria)
- [ ] Chọn Chủ bãi nhập CCCD -> Đổi sang Tài xế -> Submit thành công với thông tin tài xế sạch, không gửi kèm CCCD.
- [ ] Đăng ký thành công xong quay lại trang đăng ký: Các ô nhập đều trắng tinh.`
  },
  {
    code: 'UI-01',
    name: '[SP-UI-01] Thống nhất nhãn hiển thị và luồng chuyển trạng thái "Chờ check-in" (bỏ "Chờ vào bãi") (1 STP)',
    labels: [LABELS.FE, LABELS.BOOKING, LABELS.UI_UX, LABELS.IMPROVEMENT, LABELS.P2_MEDIUM],
    desc: `### 📌 Vấn Đề Kỹ Thuật (UI-01)
- Trạng thái \`PENDING_CHECKIN\` trong code đang có các nhãn không đồng nhất:
  - \`api.ts#L44\`: "Đã cọc · Chờ check-in"
  - \`BookingBentoStepper.tsx#L111\`: "Chờ check-in"
  - Biên bản họp và tài liệu cũ đề cập: "Chờ vào bãi".

### 🎯 Giải Pháp Đề Xuất
1. Thống nhất trên toàn bộ giao diện: Sử dụng duy nhất nhãn chuẩn **"Đã cọc · Chờ check-in"**.
2. Bảo toàn trạng thái \`PENDING_CHECKIN\` ở Backend (đã nộp cọc giữ slot 10 phút trước khi vào bãi).
3. Đồng bộ bộ lọc tìm kiếm đơn đặt chỗ ở cả Driver Portal và Merchant Portal.

### ✅ Tiêu Chí Nghiệm Thu (Acceptance Criteria)
- [ ] Không còn xuất hiện nhãn "Chờ vào bãi" trên bất kỳ màn hình nào.
- [ ] Bộ lọc trạng thái hiển thị chuẩn xác "Đã cọc · Chờ check-in".`
  },
  {
    code: 'UI-02',
    name: '[SP-UI-02] Ẩn Footer trên màn hình tìm bãi bản đồ và các trang tác vụ toàn màn hình (1 STP)',
    labels: [LABELS.FE, LABELS.UI_UX, LABELS.IMPROVEMENT, LABELS.P2_MEDIUM],
    desc: `### 📌 Vấn Đề Kỹ Thuật (UI-02)
- Frontend: \`DriverLayout.tsx#L18\` chỉ ẩn \`DriverFooter\` khi ở trang profile và payment result.
- Khi tài xế vào trang bản đồ tìm bãi (\`/parking\`), Footer vẫn hiển thị ở cuối trang, phá vỡ layout 100% viewport height và làm xuất hiện thanh cuộn dọc không cần thiết trên mobile.

### 🎯 Giải Pháp Đề Xuất
1. Bổ sung điều kiện ẩn \`DriverFooter\` trên các trang: \`/parking\` (bản đồ), \`/bookings/:id\` (trang xem vé QR), và các trang tác vụ bản đồ.
2. Bản đồ co giãn toàn màn hình mượt mà từ mép dưới Header đến đáy màn hình.

### ✅ Tiêu Chí Nghiệm Thu (Acceptance Criteria)
- [ ] Truy cập \`/parking\` không còn footer ở dưới đáy.
- [ ] Bản đồ chiếm trọn chiều cao màn hình, không phát sinh thanh cuộn dọc thừa trên cả PC và Mobile.`
  },
  {
    code: 'MAP-01',
    name: '[SP-MAP-01] Bật tính năng phóng to/thu nhỏ bản đồ bằng con lăn chuột (Scroll Wheel Zoom) (1 STP)',
    labels: [LABELS.FE, LABELS.PARKING, LABELS.UI_UX, LABELS.BUG, LABELS.P2_MEDIUM],
    desc: `### 📌 Vấn Đề Kỹ Thuật (MAP-01)
- Frontend: \`InteractiveMapCanvas.tsx#L116\` thiết lập:
  \`\`\`ts
  scrollWheelZoom: false, // <-- Đang bị tắt cứng trong cấu hình Leaflet!
  \`\`\`
- Người dùng cuộn chuột trên bản đồ không zoom được, buộc phải bấm nút (+) và (-).

### 🎯 Giải Pháp Đề Xuất
1. Đổi cấu hình thành \`scrollWheelZoom: true\` (hoặc cấu hình \`smoothWheelZoom\`).
2. Thiết lập giới hạn \`minZoom: 10\` và \`maxZoom: 18\` để tránh vỡ tile.
3. Đảm bảo cuộn chuột trên danh sách bãi (bên trái) cuộn danh sách, cuộn trên bản đồ (bên phải) zoom bản đồ.

### ✅ Tiêu Chí Nghiệm Thu (Acceptance Criteria)
- [ ] Cuộn con lăn chuột trên bản đồ phóng to / thu nhỏ mượt mà.
- [ ] Không bị giật hoặc nhảy vị trí trung tâm bản đồ.`
  },
  {
    code: 'MAP-02',
    name: '[SP-MAP-02] Đánh giá và thay thế nhà cung cấp tile bản đồ (giảm lỗi timeout OSM public tile) (3 STP)',
    labels: [LABELS.FE, LABELS.PARKING, LABELS.IMPROVEMENT, LABELS.P2_MEDIUM],
    desc: `### 📌 Vấn Đề Kỹ Thuật (MAP-02)
- Frontend: \`InteractiveMapCanvas.tsx#L121-L144\` dùng tile công cộng miễn phí \`tile.openstreetmap.org\`.
- Do bị rate-limit từ IP Việt Nam, code phải đặt \`tileTimeout\` lên tới 12 giây và thường xuyên báo lỗi "Không tải được một số ô bản đồ".

### 🎯 Giải Pháp Đề Xuất
1. Đánh giá thay thế tile server: Chuyển sang MapLibre GL với tile vector từ CartoDB/Stadia Maps, hoặc tích hợp Goong Map / Google Maps.
2. Có cơ chế fallback: Nếu nguồn tile chính lỗi, tự chuyển về nguồn dự phòng mà không đơ giao diện.
3. Thêm lớp hiển thị đường phố tiếng Việt sắc nét.

### ✅ Tiêu Chí Nghiệm Thu (Acceptance Criteria)
- [ ] Bản đồ load nhanh dưới 1.5 giây, không còn thông báo lỗi timeout ô bản đồ.
- [ ] Hiển thị đầy đủ địa danh, số nhà, tuyến đường tại các thành phố lớn.`
  },
  {
    code: 'UI-03',
    name: '[SP-UI-03] Rà soát và vô hiệu hóa nút thanh toán khi xe đang đỗ thực tế (PARKING) (2 STP)',
    labels: [LABELS.FE, LABELS.BOOKING, LABELS.PAYMENT, LABELS.BUG, LABELS.P2_MEDIUM],
    desc: `### 📌 Vấn Đề Kỹ Thuật (UI-03)
- Frontend: \`BookingActionCommands.tsx#L57-L66\` hiển thị nút:
  \`\`\`tsx
  {(isParking || isAwaitingFinal) && (
    <button onClick={onPayFinal}>THANH TOÁN KHOẢN CUỐI QUA PAYOS VIETQR ➔</button>
  )}
  \`\`\`
- Khi xe đang ở trong bãi (\`PARKING\`), xe chưa ra cổng nên chưa chốt giờ checkout, nhưng app đã cho bấm "Thanh toán khoản cuối".
- \`BookingActionCommands.tsx#L75\` nhãn nút cọc hardcode chuỗi "100.000 Đ" thay vì số tiền động.

### 🎯 Giải Pháp Đề Xuất
1. Khi trạng thái là \`PARKING\`: Ẩn nút thanh toán khoản cuối, thay bằng thông báo "Xe đang đỗ tại bãi. Cước phí sẽ được chốt khi Barie quét xe tại cổng ra."
2. Chỉ mở nút "Thanh toán khoản cuối" khi trạng thái là \`AWAITING_FINAL_PAYMENT\` (đã quét cổng ra và có số tiền chốt).
3. Đổi nhãn cọc thành số tiền động theo \`booking.advanceDeposit\`.

### ✅ Tiêu Chí Nghiệm Thu (Acceptance Criteria)
- [ ] Trạng thái \`PARKING\` không bấm thanh toán cuối được.
- [ ] Nút tiền cọc hiển thị đúng số tiền cọc của đơn (ví dụ xe container hiện đúng 200.000 đ).`
  },
  {
    code: 'NOTI-01',
    name: '[SP-NOTI-01] Sửa nội dung thông báo "Đặt chỗ thành công" trước khi nộp cọc và route điều hướng (3 STP)',
    labels: [LABELS.BE, LABELS.FE, LABELS.NOTIFICATION, LABELS.BUG, LABELS.P2_MEDIUM],
    desc: `### 📌 Vấn Đề Kỹ Thuật (NOTI-01)
- Backend: \`BookingEventListener.java#L43-L65\` khi nhận \`BOOKING_CREATED\` (mới tạo giữ chỗ, chưa trả tiền cọc) đã vội gửi thông báo "Đặt chỗ thành công" khiến tài xế tưởng đã có vé vào bãi.
- Frontend: \`notificationMapper.ts#L179-L187\` luôn map thông báo đơn đặt chỗ về route \`/bookings\` của tài xế. Chủ bãi bấm vào bị 403 Forbidden hoặc trang trắng.

### 🎯 Giải Pháp Đề Xuất
1. Đổi thông báo khi \`BOOKING_CREATED\` thành: "Yêu cầu giữ chỗ đã được tạo. Vui lòng thanh toán cọc trong 10 phút để nhận mã QR."
2. Chỉ khi nhận event \`PAYMENT_COMPLETED\` tiền cọc mới gửi thông báo "Đặt chỗ thành công! Mã QR vào bãi đã sẵn sàng."
3. Phân quyền điều hướng: Merchant chuyển về \`/merchant/bookings/:id\`, Driver chuyển về \`/bookings/:id\`.

### ✅ Tiêu Chí Nghiệm Thu (Acceptance Criteria)
- [ ] Chưa thanh toán cọc không bao giờ nhận thông báo "Đặt chỗ thành công".
- [ ] Chủ bãi bấm thông báo nhảy đúng vào trang chi tiết đơn hàng của chủ bãi.`
  },
  {
    code: 'LOT-01',
    name: '[SP-LOT-01] Khôi phục nút kích hoạt AI sinh mô tả bãi xe, nội quy PCCC và lộ trình (3 STP)',
    labels: [LABELS.FE, LABELS.AI, LABELS.PARKING, LABELS.BUG, LABELS.P2_MEDIUM],
    desc: `### 📌 Vấn Đề Kỹ Thuật (LOT-01)
- Backend: \`ai-service\` có API \`POST /api/v1/ai/generate-description\` (\`AiController.java#L36-L41\`) dùng Gemini AI sinh mô tả marketing, nội quy PCCC và lộ trình.
- Frontend: \`CreateParkingLotBasicInfoCard.tsx#L64-L92\` có sẵn khung hiển thị nội dung AI, **nhưng hoàn toàn thiếu nút bấm kích hoạt gọi API!** (Dẫn đến ghi chú "mất sinh nội dung cho chủ bãi" trong biên bản họp).

### 🎯 Giải Pháp Đề Xuất
1. Bổ sung nút bấm **"✨ AI Assistant: Tự động sinh mô tả & nội quy bãi"** tại form thông tin cơ bản.
2. Mở modal nhập từ khóa nổi bật (tiện ích, vị trí gần cảng/quốc lộ).
3. Gọi API AI, nhận kết quả và tự động điền vào các ô mô tả tương ứng.

### ✅ Tiêu Chí Nghiệm Thu (Acceptance Criteria)
- [ ] Bấm nút AI Assistant -> Nhập từ khóa -> Sinh ra 3 đoạn văn bản chuẩn format.
- [ ] Chủ bãi có thể chỉnh sửa lại nội dung do AI sinh ra trước khi lưu.`
  },
  {
    code: 'MEDIA-01',
    name: '[SP-MEDIA-01] Sửa giới hạn cho phép tải tối đa 6 ảnh bãi xe (hiện tại bị chặn ở ảnh thứ 5) (1 STP)',
    labels: [LABELS.FE, LABELS.PARKING, LABELS.BUG, LABELS.P2_MEDIUM],
    desc: `### 📌 Vấn Đề Kỹ Thuật (MEDIA-01)
- Frontend: \`CreateParkingLotPhotosCard.tsx#L19-L22\` giới hạn cứng:
  \`\`\`tsx
  if (images.length + newItems.length >= 5) { // Chặn ở 5 ảnh!
    setErrorMessage('Bạn có thể tải tối đa 5 ảnh...')
    break
  }
  \`\`\`
- Nhãn đếm ghi \`{images.length}/5 ảnh\`, khi có 5 ảnh thì ẩn luôn vùng chọn ảnh, không cho thêm ảnh thứ 6 như nghiệp vụ yêu cầu.

### 🎯 Giải Pháp Đề Xuất
1. Đổi giới hạn từ \`5\` lên \`6\` ở cả 4 vị trí: vòng lặp kiểm tra, nhãn đếm \`images.length/6 ảnh\`, điều kiện ẩn dropzone, và thông báo lỗi.
2. Cho phép tải ảnh thứ 6, chặn từ ảnh thứ 7.
3. Khi xóa 1 ảnh, cho phép tải bù ảnh mới.

### ✅ Tiêu Chí Nghiệm Thu (Acceptance Criteria)
- [ ] Tải được trọn vẹn 6 ảnh bãi xe (ảnh toàn cảnh, cổng, khoang đỗ...).
- [ ] Cố tình chọn 7 ảnh sẽ nhận cảnh báo "Bạn chỉ có thể tải tối đa 6 ảnh".`
  },
  {
    code: 'VEH-01',
    name: '[SP-VEH-01] Thêm ràng buộc và validate dải tải trọng hợp lệ theo từng loại phương tiện (2 STP)',
    labels: [LABELS.BE, LABELS.FE, LABELS.VEHICLE, LABELS.BUG, LABELS.P2_MEDIUM],
    desc: `### 📌 Vấn Đề Kỹ Thuật (VEH-01)
- Backend: \`AddVehicleRequest.java#L20-L22\` chỉ có \`@Positive\`, không có giới hạn trần (\`@DecimalMax\`), người dùng nhập 1.000.000 tấn vẫn lọt qua.
- Bất cập: Xe ô tô con (\`SEDAN_SUV\`) cũng bắt buộc nhập tải trọng tính bằng tấn và lớn hơn 0, gây khó hiểu cho người dùng xe gia đình.

### 🎯 Giải Pháp Đề Xuất
1. Backend: Bổ sung \`@DecimalMax(value = "150.0")\` cho tải trọng xe.
2. Frontend: Phân biệt theo loại xe:
   - \`SEDAN_SUV\`: Cho phép để trống hoặc tự động gán mặc định 1.5 tấn.
   - \`LIGHT_TRUCK\`, \`HEAVY_TRUCK\`, \`CONTAINER\`: Bắt buộc nhập trong dải 0.5 tấn đến 100 tấn, tối đa 2 chữ số thập phân.
3. Thông báo lỗi tiếng Việt dễ hiểu.

### ✅ Tiêu Chí Nghiệm Thu (Acceptance Criteria)
- [ ] Đăng ký xe con không bị bắt bẻ ép nhập tải trọng khó hiểu.
- [ ] Đăng ký xe tải nhập tải trọng âm hoặc >150 tấn bị chặn và báo lỗi rõ ràng.`
  },
  {
    code: 'VEH-02',
    name: '[SP-VEH-02] Chặn xóa/ngừng sử dụng phương tiện khi đang có lượt đỗ hoạt động hoặc nợ cước (3 STP)',
    labels: [LABELS.BE, LABELS.VEHICLE, LABELS.BOOKING, LABELS.BUG, LABELS.P1_HIGH],
    desc: `### 📌 Vấn Đề Kỹ Thuật (VEH-02)
- Backend: \`VehicleServiceImpl.java#L107\` khi xóa phương tiện chỉ đơn thuần đặt \`vehicle.setStatus(VehicleStatusEnum.INACTIVE)\`.
- Hoàn toàn không kiểm tra xem phương tiện có đang đỗ trong bãi hay đang giữ chỗ không. Tài xế có thể cho xe vào bãi rồi bấm ngừng sử dụng xe trên app để trốn thanh toán phụ phí hoặc gây lỗi hệ thống Barie check-out.

### 🎯 Giải Pháp Đề Xuất
1. Trước khi đổi trạng thái xe sang \`INACTIVE\`, truy vấn \`booking-service\` kiểm tra các đơn của xe.
2. Nếu có đơn ở trạng thái \`PENDING_CHECKIN\`, \`PARKING\`, \`OVERTIME\`, \`AWAITING_FINAL_PAYMENT\`: Từ chối xóa và báo lỗi: "Phương tiện đang có lượt đỗ hoạt động (Mã: #BKxxx). Vui lòng hoàn tất check-out trước."
3. Xe đã ngừng sử dụng bị ẩn khỏi danh sách chọn đặt chỗ mới, nhưng bảo lưu toàn bộ lịch sử đỗ cũ.

### ✅ Tiêu Chí Nghiệm Thu (Acceptance Criteria)
- [ ] Xe đang đỗ trong bãi không thể bấm xóa/ngừng sử dụng.
- [ ] Xe không có đơn đang chạy thì ngừng sử dụng bình thường, lịch sử cũ không bị mất.`
  },
  {
    code: 'VEH-03',
    name: '[SP-VEH-03] Nghiên cứu bổ sung tải lên ảnh giấy tờ xe (đăng kiểm, cà vẹt) (3 STP)',
    labels: [LABELS.BE, LABELS.FE, LABELS.VEHICLE, LABELS.IMPROVEMENT, LABELS.P3_LOW],
    desc: `### 📌 Yêu Cầu Cải Tiến (VEH-03 - Backlog P3)
- Bổ sung chức năng lưu trữ và kiểm tra giấy tờ xe (Đăng kiểm, Cà vẹt xe, Phù hiệu vận tải) phục vụ bãi đỗ xe đối soát tải trọng và pháp lý.
- Entity \`Vehicle.java\` hiện chỉ có text thuần, chưa có trường lưu trữ URL tài liệu.

### 🎯 Giải Pháp Đề Xuất
1. Thiết kế bảng \`vehicle_documents\` liên kết với xe: \`document_type\`, \`image_url\`, \`expiry_date\`.
2. Giao diện tải ảnh 2 mặt giấy tờ xe, hỗ trợ phóng to xem chi tiết.
3. Phân quyền bảo mật: Chỉ chủ xe và Quản trị viên mới được xem ảnh giấy tờ.

### ✅ Tiêu Chí Nghiệm Thu (Acceptance Criteria)
- [ ] Cho phép tải lên ảnh giấy đăng kiểm và cà vẹt xe dạng JPG/PNG/PDF.
- [ ] Có cảnh báo khi giấy đăng kiểm sắp hết hạn.`
  },
  {
    code: 'BOOK-01',
    name: '[SP-BOOK-01] Chặn xe đang đỗ tại bãi tiếp tục đặt chỗ mới gây xung đột slot (3 STP)',
    labels: [LABELS.BE, LABELS.BOOKING, LABELS.BUG, LABELS.P1_HIGH],
    desc: `### 📌 Lỗ Hổng Nghiệp Vụ Nghiêm Trọng (BOOK-01)
- Backend: \`HoldPolicyStore.java#L31\` câu truy vấn kiểm tra xe bận:
  \`\`\`sql
  SELECT id, driver_id FROM bookings WHERE vehicle_id=? AND status='PENDING_PAYMENT' ORDER BY id FOR UPDATE
  \`\`\`
- Chỉ kiểm tra \`PENDING_PAYMENT\`! Khi xe đã nộp cọc (\`PENDING_CHECKIN\`), hoặc **xe đang đỗ thực tế (\`PARKING\`, \`OVERTIME\`)**, hệ thống xem như xe rảnh rỗi.
- Hậu quả: Xe đang nằm trong Bãi A nhưng tài xế vẫn tạo thêm đơn đặt chỗ giữ slot ở Bãi B, vi phạm quy luật vật lý và chiếm dụng ảo chỗ đỗ.

### 🎯 Giải Pháp Đề Xuất
1. Mở rộng điều kiện kiểm tra xe bận trên SQL:
   \`status IN ('PENDING_PAYMENT', 'PENDING_CHECKIN', 'PARKING', 'OVERTIME', 'AWAITING_FINAL_PAYMENT')\`.
2. Ném ra ngoại lệ \`HoldRestrictionException\` với thông báo: "Xe này hiện đang có lượt đỗ tại bãi (Mã đơn: #BKxxx). Vui lòng hoàn tất lượt đỗ hiện tại trước."
3. Frontend vô hiệu hóa nút đặt chỗ và cảnh báo nếu chọn xe đang trong bãi.

### ✅ Tiêu Chí Nghiệm Thu (Acceptance Criteria)
- [ ] Xe đang ở trạng thái \`PARKING\` hoặc \`PENDING_CHECKIN\` không thể tạo thêm bất kỳ đơn đặt chỗ nào khác.
- [ ] Thông báo rõ ràng mã đơn đang xung đột.`
  },
  {
    code: 'TICKET-01',
    name: '[SP-TICKET-01] Chặn sinh và hiển thị mã QR Barie khi chưa hoàn tất thanh toán cọc (2 STP)',
    labels: [LABELS.FE, LABELS.BOOKING, LABELS.SECURITY, LABELS.BUG, LABELS.P1_HIGH],
    desc: `### 📌 Lỗ Hổng Bảo Mật (TICKET-01)
- Frontend: \`BookingDetailView.tsx#L58\` render \`<BookingQrTerminalPanel ref={ref} booking={booking} />\` vô điều kiện.
- Đơn hàng ở trạng thái \`PENDING_PAYMENT\` (chưa nộp cọc), \`CANCELLED\` (đã hủy) hoặc \`EXPIRED\` (hết hạn) vẫn hiển thị mã QR kèm mã Barie to rõ ràng.
- Tài xế có thể chụp màn hình mã QR này đưa cho nhân viên barie quét để vào bãi mà chưa thanh toán tiền cọc.

### 🎯 Giải Pháp Đề Xuất
1. Frontend: Tuyệt đối chỉ hiển thị mã QR Barie khi trạng thái là \`PENDING_CHECKIN\` (hoặc \`PARKING\` khi xuất bãi).
2. Khi trạng thái là \`PENDING_PAYMENT\`: Thay vùng QR bằng thông tin thanh toán VietQR PayOS kèm đếm ngược 10 phút.
3. Khi \`CANCELLED\` hoặc \`EXPIRED\`: Đóng dấu watermark "VÉ KHÔNG CÓ HIỆU LỰC".

### ✅ Tiêu Chí Nghiệm Thu (Acceptance Criteria)
- [ ] Mở chi tiết đơn hàng chưa trả cọc: Không thể thấy mã QR Barie vào cổng.
- [ ] Thanh toán cọc thành công -> Mã QR tự động hiển thị ngay lập tức.`
  },
  {
    code: 'PAY-01',
    name: '[SP-PAY-01] Minh bạch cơ chế tính phí sàn trên tiền cọc và luồng quyết toán hoàn tiền (3 STP)',
    labels: [LABELS.BE, LABELS.PAYMENT, LABELS.BUG, LABELS.P1_HIGH],
    desc: `### 📌 Vấn Đề Kỹ Thuật (PAY-01)
- Backend: Phí sàn (10%) hiện chỉ được tính gộp chung vào cuối ngày khi quyết toán (\`DailySettlementServiceImpl.java\`).
- Tiền cọc (\`DEPOSIT\`) khi thu vào nằm ở tài khoản ký quỹ \`CUSTOMER_DEPOSIT_ESCROW\`. Nếu khách hủy đơn hoặc bỏ cọc (No-show), chưa có quy chuẩn phân bổ tiền cọc bị tịch thu giữa Chủ bãi và Sàn.

### 🎯 Giải Pháp Đề Xuất
1. Định nghĩa rõ công thức phí sàn trên tiền cọc: \`Phí sàn = Tiền cọc * % Phí sàn\`.
2. Bổ sung trường \`depositPlatformFee\` và \`depositMerchantNet\` trong chi tiết đối soát.
3. Minh bạch chính sách chia tiền khi khách bỏ cọc: Phần trăm bồi thường giữ chỗ cho bãi xe và phần trăm phí vận hành sàn.

### ✅ Tiêu Chí Nghiệm Thu (Acceptance Criteria)
- [ ] Bảng kê giao dịch thể hiện rõ: Tiền cọc thu, Phí sàn khấu trừ, Số tiền chủ bãi thực nhận.
- [ ] Đối soát sổ cái khớp chính xác 100% dòng tiền cọc.`
  },
  {
    code: 'CHECKOUT-01',
    name: '[SP-CHECKOUT-01] Khắc phục lỗi tính trùng cước giờ ranh giới giữa giờ quy định và giờ quá hạn (3 STP)',
    labels: [LABELS.BE, LABELS.BOOKING, LABELS.PAYMENT, LABELS.BUG, LABELS.P1_HIGH],
    desc: `### 📌 Lỗi Logic Toán Học Tính Cước (CHECKOUT-01)
- Backend: \`CheckInOutServiceImpl.java#L106-L115\` chia giờ thành 2 đoạn \`regular\` và \`overtime\`.
- \`ParkingCharge.java#L17\` làm tròn lên từng block 1 giờ bắt đầu:
  - Xe đỗ từ 10:00 đến 13:00 (3 tiếng), hẹn ra lúc 12:30.
  - \`regular\` tính từ 10:00 - 12:30 -> làm tròn thành 3 tiếng.
  - \`overtime\` tính từ 12:30 - 13:00 -> làm tròn thành 1 tiếng phạt.
  - Tổng cộng bị tính 4 tiếng (trong khi xe thực tế chỉ đỗ đúng 3 tiếng)! Block 12:00 - 12:30 bị tính đúp.

### 🎯 Giải Pháp Đề Xuất
1. Chuẩn hóa thuật toán tính cước: Tính tổng thời gian thực tế trước, thời gian phạt chỉ tính trên số phút thực tế vượt quá giờ hẹn:
   \`Số phút quá hạn = max(0, ActualExit - ExpectedExit)\`.
2. Đảm bảo tổng số giờ tính cước không bao giờ vượt quá tổng thời lượng xe đỗ trong bãi.
3. Bổ sung 10 ca kiểm thử ranh giới giờ (vào sớm, ra trễ 5p, ra trễ 65p...).

### ✅ Tiêu Chí Nghiệm Thu (Acceptance Criteria)
- [ ] Xe đỗ 3 tiếng (từ 10:00 đến 13:00, hẹn 12:30) không bị tính thành 4 tiếng cước.
- [ ] Bảng kê giải trình từng phút minh bạch.`
  },
  {
    code: 'CHECKOUT-02',
    name: '[SP-CHECKOUT-02] Triển khai bộ đếm thời gian đỗ xe thực tế từ checkInTime thay cho text hardcode (2 STP)',
    labels: [LABELS.FE, LABELS.BOOKING, LABELS.BUG, LABELS.P1_HIGH],
    desc: `### 📌 Vấn Đề Kỹ Thuật (CHECKOUT-02)
- Frontend: \`BookingBentoStepper.tsx#L111\` chuỗi text \`'ĐANG DIỄN RA (3h45)'\` được **hardcode cứng 100% trong mã nguồn**!
- Bất kể xe mới vào bãi được 1 phút hay đã đỗ 2 ngày, màn hình luôn hiện cố định \`3h45\`.

### 🎯 Giải Pháp Đề Xuất
1. Xây dựng hook đếm giờ thực tế (\`useLiveParkingDuration\`):
   - Khi \`PARKING\`: \`Thời gian đỗ = Date.now() - new Date(checkInTime)\`.
   - Tự động nhảy số mỗi 60 giây (định dạng: \`X giờ Y phút\`).
2. Khi \`COMPLETED\`: Dừng đồng hồ cố định ở thời lượng thực tế: \`checkOutTime - checkInTime\`.
3. F5 trang hoặc mở trên thiết bị khác kết quả hiển thị phải đồng nhất.

### ✅ Tiêu Chí Nghiệm Thu (Acceptance Criteria)
- [ ] Xe mới check-in hiển thị "0 giờ 1 phút", nhảy số theo thời gian thực.
- [ ] Khi đã check-out, hiển thị đúng tổng thời gian đã đỗ trong bãi.`
  },
  {
    code: 'CHECKOUT-03',
    name: '[SP-CHECKOUT-03] Xử lý số tiền còn lại phải trả tại cổng (khấu trừ cọc) và hoàn tiền cọc thừa (3 STP)',
    labels: [LABELS.BE, LABELS.BOOKING, LABELS.PAYMENT, LABELS.BUG, LABELS.P1_HIGH],
    desc: `### 📌 Vấn Đề Kỹ Thuật (CHECKOUT-03)
- Backend: \`CheckInOutServiceImpl.java#L117, L121\`:
  \`\`\`java
  BigDecimal amountDue = totalAmount.subtract(deposit).max(BigDecimal.ZERO);
  if (amountDue.compareTo(BigDecimal.ZERO) <= 0) {
      booking.setStatus(BookingStatusEnum.COMPLETED);
  }
  \`\`\`
- Nếu tài xế nộp cọc 100.000 đ nhưng lấy xe ra sớm, cước thực tế chỉ 30.000 đ: \`amountDue = 0\`.
- Hệ thống đóng đơn thành công và **nuốt trọn 70.000 đ tiền cọc thừa**, không hề tạo lệnh hoàn tiền hay ghi có ví cho khách!

### 🎯 Giải Pháp Đề Xuất
1. Làm rõ công thức:
   - \`Số tiền phải nộp thêm = max(0, Tổng cước - Cọc đã nộp)\`
   - \`Tiền cọc thừa cần hoàn = max(0, Cọc đã nộp - Tổng cước)\`
2. Nếu phát sinh cọc thừa: Tự động hoàn tiền vào ví tài xế hoặc tạo yêu cầu hoàn tiền qua cổng PayOS.
3. Màn hình Barie hiển thị đủ 3 chỉ số: Tổng cước, Đã nộp cọc, Cần thanh toán / Hoàn trả.

### ✅ Tiêu Chí Nghiệm Thu (Acceptance Criteria)
- [ ] Khách lấy xe ra sớm hơn tiền cọc được hoàn lại đúng số tiền chênh lệch.
- [ ] Không thu trùng tiền cọc đã thanh toán.`
  },
  {
    code: 'CHECKOUT-04',
    name: '[SP-CHECKOUT-04] Hiển thị chi tiết dòng phạt quá hạn, hệ số nhân và công thức trên vé checkout (2 STP)',
    labels: [LABELS.FE, LABELS.BOOKING, LABELS.PAYMENT, LABELS.UI_UX, LABELS.P1_HIGH],
    desc: `### 📌 Vấn Đề Kỹ Thuật (CHECKOUT-04)
- Frontend: \`TariffAuditTable.tsx#L32-L86\` chỉ hiển thị: Giá cơ bản, Thời gian đỗ, Cọc đã trả, Tổng cần thanh toán.
- Hoàn toàn không có dòng nào hiển thị về: Thời gian quá hạn, Hệ số phạt (VD: 1.5x, 2.0x), và Tiền phạt phát sinh.
- Tài xế bị thu tiền nhiều hơn dự kiến không hiểu tại sao, dẫn đến tranh cãi tại cổng ra.

### 🎯 Giải Pháp Đề Xuất
1. Khi có quá giờ hẹn, bổ sung dòng riêng màu cam nổi bật:
   - "Phụ phí đỗ quá giờ: Vượt 1 giờ 20 phút (Hệ số phạt 1.5× giá cơ bản 40.000 đ/h) = +80.000 đ".
2. Hiển thị rõ mốc giờ: Hẹn ra lúc 14:00, Thực tế ra lúc 15:20.
3. Khớp chính xác 100% với số tiền Backend tính toán.

### ✅ Tiêu Chí Nghiệm Thu (Acceptance Criteria)
- [ ] Đơn có quá giờ hiển thị tách bạch: Tiền đỗ tiêu chuẩn, Tiền phạt quá hạn, Tiền cọc khấu trừ, Tổng phải trả.
- [ ] Có chú thích công thức tính rõ ràng.`
  },
  {
    code: 'WITHDRAW-01',
    name: '[SP-WITHDRAW-01] Tách bước Duyệt yêu cầu và Xác nhận đã chuyển khoản thực tế có mã tham chiếu (5 STP)',
    labels: [LABELS.BE, LABELS.FE, LABELS.PAYMENT, LABELS.SECURITY, LABELS.BUG, LABELS.P1_HIGH],
    desc: `### 📌 Lỗ Hổng Vận Hành Tài Chính (WITHDRAW-01)
- Backend: \`WithdrawalServiceImpl.java#L84-L121\` khi Admin bấm Duyệt, hệ thống lập tức đặt trạng thái \`APPROVED\` và ghi nhận quyết toán trừ tiền ví vĩnh viễn.
- Trạng thái \`PAID\` trong enum không bao giờ được dùng đến.
- Thiếu bước quan trọng: Kế toán thực tế chuyển tiền qua ngân hàng, nhập mã tham chiếu giao dịch (\`bankRefCode\`) và tải ảnh ủy nhiệm chi. Nếu lệnh chuyển khoản ngân hàng bị lỗi, hệ thống đã lỡ trừ tiền ví của chủ bãi.

### 🎯 Giải Pháp Đề Xuất
1. Tách luồng thành 3 trạng thái rõ ràng:
   - \`PENDING\`: Chủ bãi yêu cầu, đóng băng số dư (HOLD).
   - \`APPROVED\`: Quản trị viên duyệt hồ sơ, sẵn sàng chi tiền.
   - \`PAID\`: Kế toán đã chuyển khoản thành công, nhập mã giao dịch ngân hàng và đính kèm ủy nhiệm chi.
2. Thêm nút "Hủy / Chuyển thất bại": Hoàn trả số tiền đang HOLD lại ví cho chủ bãi nếu ngân hàng từ chối.
3. Lưu vết đầy đủ: người duyệt, người chi tiền, mã tham chiếu, ảnh chứng từ.

### ✅ Tiêu Chí Nghiệm Thu (Acceptance Criteria)
- [ ] Bấm Duyệt không tự động trừ tiền ví ngay, chuyển sang trạng thái "Chờ chi tiền".
- [ ] Nhập mã giao dịch ngân hàng và tải ảnh ủy nhiệm chi mới hoàn tất sang trạng thái "Đã thanh toán (PAID)".
- [ ] Có cơ chế chống double-payout nếu bấm duyệt nhiều lần.`
  }
];

async function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

async function createCards() {
  console.log(`🚀 Bắt đầu tạo 24 thẻ Trello vào danh sách: "Sprint 04 - Backlog" (${TARGET_LIST_ID})...\n`);

  let count = 0;
  const createdResults = [];

  for (const card of CARDS_DATA) {
    count++;
    console.log(`[${count}/24] Đang tạo thẻ: ${card.name}...`);

    try {
      // 1. Tạo card với đầy đủ nhãn
      const cardUrl = `https://api.trello.com/1/cards?key=${key}&token=${token}`;
      const res = await fetch(cardUrl, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          idList: TARGET_LIST_ID,
          name: card.name,
          desc: card.desc,
          idLabels: card.labels,
          pos: 'bottom',
        })
      });

      if (!res.ok) {
        const errorText = await res.text();
        console.error(`❌ Lỗi tạo thẻ ${card.code}:`, res.status, errorText);
        continue;
      }

      const created = await res.json();
      console.log(`   ✅ Đã tạo thẻ: ${created.id} (${created.shortUrl})`);
      createdResults.push({
        code: card.code,
        id: created.id,
        shortUrl: created.shortUrl,
        name: card.name
      });

      await sleep(250); // Tránh rate-limit giữa các thẻ
    } catch (err) {
      console.error(`❌ Ngoại lệ khi tạo thẻ ${card.code}:`, err.message);
    }
  }

  const outPath = path.resolve(__dirname, 'created_audit_cards.json');
  fs.writeFileSync(outPath, JSON.stringify(createdResults, null, 2), 'utf8');
  console.log(`\n🎉 Hoàn tất quá trình tạo thẻ Trello! Tổng số thẻ đã xử lý: ${createdResults.length}/24.`);
  console.log(`📄 Đã lưu kết quả tại: ${outPath}`);
}

createCards();
