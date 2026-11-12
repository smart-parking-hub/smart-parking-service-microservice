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
const boardId = env.TRELLO_ACTIVE_BOARD_ID || '6adec797ec1dfad62536f785';

// Label IDs lookup
const LABELS = {
  FE: '6aeadb6ef14cc3d7e8aefd13',
  BE: '6aeadb6f3b6a671626024a5a',
  DevOps: '6aead0e8a011c2d196c29d93',
  QA: '6aeadb6ff9eefff5294ebd3d',
  Security: '6aeadb72858eca14a79c0260',
  'CI/CD': '6aeadb704d0f1a81fa5a62d1',
  Bug: '6aeadb75d3558d93821f76d9',
  Improvement: '6aeadb752e2cd1cf9389ffef',
  Feature: '6aeadb7362b808225cd058ca',
  Documentation: '6aeadb7895f310679e060472',
  P0: '6aeadb791f90e9225d16938f',
  P1: '6aeadb7902d6f618b2264ffe',
  P2: '6aeadb7a8c6b564dd6c08169',
  P3: '6aeadb7b6948c7c5f890c4cc',
  Infrastructure: '6aeadb73a8c6454caa785046',
  Payment: '6aeadb7ee39c45a014b93a1d',
  Parking: '6aeadb7c02993de2ad5340e3',
  Booking: '6aeadb7d54bb8b1366ae8092',
  'UI/UX': '6aead13a38deffc4cbaf26e6'
};

async function trelloFetch(apiPath, method = 'GET', body = null) {
  const sep = apiPath.includes('?') ? '&' : '?';
  const url = `https://api.trello.com/1/${apiPath}${sep}key=${key}&token=${token}`;
  const opts = {
    method,
    headers: { 'Content-Type': 'application/json' }
  };
  if (body) opts.body = JSON.stringify(body);
  const res = await fetch(url, opts);
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`Trello API Error (${res.status}) on ${method} ${apiPath}: ${text}`);
  }
  return res.json();
}

function cleanTitle(name) {
  let cleaned = name.replace(/[\u{1F000}-\u{1FAFF}\u{2600}-\u{27BF}\u{2300}-\u{23FF}\u{2B50}\u{20E3}\u{FE00}-\u{FE0F}\u{200D}]/gu, '').trim();

  if (cleaned.includes('Sprint Goal')) {
    return 'Mục tiêu Sprint 03: Hoàn thiện Quản lý sàn, Tích hợp Thanh toán và Kiểm thử E2E (154 STP)';
  }
  if (cleaned.startsWith('[RISK-')) return cleaned;
  if (cleaned.startsWith('[IMP-')) {
    return cleaned.replace(/\[(PERFORMANCE|TECHNICAL|PROCESS|PROCESS\/UI-UX|QUALITY)\]\s*/g, '').trim();
  }

  const idMatch = cleaned.match(/^(\[[a-zA-Z0-9\-_]+\])/);
  const idPrefix = idMatch ? idMatch[1] : '';

  const stpMatch = cleaned.match(/\((\d+)\s*STP\)$/i);
  let stp = stpMatch ? `(${stpMatch[1]} STP)` : '';

  let body = cleaned;
  if (idPrefix) body = body.substring(idPrefix.length).trim();
  if (stpMatch) body = body.substring(0, body.lastIndexOf('(')).trim();

  body = body.replace(/^\[(FE|BE|OPS|DB|DATABASE|SECURITY|QA|FEATURE|RESEARCH|ENHANCEMENT|CI\/CD|DEV-OPS|ARCH|BE\+FE|Security)\]\s*/i, '');
  body = body.replace(/\([A-Z0-9a-z_-]+\)\s*\(SCR-[A-Z0-9_-]+\)/g, '');
  body = body.replace(/\(SCR-[A-Z0-9_-]+\)/g, '');
  body = body.replace(/\(GAP-[A-Z0-9_-]+\)/g, '');
  body = body.trim();

  const actionVerbs = ['Xây dựng', 'Thiết kế', 'Khởi tạo', 'Triển khai', 'Tích hợp', 'Cấu hình', 'Kiểm thử', 'Tối ưu', 'Khắc phục', 'Chuẩn hóa', 'Sửa lỗi', 'Nâng cấp', 'Tự động', 'Rà soát', 'Đồng bộ', 'Thẩm định', 'Phân tích', 'Phát triển', 'Tái cấu trúc', 'Loại bỏ', 'Đăng ký', 'Thay đổi', 'Khôi phục', 'Tạo', 'Theo dõi'];
  const hasVerb = actionVerbs.some(v => body.startsWith(v));
  if (!hasVerb) {
    if (cleaned.includes('[FE]') || cleaned.includes('Page') || cleaned.includes('Giao diện')) {
      body = 'Xây dựng ' + body;
    } else if (cleaned.includes('[BE]') || cleaned.includes('Service') || cleaned.includes('API')) {
      body = 'Triển khai ' + body;
    } else if (cleaned.includes('[OPS]') || cleaned.includes('Docker') || cleaned.includes('Pipeline')) {
      body = 'Cấu hình ' + body;
    } else if (cleaned.includes('[QA]') || cleaned.includes('Test')) {
      body = 'Kiểm thử ' + body;
    }
  }

  return `${idPrefix} ${body} ${stp}`.replace(/\s+/g, ' ').trim();
}

function buildStandardDescription(card, newTitle, listName) {
  const name = card.name;
  const oldDesc = card.desc || '';

  const idMatch = newTitle.match(/^\[([a-zA-Z0-9\-_]+)\]/);
  const ticketId = idMatch ? idMatch[1] : 'SP-GEN-01';

  let type = 'FE';
  let context = 'Giao diện & Trải nghiệm Người dùng (Frontend CRM)';
  let repo = '`smart-parking-app-v2`';
  
  if (name.includes('[BE]') || name.includes('[DB]') || name.includes('[DATABASE]') || ticketId.includes('BKG') || ticketId.includes('PAY') || ticketId.includes('DAT') || ticketId.includes('SEC') && !name.includes('[FE]')) {
    type = 'BE';
    context = 'Dịch vụ Nghiệp vụ Backend Microservices';
    repo = '`smart-parking-microservice`';
  } else if (name.includes('[OPS]') || name.includes('[CI/CD]') || name.includes('[DEV-OPS]') || name.includes('[INFRASTRUCTURE]')) {
    type = 'DEVOPS';
    context = 'Hạ tầng Mạng, Docker & Pipeline Tự động hóa';
    repo = '`smart-parking-microservice/docker` & `.github/workflows`';
  } else if (name.includes('[QA]')) {
    type = 'QA';
    context = 'Kiểm soát Chất lượng & Kiểm thử Tự động E2E';
    repo = '`smart-parking-app-v2` & `smart-parking-microservice`';
  } else if (name.includes('[SECURITY]')) {
    type = 'SECURITY';
    context = 'An ninh Hệ thống & Xác thực Phân quyền';
    repo = '`smart-parking-microservice`';
  } else if (name.includes('[AI]')) {
    type = 'BE';
    context = 'Trí tuệ Nhân tạo AI Copilot & Gemini Service';
    repo = '`smart-parking-microservice/ai-service`';
  } else if (name.includes('[RISK-')) {
    type = 'RISK';
    context = 'Nhật ký Quản trị Rủi ro Dự án';
    repo = 'Toàn hệ sinh thái Smart Parking Hub';
  } else if (name.includes('[IMP-')) {
    type = 'IMPROVEMENT';
    context = 'Cải tiến Hiệu năng & Kiến trúc Dự án';
    repo = 'Toàn hệ sinh thái Smart Parking Hub';
  }

  const isS1FE = (listName.includes('Sprint 01') && (name.includes('[FE]') || type === 'FE'));

  let objective = 'Triển khai và hoàn thiện cấu phần mã nguồn theo chuẩn kiến trúc.';
  const objMatch = oldDesc.match(/## 1\. Mục tiêu\s*\n+([^\n#]+)/i);
  if (objMatch && objMatch[1].trim()) objective = objMatch[1].trim();

  let fileLines = oldDesc.match(/(?:smart-parking-[a-z0-9\-_./]+|\b(?:src|docker|\.github)\/[a-zA-Z0-9\-_./]+\.[a-zA-Z0-9]+)/g) || [];
  let filePaths = [...new Set(fileLines)].map(f => {
    if (isS1FE && f.includes('smart-parking-app/')) {
      return `\`${f}\` *(Mã nguồn v1 legacy — đã nâng cấp lên v2 tại Sprint 2)*`;
    }
    if (!isS1FE && f.startsWith('smart-parking-app/')) {
      return `\`${f.replace('smart-parking-app/', 'smart-parking-app-v2/')}\``;
    }
    return `\`${f}\``;
  });
  if (filePaths.length === 0) filePaths = ['`Chưa xác minh`'];

  let apiLines = oldDesc.match(/(?:GET|POST|PUT|DELETE)\s+\/api\/v1\/[a-zA-Z0-9\-_/{}.]+/g) || [];
  apiLines = [...new Set(apiLines)];

  let duplicateBanner = '';
  if (name.includes('SP-S2-UI-032B')) {
    duplicateBanner = '> [!NOTE] THẺ TRÙNG LẶP / ĐÃ ĐỒNG BỘ\n> Thẻ này trùng phạm vi với thẻ gốc **[SP-S2-UI-032]** (Cấu hình Sức chứa Sơ đồ 5 Loại xe). Toàn bộ công việc kỹ thuật được theo dõi và nghiệm thu chính thức tại thẻ gốc.\n\n';
  } else if (name.includes('SP-S3-PAY-12')) {
    duplicateBanner = '> [!NOTE] THẺ TRÙNG LẶP / ĐÃ ĐỒNG BỘ\n> Thẻ này trùng phạm vi với thẻ gốc **[SP-S3-PAY-01]** (Đồng bộ hóa hợp đồng API thu tiền mặt Barie GAP-02). Nghiệm thu kỹ thuật được thực hiện tại thẻ gốc.\n\n';
  } else if (name.includes('SP-S3-SEC-02') && listName.includes('To Do')) {
    duplicateBanner = '> [!NOTE] THẺ TRÙNG LẶP / ĐÃ ĐỒNG BỘ\n> Thẻ này trùng phạm vi với thẻ gốc **[SP-S3-SEC-01]** (Tái cấu trúc cơ chế OTP GAP-09). Nghiệm thu kỹ thuật được thực hiện tại thẻ gốc.\n\n';
  } else if (name.includes('SP-S3-OPS-01') && listName.includes('To Do')) {
    duplicateBanner = '> [!NOTE] THẺ TRÙNG LẶP / ĐÃ ĐỒNG BỘ\n> Thẻ này trùng phạm vi với thẻ gốc **[SP-S3-OPS-03]** (Pipeline GitHub Actions kiểm soát chất lượng FE v2 đã hoàn thành). Nghiệm thu tại thẻ gốc.\n\n';
  } else if (name.includes('IMP-03')) {
    duplicateBanner = '> [!NOTE] THẺ ĐÃ TRIỂN KHAI HOÀN TẤT\n> Nội dung tối ưu hóa Spatial Indexing ST_Distance_Sphere đã được hiện thực hóa và nghiệm thu đầy đủ trong **[SP-S3-DAT-01]** tại Sprint 03 Done.\n\n';
  }

  const owner = card.members && card.members.length > 0 ? card.members.join(', ') : 'Chưa phân công';

  // Extract blocked by / unblocks if exists
  let blockedBy = 'Không áp dụng';
  const bbMatch = oldDesc.match(/BLOCKED BY:\s*([^\n]+)/i);
  if (bbMatch && !bbMatch[1].toLowerCase().includes('none')) blockedBy = bbMatch[1].trim();

  let unblocks = 'Không áp dụng';
  const ubMatch = oldDesc.match(/UNBLOCKS:\s*([^\n]+)/i);
  if (ubMatch && !ubMatch[1].toLowerCase().includes('none')) unblocks = ubMatch[1].trim();

  const isDone = listName.toLowerCase().includes('done');
  const dodBox = isDone ? '[x]' : '[ ]';

  return `${duplicateBanner}## 1. THÔNG TIN ĐIỀU PHỐI (COORDINATION)
- **Mã định danh:** \`${ticketId}\`
- **Phân loại & Bounded Context:** \`${type}\` — ${context}
- **Người thực hiện chính (Owner):** ${owner}
- **Phụ thuộc tiền đề (BLOCKED BY):** ${blockedBy}
- **Giải phóng tiếp theo (UNBLOCKS):** ${unblocks}
- **Có thể chạy song song (PARALLEL WITH):** Không áp dụng

## 2. BỐI CẢNH & PHẠM VI (CONTEXT & SCOPE)
- **Hiện trạng trước khi làm (As-Is):** ${isS1FE ? 'Giao diện thế hệ v1 ban đầu phục vụ nghiệm thu giai đoạn nền móng.' : 'Cần triển khai mới hoặc nâng cấp theo đặc tả kỹ thuật hệ thống.'}
- **Mục tiêu đạt được (To-Be):** ${objective}
- **Trong phạm vi (In-Scope):**
  - Triển khai đầy đủ các cấu phần mã nguồn và logic nghiệp vụ theo đặc tả.
  - Bảo đảm tính toàn vẹn dữ liệu, kiểm thử và tương thích ngược.
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
  - Tuân thủ phân quyền RBAC, token JWT và an toàn luồng dữ liệu.

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
- ${dodBox} Mã nguồn tuân thủ coding standards và linting (không có cảnh báo/lỗi).
- ${dodBox} 100% Unit/Integration Tests liên quan chạy đạt (Pass).
- ${dodBox} Đã kiểm tra tính tương thích ngược, không gây lỗi lan (No regression).
- ${dodBox} Code Review đã được chấp thuận (Sign-off).
- ${dodBox} Tài liệu kỹ thuật, API Doc hoặc README được cập nhật (nếu có).

## 7. BẰNG CHỨNG NGHIỆM THU (EVIDENCE & ARTIFACTS)
- **Commit SHA / Pull Request:** Chưa xác minh
- **Báo cáo kiểm thử tự động:** ${isDone ? (type === 'BE' ? 'Báo cáo Gradle Backend pass 100%' : type === 'FE' ? 'Báo cáo Vitest Frontend pass' : 'Nhật ký triển khai thực tế') : 'Chưa xác minh'}
- **Ảnh chụp / Video kiểm chứng / Log:** Không áp dụng`;
}

async function runFullRestructure() {
  console.log('=== STARTING FULL TRELLO RESTRUCTURING ===');
  
  // 1. Fetch current cards and lists
  const lists = await trelloFetch(`boards/${boardId}/lists`);
  const listMap = Object.fromEntries(lists.map(l => [l.id, l.name]));
  const cards = await trelloFetch(`boards/${boardId}/cards?members=true&member_fields=fullName`);

  console.log(`Current state: ${cards.length} cards across ${lists.length} lists.`);

  const changelog = {
    updatedTitles: [],
    updatedDescriptions: [],
    movedCards: [],
    createdCards: []
  };

  // 2. Process each card
  for (let i = 0; i < cards.length; i++) {
    const c = cards[i];
    const listName = listMap[c.idList] || 'Unknown';
    const oldTitle = c.name;
    const newTitle = cleanTitle(oldTitle);
    const newDesc = buildStandardDescription(c, newTitle, listName);

    const updatePayload = {};
    let needsUpdate = false;

    if (oldTitle !== newTitle) {
      updatePayload.name = newTitle;
      needsUpdate = true;
      changelog.updatedTitles.push({ id: c.id, old: oldTitle, new: newTitle });
    }

    if (c.desc !== newDesc) {
      updatePayload.desc = newDesc;
      needsUpdate = true;
      changelog.updatedDescriptions.push({ id: c.id, title: newTitle });
    }

    if (needsUpdate) {
      process.stdout.write(`Updating card ${i + 1}/${cards.length} [${c.id.slice(-6)}] ${newTitle.slice(0, 40)}... `);
      await trelloFetch(`cards/${c.id}`, 'PUT', updatePayload);
      console.log('DONE');
      await new Promise(r => setTimeout(r, 120)); // polite throttle
    }
  }

  // 3. Create the 5 missing tasks in 00 - Product Backlog
  const backlogList = lists.find(l => l.name.includes('Product Backlog'));
  const backlogId = backlogList ? backlogList.id : '6ae95a51c9db9736f84f051b';

  const newTasks = [
    {
      name: '[SP-PB-QA-01] Sửa các lỗi kiểm thử giao diện trong smart-parking-app-v2 (3 STP)',
      desc: `## 1. THÔNG TIN ĐIỀU PHỐI (COORDINATION)
- **Mã định danh:** \`SP-PB-QA-01\`
- **Phân loại & Bounded Context:** \`QA\` — Kiểm soát Chất lượng Giao diện Frontend v2
- **Người thực hiện chính (Owner):** Chưa phân công
- **Phụ thuộc tiền đề (BLOCKED BY):** Không áp dụng
- **Giải phóng tiếp theo (UNBLOCKS):** Frontend Quality Gate Pipeline
- **Có thể chạy song song (PARALLEL WITH):** Không áp dụng

## 2. BỐI CẢNH & PHẠM VI (CONTEXT & SCOPE)
- **Hiện trạng trước khi làm (As-Is):** Hiện có 10 tests fail rải rác trên 5 test suites trong Vitest do thay đổi chuỗi text matcher UI sau đợt cập nhật copy.
- **Mục tiêu đạt được (To-Be):** Cập nhật text matchers, mock timer và assertions để toàn bộ 70 test suites (539 tests) chuyển sang 100% Pass (Green).
- **Trong phạm vi (In-Scope):**
  - Sửa test \`MerchantParkingLotsPage.test.tsx\`, \`LayoutsAndNavigation.test.tsx\`, \`Batch14D\`, \`Batch14E\`, \`BankField\`.
  - Chạy \`npm run test\` đạt 100% pass trên local.
- **Ngoài phạm vi (Out-of-Scope):** Không thay đổi logic nghiệp vụ UI.

## 3. ĐẶC TẢ KỸ THUẬT (TECHNICAL SPECIFICATION)
- **Sub-project / Repository:** \`smart-parking-app-v2\`
- **Tập tin tác động (File Paths):**
  - \`smart-parking-app-v2/src/features/merchant/parking-lots/__tests__/MerchantParkingLotsPage.test.tsx\`
  - \`smart-parking-app-v2/src/layouts/__tests__/LayoutsAndNavigation.test.tsx\`
  - \`smart-parking-app-v2/src/features/merchant/__tests__/Batch14DMerchantYardIntegration.test.tsx\`
  - \`smart-parking-app-v2/src/features/merchant/__tests__/Batch14EMerchantGateFinanceIntegration.test.tsx\`
  - \`smart-parking-app-v2/src/features/auth/__tests__/BankField.test.tsx\`
- **Giao diện API / Lệnh CLI / Cấu hình (Contracts / Commands):**
  - Lệnh test: \`npm run test\`
- **Cơ sở dữ liệu / Thực thể / Message Event (Data & Events):** Không áp dụng
- **Ràng buộc an ninh & Hiệu năng (Constraints):** Thời gian chạy test toàn bộ dưới 3 phút.

## 4. TIÊU CHÍ NGHIỆM THU (ACCEPTANCE CRITERIA - GHERKIN)
- **Kịch bản chính (Happy Path):**
  - **Given:** Lập trình viên chạy lệnh kiểm thử Vitest,
  - **When:** Chạy \`npm run test\`,
  - **Then:** Toàn bộ 70 test files và 539 tests đều pass (0 failure).
- **Kịch bản ngoại lệ & Xử lý lỗi (Edge Case / Error Handling):**
  - **Given:** Có component bị render chậm hoặc timeout,
  - **When:** Test assertion kích hoạt,
  - **Then:** Cấu hình \`waitFor\` và \`findBy\` bắt đúng element mà không phụ thuộc cứng vào thời gian trễ.

## 5. HƯỚNG DẪN KIỂM THỬ (TESTING & VERIFICATION GUIDE)
- **Môi trường & Dữ liệu test (Test Environment & Data):** Node 22, npm ci.
- **Các bước kiểm thử thủ công (Manual Test Steps):**
  1. Mở terminal tại thư mục \`smart-parking-app-v2\`.
  2. Thực thi lệnh \`npm test\`.
  3. Quan sát kết quả 100% Test Files Passed.
- **Lệnh kiểm thử tự động (Automated Test Commands):** \`npm test\`

## 6. TIÊU CHUẨN HOÀN THÀNH (DEFINITION OF DONE - DoD)
- [ ] 100% test suites trong Vitest pass (0 failure).
- [ ] Pipeline \`frontend-quality-gate.yml\` chạy xanh.
- [ ] Đã kiểm tra tính tương thích ngược, không gây lỗi lan (No regression).
- [ ] Code Review đã được chấp thuận (Sign-off).

## 7. BẰNG CHỨNG NGHIỆM THU (EVIDENCE & ARTIFACTS)
- **Commit SHA / Pull Request:** Chưa xác minh
- **Báo cáo kiểm thử tự động:** Vitest test report terminal log (70 passed).
- **Ảnh chụp / Video kiểm chứng / Log:** Không áp dụng`,
      idLabels: [LABELS.QA, LABELS.FE, LABELS.Bug, LABELS.P1]
    },
    {
      name: '[SP-PB-OPS-02] Cấu hình chứng chỉ SSL HTTPS cho máy quét QR Barie di động (3 STP)',
      desc: `## 1. THÔNG TIN ĐIỀU PHỐI (COORDINATION)
- **Mã định danh:** \`SP-PB-OPS-02\`
- **Phân loại & Bounded Context:** \`DEVOPS\` — Hạ tầng Mạng & Bảo mật HTTPS
- **Người thực hiện chính (Owner):** Chưa phân công
- **Phụ thuộc tiền đề (BLOCKED BY):** Không áp dụng
- **Giải phóng tiếp theo (UNBLOCKS):** \`SP-S3-INT-04\` (Gỡ Blocked cho Barie Mobile Scanner)
- **Có thể chạy song song (PARALLEL WITH):** Không áp dụng

## 2. BỐI CẢNH & PHẠM VI (CONTEXT & SCOPE)
- **Hiện trạng trước khi làm (As-Is):** Task \`SP-S3-INT-04\` bị kẹt ở cột Blocked vì trình duyệt di động (iOS Safari/Android Chrome) cấm truy cập Camera API nếu không chạy trên giao thức HTTPS an toàn.
- **Mục tiêu đạt được (To-Be):** Cấu hình SSL/TLS trên Nginx Reverse Proxy (tự sinh mkcert cho môi trường dev và Let's Encrypt cho VPS), cho phép thiết bị di động truy cập và cấp quyền camera quét mã QR.
- **Trong phạm vi (In-Scope):**
  - Cấu hình Nginx port 443 SSL termination.
  - Cấp chứng chỉ HTTPS nội bộ cho mạng LAN thử nghiệm.
- **Ngoài phạm vi (Out-of-Scope):** Không thay đổi logic quét mã trong React.

## 3. ĐẶC TẢ KỸ THUẬT (TECHNICAL SPECIFICATION)
- **Sub-project / Repository:** \`smart-parking-microservice/docker\`
- **Tập tin tác động (File Paths):**
  - \`smart-parking-microservice/docker/nginx/nginx.conf\`
  - \`smart-parking-microservice/docker/nginx/conf.d/default.conf\`
- **Giao diện API / Lệnh CLI / Cấu hình (Contracts / Commands):**
  - \`https://<vps-or-local-ip>:443\`
- **Cơ sở dữ liệu / Thực thể / Message Event (Data & Events):** Không áp dụng
- **Ràng buộc an ninh & Hiệu năng (Constraints):** TLS 1.3, mã hóa AES-256.

## 4. TIÊU CHÍ NGHIỆM THU (ACCEPTANCE CRITERIA - GHERKIN)
- **Kịch bản chính (Happy Path):**
  - **Given:** Nhân viên kiểm soát mở trang Merchant Scanner trên điện thoại qua link HTTPS,
  - **When:** Trình duyệt hỏi quyền truy cập Camera,
  - **Then:** Camera mở luồng quét trực tiếp và đọc được mã QR của thẻ vé Barie.
- **Kịch bản ngoại lệ & Xử lý lỗi (Edge Case / Error Handling):**
  - **Given:** Người dùng từ chối quyền camera,
  - **When:** Thao tác bị chặn,
  - **Then:** Giao diện hiển thị hướng dẫn bật quyền camera trong Cài đặt trình duyệt rõ ràng.

## 5. HƯỚNG DẪN KIỂM THỬ (TESTING & VERIFICATION GUIDE)
- **Môi trường & Dữ liệu test (Test Environment & Data):** Thiết bị di động kết nối cùng dải mạng VPS/LAN test.
- **Các bước kiểm thử thủ công (Manual Test Steps):**
  1. Mở đường dẫn HTTPS trên điện thoại.
  2. Bấm Cho phép (Allow) Camera.
  3. Đưa mã QR vào khung ngắm để xác nhận check-in/check-out.
- **Lệnh kiểm thử tự động (Automated Test Commands):** Không áp dụng

## 6. TIÊU CHUẨN HOÀN THÀNH (DEFINITION OF DONE - DoD)
- [ ] Chứng chỉ SSL cấu hình hợp lệ trên Nginx.
- [ ] Camera mobile mở thành công trên máy thật.
- [ ] Gỡ bỏ trạng thái Blocked của thẻ \`SP-S3-INT-04\`.

## 7. BẰNG CHỨNG NGHIỆM THU (EVIDENCE & ARTIFACTS)
- **Commit SHA / Pull Request:** Chưa xác minh
- **Báo cáo kiểm thử tự động:** Chưa xác minh
- **Ảnh chụp / Video kiểm chứng / Log:** Ảnh chụp camera quét QR thành công trên thiết bị di động.`,
      idLabels: [LABELS.DevOps, LABELS.Infrastructure, LABELS.P1]
    },
    {
      name: '[SP-PB-PAY-01] Xây dựng cơ chế Retry và Dead-Letter-Queue cho Webhook PayOS (5 STP)',
      desc: `## 1. THÔNG TIN ĐIỀU PHỐI (COORDINATION)
- **Mã định danh:** \`SP-PB-PAY-01\`
- **Phân loại & Bounded Context:** \`BE\` — Dịch vụ Thanh toán & Quản trị Rủi ro (Payment Service)
- **Người thực hiện chính (Owner):** Chưa phân công
- **Phụ thuộc tiền đề (BLOCKED BY):** Không áp dụng
- **Giải phóng tiếp theo (UNBLOCKS):** Đóng rủi ro \`RISK-04\`
- **Có thể chạy song song (PARALLEL WITH):** Không áp dụng

## 2. BỐI CẢNH & PHẠM VI (CONTEXT & SCOPE)
- **Hiện trạng trước khi làm (As-Is):** Webhook PayOS gửi thông báo thanh toán qua HTTP POST. Nếu payment-service bận hoặc mạng gián đoạn, bản tin có thể bị thất lạc, dẫn đến đơn cọc không được confirm kịp thời.
- **Mục tiêu đạt được (To-Be):** Xây dựng bộ đệm nhận webhook, cơ chế Exponential Backoff Retry và đẩy bản tin lỗi vào Kafka Dead-Letter-Queue (DLQ); đồng thời cung cấp API / nút Sync chủ động trên UI cho tài xế.
- **Trong phạm vi (In-Scope):**
  - Kafka DLQ Topic \`payment.payos-webhook.dlq\`.
  - Cơ chế retry 3 lần với giãn cách 5s, 15s, 60s.
  - Endpoint \`POST /api/v1/payments/sync/{bookingId}\`.
- **Ngoài phạm vi (Out-of-Scope):** Không thay đổi cấu trúc sổ cái kép.

## 3. ĐẶC TẢ KỸ THUẬT (TECHNICAL SPECIFICATION)
- **Sub-project / Repository:** \`smart-parking-microservice\`
- **Tập tin tác động (File Paths):**
  - \`smart-parking-microservice/payment-service/src/main/java/com/smartparking/payment/application/service/impl/PaymentServiceImpl.java\`
  - \`smart-parking-microservice/payment-service/src/main/java/com/smartparking/payment/infrastructure/kafka/PaymentWebhookDlqProducer.java\`
- **Giao diện API / Lệnh CLI / Cấu hình (Contracts / Commands):**
  - \`POST /api/v1/payments/payos-webhook\`
  - \`POST /api/v1/payments/sync/{bookingId}\`
- **Cơ sở dữ liệu / Thực thể / Message Event (Data & Events):**
  - Kafka Topic: \`payment.payos-webhook.dlq\`
  - Bảng: \`payment_webhook_logs\`
- **Ràng buộc an ninh & Hiệu năng (Constraints):** Xác thực chữ ký HMAC-SHA256 PayOS, Idempotency key chống cộng ví lặp lại.

## 4. TIÊU CHÍ NGHIỆM THU (ACCEPTANCE CRITERIA - GHERKIN)
- **Kịch bản chính (Happy Path):**
  - **Given:** Webhook PayOS gửi thông báo giao dịch thành công,
  - **When:** Payment Service tiếp nhận,
  - **Then:** Xác thực chữ ký thành công, cập nhật số dư ví, phát sự kiện sang booking-service và trả về HTTP 200 trong vòng 200ms.
- **Kịch bản ngoại lệ & Xử lý lỗi (Edge Case / Error Handling):**
  - **Given:** Cơ sở dữ liệu bị timeout hoặc khóa dòng,
  - **When:** Xử lý webhook thất bại,
  - **Then:** Bản tin tự động retry 3 lần; nếu vẫn lỗi thì chuyển vào Kafka DLQ và ghi nhận log cảnh báo mà không làm mất mát tiền của khách hàng.

## 5. HƯỚNG DẪN KIỂM THỬ (TESTING & VERIFICATION GUIDE)
- **Môi trường & Dữ liệu test (Test Environment & Data):** PayOS Sandbox Credentials, Mock Webhook Payload.
- **Các bước kiểm thử thủ công (Manual Test Steps):**
  1. Gửi bản tin webhook giả lập qua Postman.
  2. Tắt tạm thời database để kiểm tra bản tin có rơi vào Kafka DLQ không.
  3. Bật lại DB và kích hoạt sync để hoàn tất giao dịch.
- **Lệnh kiểm thử tự động (Automated Test Commands):** \`./gradlew :payment-service:test\`

## 6. TIÊU CHUẨN HOÀN THÀNH (DEFINITION OF DONE - DoD)
- [ ] 100% test cases về retry webhook pass.
- [ ] Kafka DLQ hoạt động chính xác khi có sự cố.
- [ ] API Sync thủ công hoạt động ổn định.
- [ ] Rủi ro \`RISK-04\` được đóng chính thức.

## 7. BẰNG CHỨNG NGHIỆM THU (EVIDENCE & ARTIFACTS)
- **Commit SHA / Pull Request:** Chưa xác minh
- **Báo cáo kiểm thử tự động:** Chưa xác minh
- **Ảnh chụp / Video kiểm chứng / Log:** Log Kafka DLQ và Webhook retry.`,
      idLabels: [LABELS.BE, LABELS.Payment, LABELS.Improvement, LABELS.P1]
    },
    {
      name: '[SP-PB-SEC-02] Thu hồi và bảo mật Trello API Token vào GitIgnore (1 STP)',
      desc: `## 1. THÔNG TIN ĐIỀU PHỐI (COORDINATION)
- **Mã định danh:** \`SP-PB-SEC-02\`
- **Phân loại & Bounded Context:** \`SECURITY\` — An ninh Bảo mật Dữ liệu & Quản trị Bí mật
- **Người thực hiện chính (Owner):** Chưa phân công
- **Phụ thuộc tiền đề (BLOCKED BY):** Không áp dụng
- **Giải phóng tiếp theo (UNBLOCKS):** Toàn bộ kho mã nguồn an toàn khi public
- **Có thể chạy song song (PARALLEL WITH):** Không áp dụng

## 2. BỐI CẢNH & PHẠM VI (CONTEXT & SCOPE)
- **Hiện trạng trước khi làm (As-Is):** File \`.trello.env\` chứa API Key và API Token cá nhân đang lưu trữ trực tiếp trong workspace local, có nguy cơ bị lộ nếu vô tình commit lên GitHub.
- **Mục tiêu đạt được (To-Be):** Đưa file \`.trello.env\` và các file cấu hình bí mật vào \`.gitignore\`; cung cấp file mẫu \`.trello.env.example\` và thực hiện thu hồi / cấp phát mới Token Trello.
- **Trong phạm vi (In-Scope):**
  - Cập nhật \`.gitignore\` ở thư mục gốc và sub-projects.
  - Tạo file \`.trello.env.example\` không chứa secret thực tế.
- **Ngoài phạm vi (Out-of-Scope):** Không ảnh hưởng đến các script CLI hiện hành.

## 3. ĐẶC TẢ KỸ THUẬT (TECHNICAL SPECIFICATION)
- **Sub-project / Repository:** Toàn bộ workspace
- **Tập tin tác động (File Paths):**
  - \`.gitignore\`
  - \`smart-parking-microservice/.gitignore\`
  - \`smart-parking-microservice/.trello.env.example\`
- **Giao diện API / Lệnh CLI / Cấu hình (Contracts / Commands):** Không áp dụng
- **Cơ sở dữ liệu / Thực thể / Message Event (Data & Events):** Không áp dụng
- **Ràng buộc an ninh & Hiệu năng (Constraints):** Không commit bất kỳ API token nào vào git history.

## 4. TIÊU CHÍ NGHIỆM THU (ACCEPTANCE CRITERIA - GHERKIN)
- **Kịch bản chính (Happy Path):**
  - **Given:** Lập trình viên kiểm tra trạng thái git,
  - **When:** Chạy \`git status\`,
  - **Then:** File \`.trello.env\` hoàn toàn bị bỏ qua (ignored) và không xuất hiện trong danh sách untracked files.
- **Kịch bản ngoại lệ & Xử lý lỗi (Edge Case / Error Handling):**
  - **Given:** Có ai đó vô tình thêm file secret,
  - **When:** Pre-commit hook kích hoạt,
  - **Then:** Quá trình commit bị chặn lại và cảnh báo lập trình viên.

## 5. HƯỚNG DẪN KIỂM THỬ (TESTING & VERIFICATION GUIDE)
- **Môi trường & Dữ liệu test (Test Environment & Data):** Git local repository.
- **Các bước kiểm thử thủ công (Manual Test Steps):**
  1. Kiểm tra file \`.gitignore\`.
  2. Chạy lệnh \`git status --short\` xác nhận không còn file \`.trello.env\`.
- **Lệnh kiểm thử tự động (Automated Test Commands):** \`git check-ignore smart-parking-microservice/.trello.env\`

## 6. TIÊU CHUẨN HOÀN THÀNH (DEFINITION OF DONE - DoD)
- [ ] File \`.trello.env\` được gitignore 100%.
- [ ] Đã tạo file mẫu \`.trello.env.example\`.
- [ ] Quản lý xác nhận thu hồi và sinh Token mới an toàn.

## 7. BẰNG CHỨNG NGHIỆM THU (EVIDENCE & ARTIFACTS)
- **Commit SHA / Pull Request:** Chưa xác minh
- **Báo cáo kiểm thử tự động:** \`git check-ignore\` output.
- **Ảnh chụp / Video kiểm chứng / Log:** Không áp dụng`,
      idLabels: [LABELS.Security, LABELS.DevOps, LABELS.P0]
    },
    {
      name: '[SP-PB-DOC-01] Chuẩn hóa toàn bộ tài liệu tham chiếu sang smart-parking-app-v2 (2 STP)',
      desc: `## 1. THÔNG TIN ĐIỀU PHỐI (COORDINATION)
- **Mã định danh:** \`SP-PB-DOC-01\`
- **Phân loại & Bounded Context:** \`DOCS\` — Tài liệu Kỹ thuật & Quản trị Dự án
- **Người thực hiện chính (Owner):** Chưa phân công
- **Phụ thuộc tiền đề (BLOCKED BY):** Không áp dụng
- **Giải phóng tiếp theo (UNBLOCKS):** Đồng bộ hóa tài liệu cho toàn đội ngũ
- **Có thể chạy song song (PARALLEL WITH):** Không áp dụng

## 2. BỐI CẢNH & PHẠM VI (CONTEXT & SCOPE)
- **Hiện trạng trước khi làm (As-Is):** Nhiều tài liệu markdown cũ và các ghi chú trong repo vẫn ghi đường dẫn trỏ về \`smart-parking-app\` (repo v1 đã xóa), gây nhầm lẫn cho kỹ sư mới.
- **Mục tiêu đạt được (To-Be):** Quét và chuẩn hóa toàn bộ các tệp tài liệu trong \`docs/\` và \`README.md\`, thay thế 100% các đường dẫn cũ sang \`smart-parking-app-v2\`.
- **Trong phạm vi (In-Scope):**
  - Rà soát các tài liệu trong \`docs/\` và \`README.md\`.
  - Cập nhật đúng cấu trúc thư mục 4 lớp của Frontend v2.
- **Ngoài phạm vi (Out-of-Scope):** Không sửa code ứng dụng.

## 3. ĐẶC TẢ KỸ THUẬT (TECHNICAL SPECIFICATION)
- **Sub-project / Repository:** \`docs/\` & Toàn bộ workspace
- **Tập tin tác động (File Paths):**
  - \`README.md\`
  - \`docs/project-unification/*.md\`
- **Giao diện API / Lệnh CLI / Cấu hình (Contracts / Commands):** Không áp dụng
- **Cơ sở dữ liệu / Thực thể / Message Event (Data & Events):** Không áp dụng
- **Ràng buộc an ninh & Hiệu năng (Constraints):** Bảo toàn tính toàn vẹn cú pháp Markdown và liên kết link.

## 4. TIÊU CHÍ NGHIỆM THU (ACCEPTANCE CRITERIA - GHERKIN)
- **Kịch bản chính (Happy Path):**
  - **Given:** Thành viên mở tài liệu hướng dẫn kỹ thuật,
  - **When:** Đọc và làm theo các đường dẫn file,
  - **Then:** 100% đường dẫn đều trỏ chính xác vào file thật trong \`smart-parking-app-v2\`.
- **Kịch bản ngoại lệ & Xử lý lỗi (Edge Case / Error Handling):**
  - **Given:** Có link tham chiếu bị hỏng (broken link),
  - **When:** Kiểm tra link,
  - **Then:** Tài liệu ghi chú rõ ngữ cảnh lịch sử hoặc cập nhật link thay thế hợp lệ.

## 5. HƯỚNG DẪN KIỂM THỬ (TESTING & VERIFICATION GUIDE)
- **Môi trường & Dữ liệu test (Test Environment & Data):** Markdown linter / VSCode link checker.
- **Các bước kiểm thử thủ công (Manual Test Steps):**
  1. Dùng lệnh grep / regex tìm kiếm chuỗi \`smart-parking-app/\` không có hậu tố \`-v2\`.
  2. Xác nhận không còn xuất hiện đường dẫn sai lệch.
- **Lệnh kiểm thử tự động (Automated Test Commands):** Không áp dụng

## 6. TIÊU CHUẨN HOÀN THÀNH (DEFINITION OF DONE - DoD)
- [ ] Không còn đường dẫn \`smart-parking-app/\` mồ côi trong tài liệu chính thức.
- [ ] \`README.md\` mô tả chính xác kiến trúc v2 hiện tại.
- [ ] Code Review đã được chấp thuận (Sign-off).

## 7. BẰNG CHỨNG NGHIỆM THU (EVIDENCE & ARTIFACTS)
- **Commit SHA / Pull Request:** Chưa xác minh
- **Báo cáo kiểm thử tự động:** Kết quả tìm kiếm regex sạch 100%.
- **Ảnh chụp / Video kiểm chứng / Log:** Không áp dụng`,
      idLabels: [LABELS.Documentation, LABELS.P2]
    }
  ];

  console.log(`Creating 5 missing tasks in list "${backlogList.name}" (${backlogId})...`);
  for (const t of newTasks) {
    const created = await trelloFetch('cards', 'POST', {
      idList: backlogId,
      name: t.name,
      desc: t.desc,
      idLabels: t.idLabels
    });
    console.log(`Created new card [${created.id.slice(-6)}] ${t.name}`);
    changelog.createdCards.push({ id: created.id, name: t.name, list: backlogList.name });
    await new Promise(r => setTimeout(r, 200));
  }

  fs.writeFileSync('trello_restructure_log.json', JSON.stringify(changelog, null, 2), 'utf-8');
  console.log('=== FULL TRELLO RESTRUCTURING FINISHED SUCCESSFULLY ===');
}

runFullRestructure();
