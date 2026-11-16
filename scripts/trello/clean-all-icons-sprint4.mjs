import fs from 'node:fs';

const envContent = fs.readFileSync('smart-parking-microservice/.trello.env', 'utf8');
const key = envContent.match(/TRELLO_API_KEY=(.+)/)?.[1]?.trim();
const token = envContent.match(/TRELLO_API_TOKEN=(.+)/)?.[1]?.trim();
const LIST_ID = '6af80755e8b450a479eac591'; // Sprint 04 - Backlog

const emojiRegex = /[\p{Extended_Pictographic}\u{1F300}-\u{1F9FF}\u{2600}-\u{26FF}\u{2700}-\u{27BF}\u{FE0F}\u{200D}]/gu;

function stripEmojis(text) {
  if (!text) return '';
  return text
    .replace(/###\s*📌\s*/g, '### 1. ')
    .replace(/###\s*🎯\s*/g, '### 2. ')
    .replace(/###\s*✅\s*/g, '### 3. ')
    .replace(/##\s*🎯\s*/g, '## ')
    .replace(/###\s*🚀\s*/g, '### ')
    .replace(/###\s*👥\s*/g, '### ')
    .replace(/[\p{Extended_Pictographic}\u{1F300}-\u{1F9FF}\u{2600}-\u{26FF}\u{2700}-\u{27BF}\u{FE0F}\u{200D}]/gu, '')
    .replace(/[➔➜➡]/g, '->')
    .replace(/[★☆✨⚡]/g, '')
    .replace(/  +/g, ' ');
}

async function cleanCards() {
  console.log('🚀 Bắt đầu quét và làm sạch toàn bộ icon/emoji trong danh sách "Sprint 04 - Backlog"...');
  
  const res = await fetch(`https://api.trello.com/1/lists/${LIST_ID}/cards?fields=name,desc&key=${key}&token=${token}`);
  const cards = await res.json();
  console.log(`Tìm thấy ${cards.length} thẻ trên Trello.\n`);

  let updatedCount = 0;

  for (let i = 0; i < cards.length; i++) {
    const card = cards[i];
    const cleanedName = stripEmojis(card.name).trim();
    const cleanedDesc = stripEmojis(card.desc).trim();

    const nameChanged = cleanedName !== card.name;
    const descChanged = cleanedDesc !== (card.desc || '').trim();

    if (nameChanged || descChanged) {
      console.log(`[${i + 1}/${cards.length}] Đang xóa icon trong thẻ: ${card.name.slice(0, 50)}...`);
      
      const payload = {};
      if (nameChanged) payload.name = cleanedName;
      if (descChanged) payload.desc = cleanedDesc;

      const putRes = await fetch(`https://api.trello.com/1/cards/${card.id}?key=${key}&token=${token}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      if (putRes.ok) {
        console.log(`   ✅ Đã làm sạch thành công: ${card.id}`);
        updatedCount++;
      } else {
        const err = await putRes.text();
        console.error(`   ❌ Lỗi cập nhật thẻ ${card.id}:`, err);
      }

      await new Promise(r => setTimeout(r, 200));
    }
  }

  console.log(`\n🎉 Hoàn tất! Đã làm sạch icon cho ${updatedCount} thẻ trong Sprint 04 - Backlog.`);

  // Kiểm tra lại toàn diện
  console.log('\n🔍 Bắt đầu tái kiểm tra toàn bộ 54 thẻ...');
  const verifyRes = await fetch(`https://api.trello.com/1/lists/${LIST_ID}/cards?fields=name,desc&key=${key}&token=${token}`);
  const verifyCards = await verifyRes.json();
  
  let remainingEmojis = 0;
  verifyCards.forEach((c, idx) => {
    const hasInName = emojiRegex.test(c.name);
    const hasInDesc = emojiRegex.test(c.desc || '');
    if (hasInName || hasInDesc) {
      remainingEmojis++;
      console.log(`⚠️ Thẻ vẫn còn icon: [${idx + 1}] ${c.name} (Name: ${hasInName}, Desc: ${hasInDesc})`);
    }
  });

  if (remainingEmojis === 0) {
    console.log('✅ XÁC NHẬN: 100% các thẻ trong Sprint 04 - Backlog đã sạch hoàn toàn icon/emoji!');
  } else {
    console.log(`⚠️ Vẫn còn ${remainingEmojis} thẻ chứa ký tự đặc biệt.`);
  }
}

cleanCards();
