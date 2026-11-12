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

const updates = [
  {
    id: "6aeae19a95f310679e11308e",
    name: "[SP-S2-QA-01] [QA] Kiểm thử luồng quét QR Barie Check-in/Out & Thu tiền mặt (3 STP)"
  },
  {
    id: "6aed2a436f830c7a8798de07",
    name: "[SP-S2-UI-054] [FE] Tối ưu hóa Hiệu năng Web Vitals (LCP, INP, CLS) & Chuẩn hóa SEO On-page (8 STP)"
  },
  {
    id: "6aeae1984fd9955375ef4495",
    name: "[SP-S2-QA-02] [QA] Kiểm thử tích hợp luồng tạo bãi đỗ mới & Kiểm duyệt hồ sơ (5 STP)"
  },
  {
    id: "6af039e189974295d07e2bd3",
    name: "[SP-S2-ANPR-01] [FE] Nâng cấp Barie Gate QR Scanner Check-in/Check-out (3 STP)"
  },
  {
    id: "6aeb22bbfa0cb8e717a89feb",
    name: "[SP-S2-AI-01] [BE] Tích hợp RAG Chính sách & Bảng giá riêng từng Bãi xe (Dynamic Policy RAG) (3 STP)"
  },
  {
    id: "6aeb2402fd4829d28a8839cf",
    name: "[SP-S2-AI-02] [FE] Tích hợp Ngữ cảnh Bãi xe & Chatbot Widget (AiChatWidget) (3 STP)"
  },
  {
    id: "6aeae1b3c33d77b1e3f54582",
    name: "[SP-S2-AI-03] [BE] Cấu hình môi trường & Kích hoạt Gemini API cho AI Copilot (3 STP)"
  },
  {
    id: "6aeb245a3c2cb8d1ee816085",
    name: "[SP-S2-AI-04] [BE] Mở rộng AI Generator với Custom Highlights & Smart Fallback Template (5 STP)"
  },
  {
    id: "6aeb3ff06863085cc711105e",
    name: "[SP-S2-AI-05] [FE] Tích hợp AI Copilot Chatbot & Đấu nối API sinh mô tả bãi xe thật (5 STP)"
  },
  {
    id: "6aeb2497162d7303d78dc3ba",
    name: "[SP-S2-AI-06] [FE] Bổ sung Ô nhập Từ khóa & Tags Điểm nhấn (GenerateDescriptionModal) (3 STP)"
  },
  {
    id: "6aeafc2c30b544ecf8649387",
    name: "[SP-S2-SEC-01] [OPS] Khóa toàn bộ cổng public 0.0.0.0 & Thiết lập VPN WireGuard/Tailscale VPS (8 STP)"
  },
  {
    id: "6aeafba1f576e712ba6f00b0",
    name: "[SP-S2-OPS-01] [OPS] Xử lý rủi ro lộ cổng dịch vụ & Hạ tầng Prometheus/Grafana (RISK-07) (5 STP)"
  },
  {
    id: "6aeafb98ab5f0d40a9cad3a4",
    name: "[SP-S2-OPS-02] [OPS] Xử lý rủi ro cạn kiệt tài nguyên (OOM) & Phân mảnh mạng VPS (RISK-06) (8 STP)"
  },
  {
    id: "6aeae196d4022c07ef35fe57",
    name: "[SP-S2-UI-032B] [FE] Thiết lập Cấu hình Sức chứa Slot theo Loại xe (SCR-032) (5 STP)"
  },
  {
    id: "6aeae1973433e4c83abfab16",
    name: "[SP-S2-UI-033] [FE] Cấu hình Biểu phí Đỗ xe Lũy tiến (MerchantPricingPage) (SCR-033) (5 STP)"
  },
  {
    id: "6aec368e8ac346ffc843a59f",
    name: "[SP-S2-UI-055] [FE] Chuẩn hóa Design Tokens Tailwind CSS v4, Loại bỏ mã màu Hex (2 STP)"
  },
  {
    id: "6aec368b386d37e03f4ec86a",
    name: "[SP-S2-UI-056] [FE] Loại bỏ mã Screen nội bộ trên toàn bộ Button, Banner & Microcopy (3 STP)"
  },
  {
    id: "6aec368c3c1c68a765833908",
    name: "[SP-S2-UI-057] [FE] Chuẩn hóa Accessible Name, Form IDs & Label theo WCAG 2.1 (3 STP)"
  }
];

async function run() {
  console.log(`Bắt đầu cập nhật BATCH D (${updates.length} thẻ trong 10. Sprint 02 Done)...`);
  for (let i = 0; i < updates.length; i++) {
    const card = updates[i];
    const url = `https://api.trello.com/1/cards/${card.id}?key=${key}&token=${token}`;
    try {
      const res = await fetch(url, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: card.name })
      });
      if (res.ok) {
        console.log(`[${i + 1}/${updates.length}] OK: ${card.name}`);
      } else {
        const text = await res.text();
        console.error(`[${i + 1}/${updates.length}] FAIL ${card.id}: ${res.status} - ${text}`);
      }
      await new Promise(r => setTimeout(r, 150));
    } catch (e) {
      console.error(`Error updating ${card.id}:`, e.message);
    }
  }
  console.log("Hoàn thành Batch D!");
}

run();
