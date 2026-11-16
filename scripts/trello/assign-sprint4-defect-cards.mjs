import { key, token, MEMBERS } from './trello-client.mjs';

const ASSIGNMENTS = {
  // Huỳnh La Tiến Lộc: Payment & Checkout
  [MEMBERS.LOC]: [
    'SP-PAY-01',
    'SP-CHECKOUT-01',
    'SP-CHECKOUT-03',
    'SP-WITHDRAW-01'
  ],
  // Hồ Quốc Khải: Booking, Vehicle & Media
  [MEMBERS.KHAI]: [
    'SP-BOOK-01',
    'SP-TICKET-01',
    'SP-VEH-01',
    'SP-VEH-02',
    'SP-VEH-03',
    'SP-MEDIA-01'
  ],
  // Lâm Gia Huy: Map, UI & Driver Checkout
  [MEMBERS.HUY]: [
    'SP-MAP-01',
    'SP-MAP-02',
    'SP-UI-01',
    'SP-UI-02',
    'SP-UI-03',
    'SP-CHECKOUT-02',
    'SP-CHECKOUT-04'
  ],
  // Hoàng Đình Tùng: Auth, AI & Notification
  [MEMBERS.TUNG]: [
    'SP-AUTH-01',
    'SP-AUTH-02',
    'SP-AUTH-03',
    'SP-AUTH-04',
    'SP-AUTH-05',
    'SP-LOT-01',
    'SP-NOTI-01'
  ]
};

const MEMBER_NAMES = {
  [MEMBERS.LOC]: 'Huỳnh La Tiến Lộc',
  [MEMBERS.KHAI]: 'Hồ Quốc Khải',
  [MEMBERS.HUY]: 'Lâm Gia Huy',
  [MEMBERS.TUNG]: 'Hoàng Đình Tùng'
};

const LIST_ID = '6af80755e8b450a479eac591'; // Sprint 04 - Backlog

async function assignMembers() {
  console.log('🚀 Bắt đầu gán thành viên cho 24 thẻ lỗi kỹ thuật trong Sprint 04 - Backlog theo phân bổ Sprint Goal...\n');

  const res = await fetch(`https://api.trello.com/1/lists/${LIST_ID}/cards?fields=name,idMembers&key=${key}&token=${token}`);
  const cards = await res.json();

  let assignedCount = 0;

  for (const [memberId, prefixes] of Object.entries(ASSIGNMENTS)) {
    const memberName = MEMBER_NAMES[memberId];
    console.log(`\n📌 Đang gán cho thành viên: ${memberName} (${prefixes.length} thẻ)...`);

    for (const prefix of prefixes) {
      const card = cards.find(c => c.name.includes(`[${prefix}]`));
      if (!card) {
        console.error(`   ⚠️ Không tìm thấy thẻ có mã ${prefix}!`);
        continue;
      }

      // Kiểm tra nếu đã có member này chưa
      if (card.idMembers && card.idMembers.includes(memberId)) {
        console.log(`   ℹ️ Thẻ ${prefix} đã được gán cho ${memberName}. Bỏ qua.`);
        continue;
      }

      const postUrl = `https://api.trello.com/1/cards/${card.id}/idMembers?value=${memberId}&key=${key}&token=${token}`;
      const postRes = await fetch(postUrl, { method: 'POST' });

      if (postRes.ok) {
        console.log(`   ✅ Đã gán thẻ [${prefix}] cho ${memberName}`);
        assignedCount++;
      } else {
        const err = await postRes.text();
        console.error(`   ❌ Lỗi gán thẻ [${prefix}]:`, postRes.status, err);
      }

      await new Promise(r => setTimeout(r, 200));
    }
  }

  console.log(`\n🎉 Hoàn tất gán thành viên! Tổng số lượt gán thành công: ${assignedCount}/24 thẻ.`);

  // Kiểm tra lại toàn diện
  console.log('\n🔍 Bắt đầu kiểm tra lại toàn bộ trạng thái gán thành viên trong Sprint 04...');
  const verifyRes = await fetch(`https://api.trello.com/1/lists/${LIST_ID}/cards?fields=name,idMembers&key=${key}&token=${token}`);
  const verifyCards = await verifyRes.json();

  let unassignedLeft = 0;
  verifyCards.forEach(c => {
    if (c.id === '6af9c711c03fc42b36b3f865') return; // Goal card
    if (!c.idMembers || c.idMembers.length === 0) {
      unassignedLeft++;
      console.log(`⚠️ Thẻ chưa được gán: ${c.name}`);
    }
  });

  if (unassignedLeft === 0) {
    console.log('✅ XÁC NHẬN: 100% (53/53) các thẻ nghiệp vụ trong Sprint 04 - Backlog đã được gán thành viên đầy đủ!');
  } else {
    console.log(`⚠️ Vẫn còn ${unassignedLeft} thẻ chưa được gán thành viên.`);
  }
}

assignMembers();
