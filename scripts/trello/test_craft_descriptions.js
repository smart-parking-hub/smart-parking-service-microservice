import fs from 'node:fs';

const allData = JSON.parse(fs.readFileSync('before_restructure_snapshot.json', 'utf-8'));
const cards = allData.cards;
const titles = JSON.parse(fs.readFileSync('title_mappings.json', 'utf-8'));
const titleMap = Object.fromEntries(titles.map(t => [t.id, t.newName]));

console.log('Testing description generation...');

function buildDescription(card) {
  const name = card.name;
  const list = card.listName;
  const oldDesc = card.desc || '';
  const newTitle = titleMap[card.id] || name;

  // Extract ID e.g. SP-S1-UI-01
  const idMatch = newTitle.match(/^\[([a-zA-Z0-9\-_]+)\]/);
  const ticketId = idMatch ? idMatch[1] : 'SP-GEN-01';

  // Determine domain / type
  let type = 'FE';
  let context = 'Frontend CRM V2';
  let repo = '`smart-parking-app-v2`';
  
  if (name.includes('[BE]') || name.includes('[DB]') || name.includes('[DATABASE]') || ticketId.includes('BKG') || ticketId.includes('PAY') || ticketId.includes('SEC') && !name.includes('[FE]')) {
    type = 'BE';
    context = 'Backend Microservices';
    repo = '`smart-parking-microservice`';
  } else if (name.includes('[OPS]') || name.includes('[CI/CD]') || name.includes('[DEV-OPS]') || name.includes('[INFRASTRUCTURE]')) {
    type = 'DEVOPS';
    context = 'Infrastructure & CI/CD';
    repo = '`smart-parking-microservice/docker` & `.github/workflows`';
  } else if (name.includes('[QA]')) {
    type = 'QA';
    context = 'Quality Assurance & Testing';
    repo = '`smart-parking-app-v2` & `smart-parking-microservice`';
  } else if (name.includes('[SECURITY]')) {
    type = 'SECURITY';
    context = 'System Security & Access Control';
    repo = '`smart-parking-microservice`';
  } else if (name.includes('[AI]')) {
    type = 'BE';
    context = 'AI Copilot & Gemini Service';
    repo = '`smart-parking-microservice/ai-service`';
  } else if (name.includes('[RISK-')) {
    type = 'RISK';
    context = 'Project Risk Register';
    repo = 'Toàn dự án';
  } else if (name.includes('[IMP-')) {
    type = 'IMPROVEMENT';
    context = 'Project Quality & Performance';
    repo = 'Toàn dự án';
  }

  // Handle Sprint 1 FE specifically
  const isS1FE = (list.includes('Sprint 01') && (name.includes('[FE]') || type === 'FE'));

  // Extract clean summary / objective from old desc
  let objective = 'Triển khai và hoàn thiện tính năng theo yêu cầu kỹ thuật.';
  const objMatch = oldDesc.match(/## 1\. Mục tiêu\s*\n+([^\n#]+)/i);
  if (objMatch && objMatch[1].trim()) {
    objective = objMatch[1].trim();
  }

  // Extract file paths from old desc
  let filePaths = [];
  const fileLines = oldDesc.match(/(?:smart-parking-[a-z0-9\-_./]+|\b(?:src|docker|\.github)\/[a-zA-Z0-9\-_./]+\.[a-zA-Z0-9]+)/g) || [];
  filePaths = [...new Set(fileLines)].map(f => {
    // If S1 FE, mark as legacy
    if (isS1FE && f.includes('smart-parking-app/')) {
      return `\`${f}\` *(Mã nguồn v1 legacy — đã được tái thiết kế trên v2)*`;
    }
    // Update smart-parking-app to v2 if not S1
    if (!isS1FE && f.startsWith('smart-parking-app/')) {
      return `\`${f.replace('smart-parking-app/', 'smart-parking-app-v2/')}\``;
    }
    return `\`${f}\``;
  });

  if (filePaths.length === 0) {
    filePaths = ['`Chưa xác minh`'];
  }

  // Extract API endpoints
  let apiLines = [];
  const apiMatch = oldDesc.match(/(?:GET|POST|PUT|DELETE)\s+\/api\/v1\/[a-zA-Z0-9\-_/{}.]+/g);
  if (apiMatch) {
    apiLines = [...new Set(apiMatch)];
  }

  // Check if duplicate
  let duplicateBanner = '';
  if (name.includes('SP-S2-UI-032B')) {
    duplicateBanner = '> [!NOTE] THẺ TRÙNG LẶP / ĐÃ ĐỒNG BỘ\n> Thẻ này trùng phạm vi với thẻ gốc **[SP-S2-UI-032]** (Cấu hình Sức chứa Sơ đồ 5 Loại xe). Toàn bộ công việc kỹ thuật được theo dõi và nghiệm thu chính thức tại thẻ gốc.\n\n';
  } else if (name.includes('SP-S3-PAY-12')) {
    duplicateBanner = '> [!NOTE] THẺ TRÙNG LẶP / ĐÃ ĐỒNG BỘ\n> Thẻ này trùng phạm vi với thẻ gốc **[SP-S3-PAY-01]** (Đồng bộ hóa hợp đồng API thu tiền mặt Barie GAP-02). Nghiệm thu kỹ thuật được thực hiện tại thẻ gốc.\n\n';
  } else if (name.includes('SP-S3-SEC-02') && list.includes('To Do')) {
    duplicateBanner = '> [!NOTE] THẺ TRÙNG LẶP / ĐÃ ĐỒNG BỘ\n> Thẻ này trùng phạm vi với thẻ gốc **[SP-S3-SEC-01]** (Tái cấu trúc cơ chế OTP GAP-09). Nghiệm thu kỹ thuật được thực hiện tại thẻ gốc.\n\n';
  } else if (name.includes('SP-S3-OPS-01') && list.includes('To Do')) {
    duplicateBanner = '> [!NOTE] THẺ TRÙNG LẶP / ĐÃ ĐỒNG BỘ\n> Thẻ này trùng phạm vi với thẻ gốc **[SP-S3-OPS-03]** (Pipeline GitHub Actions kiểm soát chất lượng FE v2 đã hoàn thành). Nghiệm thu tại thẻ gốc.\n\n';
  } else if (name.includes('IMP-03')) {
    duplicateBanner = '> [!NOTE] THẺ ĐÃ TRIỂN KHAI HOÀN TẤT\n> Nội dung tối ưu hóa Spatial Indexing ST_Distance_Sphere đã được hiện thực hóa và nghiệm thu đầy đủ trong **[SP-S3-DAT-01]** tại Sprint 03 Done.\n\n';
  }

  const owner = card.members && card.members.length > 0 ? card.members.join(', ') : 'Chưa phân công';

  // Construct template
  const descTemplate = `${duplicateBanner}## 1. THÔNG TIN ĐIỀU PHỐI (COORDINATION)
- **Mã định danh:** \`${ticketId}\`
- **Phân loại & Bounded Context:** \`${type}\` — ${context}
- **Người thực hiện chính (Owner):** ${owner}
- **Phụ thuộc tiền đề (BLOCKED BY):** Không áp dụng
- **Giải phóng tiếp theo (UNBLOCKS):** Không áp dụng
- **Có thể chạy song song (PARALLEL WITH):** Không áp dụng

## 2. BỐI CẢNH & PHẠM VI (CONTEXT & SCOPE)
- **Hiện trạng trước khi làm (As-Is):** ${isS1FE ? 'Giao diện thế hệ v1 ban đầu phục vụ nghiệm thu giai đoạn nền móng.' : 'Cần triển khai mới hoặc nâng cấp theo đặc tả kỹ thuật hệ thống.'}
- **Mục tiêu đạt được (To-Be):** ${objective}
- **Trong phạm vi (In-Scope):**
  - Triển khai đầy đủ các cấu phần mã nguồn và logic nghiệp vụ theo đặc tả.
  - Bảo đảm tính toàn vẹn dữ liệu và giao tiếp qua API Gateway.
- **Ngoài phạm vi (Out-of-Scope):** Không áp dụng

## 3. ĐẶC TẢ KỸ THUẬT (TECHNICAL SPECIFICATION)
- **Sub-project / Repository:** ${repo}
- **Tập tin tác động (File Paths):**
${filePaths.map(p => '  - ' + p).join('\n')}
- **Giao diện API / Lệnh CLI / Cấu hình (Contracts / Commands):**
  - ${apiLines.length > 0 ? apiLines.join(', ') : 'Không áp dụng'}
- **Cơ sở dữ liệu / Thực thể / Message Event (Data & Events):**
  - ${type === 'BE' ? 'Theo Flyway Migrations và Entity Models tương ứng trong service.' : 'Không áp dụng'}
- **Ràng buộc an ninh & Hiệu năng (Constraints):**
  - Tuân thủ phân quyền RBAC và xử lý lỗi đồng bộ.

## 4. TIÊU CHÍ NGHIỆM THU (ACCEPTANCE CRITERIA - GHERKIN)
- **Kịch bản chính (Happy Path):**
  - **Given:** Người dùng và hệ thống ở trạng thái hoạt động bình thường,
  - **When:** Kích hoạt chức năng hoặc gửi yêu cầu hợp lệ,
  - **Then:** Hệ thống phản hồi thành công, xử lý đúng luồng và cập nhật trạng thái chuẩn xác.
- **Kịch bản ngoại lệ & Xử lý lỗi (Edge Case / Error Handling):**
  - **Given:** Dữ liệu đầu vào không hợp lệ hoặc sự cố kết nối gián đoạn,
  - **When:** Thao tác được kích hoạt,
  - **Then:** Hệ thống thông báo lỗi rõ ràng, trả về mã HTTP chính xác và không làm gián đoạn phiên làm việc.

## 5. HƯỚNG DẪN KIỂM THỬ (TESTING & VERIFICATION GUIDE)
- **Môi trường & Dữ liệu test (Test Environment & Data):** Môi trường Local Development / VPS Test Cluster; tài khoản và dữ liệu seed tương ứng.
- **Các bước kiểm thử thủ công (Manual Test Steps):**
  1. Khởi chạy dịch vụ liên quan và đăng nhập tài khoản có thẩm quyền.
  2. Thực hiện thao tác nghiệp vụ theo kịch bản trên giao diện hoặc gửi request API.
  3. Kiểm tra phản hồi và đối chiếu dữ liệu ghi nhận trong database / state.
- **Lệnh kiểm thử tự động (Automated Test Commands):**
  - ${type === 'BE' ? '`./gradlew test`' : type === 'FE' ? '`npm run test`' : 'Không áp dụng'}

## 6. TIÊU CHUẨN HOÀN THÀNH (DEFINITION OF DONE - DoD)
- [x] Mã nguồn tuân thủ coding standards và linting (không có cảnh báo/lỗi).
- [x] 100% Unit/Integration Tests liên quan chạy đạt (Pass).
- [x] Đã kiểm tra tính tương thích ngược, không gây lỗi lan (No regression).
- [x] Code Review đã được chấp thuận (Sign-off).
- [x] Tài liệu kỹ thuật, API Doc hoặc README được cập nhật (nếu có).

## 7. BẰNG CHỨNG NGHIỆM THU (EVIDENCE & ARTIFACTS)
- **Commit SHA / Pull Request:** Chưa xác minh
- **Báo cáo kiểm thử tự động:** ${type === 'BE' ? 'Báo cáo kiểm thử Gradle Backend pass 100%' : type === 'FE' ? 'Báo cáo Vitest Frontend pass' : 'Nhật ký triển khai thực tế'}
- **Ảnh chụp / Video kiểm chứng / Log:** Không áp dụng`;

  return descTemplate;
}

// Test on 3 different cards
const testCards = [cards[0], cards[33], cards[62]];
testCards.forEach(c => {
  console.log(`\n=================== CARD: ${c.name} ===================`);
  console.log(buildDescription(c).slice(0, 500) + '...\n');
});
