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

const batchBCards = [
  // 04. Ready for Sprint (3 cards)
  { id: '6aeaddb301b0f862ece0feb8', name: '[SP-S3-PRC-02] [FEATURE] Cơ chế định giá đỗ xe thông minh theo công suất sử dụng (Dynamic Pricing)' },
  { id: '6aeaddb42e21b05346278b65', name: '[SP-S3-UI-22] [ENHANCEMENT] Bản đồ nhiệt trực quan hóa mật độ đỗ xe toàn khu vực (Heatmap Density)' },
  { id: '6aeaddb5dd17530efb924ec8', name: '[SP-S3-OPS-05] [INFRASTRUCTURE] Triển khai Kubernetes Cluster và Helm Charts cho Microservices' },

  // 02. Product Backlog (4 cards)
  { id: '6aeaddae23fc37f6a2b5cdae', name: '[SP-PB-OPS-01] [FEATURE] Mở rộng tích hợp IoT Gate Controller và cảm biến siêu âm khoang đỗ' },
  { id: '6aeaddb0bad3be7df80e0064', name: '[SP-PB-ANPR-01] [FEATURE] Tích hợp module camera nhận diện biển số tự động ALPR' },
  { id: '6aeaddb16df1d8839161daa6', name: '[SP-PB-SEC-01] [FEATURE] Tích hợp cổng tin nhắn SMS Brandname gửi mã xác thực OTP' },
  { id: '6aeaddb2c0dd0ac705ad4b00', name: '[SP-PB-UI-01] [RESEARCH] Khảo sát và thiết kế ứng dụng di động dành cho Chủ bãi xe' }
];

async function updateBatchB() {
  console.log(`Bắt đầu chuẩn hóa BATCH B (${batchBCards.length} thẻ trong Ready for Sprint và Product Backlog)...`);
  for (let i = 0; i < batchBCards.length; i++) {
    const card = batchBCards[i];
    const url = `https://api.trello.com/1/cards/${card.id}?key=${key}&token=${token}`;
    try {
      const res = await fetch(url, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: card.name })
      });
      if (res.ok) {
        console.log(`[${i + 1}/${batchBCards.length}] ✅ Đã cập nhật: ${card.name}`);
      } else {
        console.error(`[${i + 1}/${batchBCards.length}] ❌ Lỗi thẻ ${card.id}:`, await res.text());
      }
    } catch (e) {
      console.error(`Lỗi mạng:`, e.message);
    }
    await new Promise(r => setTimeout(r, 120));
  }
  console.log('\n🎉 HOÀN TẤT BATCH B!');
}

updateBatchB();
