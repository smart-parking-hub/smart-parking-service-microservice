import fs from 'node:fs';

const snapshot = JSON.parse(fs.readFileSync('before_restructure_snapshot.json', 'utf-8'));
const cards = snapshot.cards;

console.log('Preparing standardized titles for all cards...');

// Helper to clean title
function standardizeTitle(name) {
  // Remove emoji
  let cleaned = name.replace(/[\u{1F000}-\u{1FAFF}\u{2600}-\u{27BF}\u{2300}-\u{23FF}\u{2B50}\u{20E3}\u{FE00}-\u{FE0F}\u{200D}]/gu, '').trim();

  // Handle Sprint Goal
  if (cleaned.includes('Sprint Goal')) {
    return 'Mục tiêu Sprint 03: Hoàn thiện Quản lý sàn, Tích hợp Thanh toán và Kiểm thử E2E (154 STP)';
  }

  // Handle Risks
  if (cleaned.startsWith('[RISK-')) {
    // Keep clean format e.g. [RISK-01] Rủi ro đứt gãy luồng thanh toán Barie do lệch DTO CheckInRequest
    return cleaned;
  }

  // Handle Improvements
  if (cleaned.startsWith('[IMP-')) {
    // Replace [PERFORMANCE], [TECHNICAL], [PROCESS] tags
    let title = cleaned.replace(/\[(PERFORMANCE|TECHNICAL|PROCESS|PROCESS\/UI-UX|QUALITY)\]\s*/g, '');
    return title.trim();
  }

  // Handle Standard Tickets [SP-...]
  // Extract ID e.g. [SP-S1-UI-01]
  const idMatch = cleaned.match(/^(\[[a-zA-Z0-9\-_]+\])/);
  const idPrefix = idMatch ? idMatch[1] : '';

  // Extract STP at end
  const stpMatch = cleaned.match(/\((\d+)\s*STP\)$/i);
  let stp = stpMatch ? `(${stpMatch[1]} STP)` : '';

  // Body text without prefix and STP
  let body = cleaned;
  if (idPrefix) body = body.substring(idPrefix.length).trim();
  if (stpMatch) body = body.substring(0, body.lastIndexOf('(')).trim();

  // Remove tag like [FE], [BE], [OPS], [DB], [DATABASE], [SECURITY], [QA], [FEATURE], [RESEARCH], [ENHANCEMENT]
  body = body.replace(/^\[(FE|BE|OPS|DB|DATABASE|SECURITY|QA|FEATURE|RESEARCH|ENHANCEMENT|CI\/CD|DEV-OPS|ARCH|BE\+FE|Security)\]\s*/i, '');
  
  // Remove internal screen tags in body like (LandingPage) (SCR-001), (LoginPage) (SCR-010)
  body = body.replace(/\([A-Z0-9a-z_-]+\)\s*\(SCR-[A-Z0-9_-]+\)/g, '');
  body = body.replace(/\(SCR-[A-Z0-9_-]+\)/g, '');
  body = body.replace(/\(GAP-[A-Z0-9_-]+\)/g, '');
  body = body.trim();

  // If body doesn't start with action verb, add appropriate verb
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

  // Assemble clean concise title
  const finalTitle = `${idPrefix} ${body} ${stp}`.replace(/\s+/g, ' ').trim();
  return finalTitle;
}

const titleMappings = cards.map(c => ({
  id: c.id,
  oldName: c.name,
  newName: standardizeTitle(c.name),
  listName: c.listName
}));

fs.writeFileSync('title_mappings.json', JSON.stringify(titleMappings, null, 2), 'utf-8');

console.log('Sample title transformations:');
titleMappings.slice(0, 15).forEach(m => {
  console.log(`OLD: ${m.oldName}`);
  console.log(`NEW: ${m.newName}\n`);
});
