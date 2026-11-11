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
const listId = '6adecc33f1b35f096a0e9871'; // Sprint Backlog 2 (Done)

const cardName = '[SP-DON-17] [Fullstack/DevOps] Triển khai Bảo mật Đa Tầng API Gateway (CORS Whitelist, X-App-Key Filter) & Tối ưu Vercel Deployment (5 STP)';

const cardDesc = `### 🎯 Mục tiêu công việc:
- Thiết lập hàng rào bảo mật đa tầng (Multi-tier Defense) cho Backend Microservices chống truy cập trái phép.
- Ngăn chặn các client bên ngoài, script lạ hoặc bot cào dữ liệu khi chưa được cấp phép.
- Hoàn thiện hạ tầng cấu hình để deploy Frontend lên Vercel thông suốt (chống lỗi Mixed Content và 404 SPA Routing).
- Cập nhật logo nhận diện thương hiệu đồng bộ trên tab trình duyệt và toàn bộ hệ thống.

### 🛠️ Chi tiết công việc đã thực hiện:
1. **Tầng 1 - Khóa chặt CORS tại API Gateway (Spring Cloud Gateway)**:
   - Thay thế cấu hình \`allowedOriginPatterns: "*"\` thành danh sách whitelist chặt chẽ:
     - \`http://localhost:*\` (phát triển local)
     - \`http://127.0.0.1:*\`
     - \`https://*.vercel.app\` (tất cả deployment Vercel preview & production)
     - \`https://vercel.app\`
     - \`http://103.20.102.190\` (IP VPS)
   - Trình duyệt sẽ tự động chặn mọi request xuất phát từ các domain lạ, chống tấn công CSRF / cross-origin injection.

2. **Tầng 2 - Khóa Chìa khóa Xác thực Ứng dụng (X-App-Key Header Filter)**:
   - **Backend**: Viết mới \`ApiKeyValidationFilter.java\` (GlobalFilter Order -100). Tự động chặn và trả về \`403 Forbidden\` (\`ERR_403\`) nếu request không có header \`X-App-Key\` hợp lệ. Bỏ qua preflight OPTIONS và các endpoint \`/actuator/**\`.
   - **Frontend**: Cấu hình Axios Request Interceptor trong \`client.ts\` để tự động gắn \`X-App-Key: SP_APP_KEY_2026_xM99qP2!\` vào tất cả request gọi tới Backend.
   - **Hỗ trợ Postman**: Cung cấp key để lập trình viên test bằng Postman thông qua header \`X-App-Key: SP_APP_KEY_2026_xM99qP2!\`.

3. **Tầng 3 - Tối ưu Vercel Deployment & SPA Routing**:
   - Tạo file \`vercel.json\` với rewrite rule \`/(.*) -> /index.html\` để fix lỗi 404 khi làm mới trang trên React Router.
   - Cấu hình Reverse Proxy \`/api/:path* -> http://103.20.102.190/api/:path*\` xử lý triệt để lỗi Mixed Content (HTTPS sang HTTP).

4. **Tầng 4 - Cập nhật Nhận diện Thương hiệu & Favicon**:
   - Xóa bỏ \`favicon.svg\` cũ, cập nhật favicon trên tab trình duyệt sang \`image-smart-parking-hub.png\` trong \`index.html\`.
   - Thay thế icon xe tải placeholder bằng logo chính thức trên 6 layout: \`LandingNavbar\`, \`LandingFooter\`, \`DriverHeader\`, \`MerchantSidebar\`, \`AuthLayout\`, \`AdminLayout\`.

5. **Tầng 5 - Sửa lỗi UI Admin Header**:
   - Điều chỉnh padding trong \`AdminLayout.tsx\` (\`lg:pl-[calc(16rem+2rem)] lg:pr-8\`), khắc phục lỗi tiêu đề dính sát viền sidebar.

### 🧪 Bằng chứng kiểm thử (Verification & Evidence):
- Gradle compile Java: \`BUILD SUCCESSFUL\` (:common-library & :api-gateway).
- Frontend build: \`tsc -b && vite build\` thành công 100% trong 14.33s.
- Git commit & push: Đã push lên nhánh \`main\` ở cả 2 repository:
  - Backend: commit \`5865027\`
  - Frontend: commits \`cd38c97\`, \`ff3a827\`, \`7820d04\`

### 📌 Điểm Story Point đề xuất: 5 STP`;

async function run() {
  const res = await fetch(`https://api.trello.com/1/cards?key=${key}&token=${token}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      idList: listId,
      name: cardName,
      desc: cardDesc,
      pos: 'top'
    })
  });

  const createdCard = await res.json();
  console.log('✅ Đã tạo thẻ Trello thành công!');
  console.log('Tên thẻ:', createdCard.name);
  console.log('ID thẻ:', createdCard.id);
  console.log('URL thẻ:', createdCard.shortUrl);
}

run();
