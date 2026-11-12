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
const boardId = env.TRELLO_ACTIVE_BOARD_ID;

const SPRINT_01_ID = '6aeae16be160fedf64991c1b';
const SPRINT_02_ID = '6aeae16c782dc3b52dfde1fd';
const SPRINT_03_ID = '6aeae16d5ee40e0053676fbf';
const AI_LABEL_ID  = '6aeadb815eeddc634d7a94dc';
const FE_LABEL_ID  = '6aeadb6ef14cc3d7e8aefd13';
const BE_LABEL_ID  = '6aeadb6f3b6a671626024a5a';
const P1_LABEL_ID  = '6aeadb7902d6f618b2264ffe';
const FEAT_LABEL_ID= '6aeadb7362b808225cd058ca';

async function addLabel(cardId, labelId) {
  const url = `https://api.trello.com/1/cards/${cardId}/idLabels?value=${labelId}&key=${key}&token=${token}`;
  const res = await fetch(url, { method: 'POST' });
  return res.ok;
}

async function removeLabel(cardId, labelId) {
  const url = `https://api.trello.com/1/cards/${cardId}/idLabels/${labelId}?key=${key}&token=${token}`;
  const res = await fetch(url, { method: 'DELETE' });
  return res.ok;
}

async function run() {
  console.log(`Đang lấy danh sách thẻ mới nhất từ board ${boardId}...`);
  const cardsRes = await fetch(`https://api.trello.com/1/boards/${boardId}/cards?key=${key}&token=${token}`);
  const cards = await cardsRes.json();
  console.log(`Tổng cộng ${cards.length} thẻ trên board.\n`);

  let s3Fixed = 0;
  let s2Fixed = 0;
  let s1Fixed = 0;
  let aiFixed = 0;

  for (const card of cards) {
    const currentLabelIds = card.idLabels || [];
    const name = card.name;

    // 1. Handle SP-S3 cards (Must have Sprint 03, Must NOT have Sprint 02)
    if (name.includes('SP-S3-') || name.includes('Sprint Goal 03')) {
      if (currentLabelIds.includes(SPRINT_02_ID)) {
        await removeLabel(card.id, SPRINT_02_ID);
        console.log(`[-] Gỡ Sprint 02: ${card.name}`);
        s3Fixed++;
        await new Promise(r => setTimeout(r, 60));
      }
      if (!currentLabelIds.includes(SPRINT_03_ID)) {
        await addLabel(card.id, SPRINT_03_ID);
        console.log(`[+] Gán Sprint 03: ${card.name}`);
        s3Fixed++;
        await new Promise(r => setTimeout(r, 60));
      }
    }

    // 2. Handle SP-S2 cards (Must have Sprint 02, Must NOT have Sprint 03 or Sprint 01)
    if (name.includes('SP-S2-')) {
      if (currentLabelIds.includes(SPRINT_03_ID)) {
        await removeLabel(card.id, SPRINT_03_ID);
        console.log(`[-] Gỡ Sprint 03 nhầm khỏi S2: ${card.name}`);
        s2Fixed++;
        await new Promise(r => setTimeout(r, 60));
      }
      if (currentLabelIds.includes(SPRINT_01_ID)) {
        await removeLabel(card.id, SPRINT_01_ID);
        console.log(`[-] Gỡ Sprint 01 nhầm khỏi S2: ${card.name}`);
        s2Fixed++;
        await new Promise(r => setTimeout(r, 60));
      }
      if (!currentLabelIds.includes(SPRINT_02_ID)) {
        await addLabel(card.id, SPRINT_02_ID);
        console.log(`[+] Gán Sprint 02: ${card.name}`);
        s2Fixed++;
        await new Promise(r => setTimeout(r, 60));
      }
    }

    // 3. Handle SP-S1 cards (Must have Sprint 01, Must NOT have Sprint 02 or Sprint 03)
    if (name.includes('SP-S1-')) {
      if (currentLabelIds.includes(SPRINT_02_ID)) {
        await removeLabel(card.id, SPRINT_02_ID);
        console.log(`[-] Gỡ Sprint 02 nhầm khỏi S1: ${card.name}`);
        s1Fixed++;
        await new Promise(r => setTimeout(r, 60));
      }
      if (currentLabelIds.includes(SPRINT_03_ID)) {
        await removeLabel(card.id, SPRINT_03_ID);
        console.log(`[-] Gỡ Sprint 03 nhầm khỏi S1: ${card.name}`);
        s1Fixed++;
        await new Promise(r => setTimeout(r, 60));
      }
      if (!currentLabelIds.includes(SPRINT_01_ID)) {
        await addLabel(card.id, SPRINT_01_ID);
        console.log(`[+] Gán Sprint 01: ${card.name}`);
        s1Fixed++;
        await new Promise(r => setTimeout(r, 60));
      }
    }

    // 4. Handle AI labels for AI cards
    if (name.includes('-AI-')) {
      if (!currentLabelIds.includes(AI_LABEL_ID)) {
        await addLabel(card.id, AI_LABEL_ID);
        console.log(`[+] Gán AI Assistant: ${card.name}`);
        aiFixed++;
        await new Promise(r => setTimeout(r, 60));
      }
    }

    // 5. Special fix for SP-S2-AI-05 (6aeb3ff06863085cc711105e) which had 0 labels
    if (card.id === '6aeb3ff06863085cc711105e') {
      if (!currentLabelIds.includes(FE_LABEL_ID)) await addLabel(card.id, FE_LABEL_ID);
      if (!currentLabelIds.includes(P1_LABEL_ID)) await addLabel(card.id, P1_LABEL_ID);
      if (!currentLabelIds.includes(FEAT_LABEL_ID)) await addLabel(card.id, FEAT_LABEL_ID);
      await new Promise(r => setTimeout(r, 60));
    }
  }

  console.log(`\n=== TỔNG KẾT HOÀN TẤT ===`);
  console.log(`- Thao tác sửa nhãn Sprint 3: ${s3Fixed}`);
  console.log(`- Thao tác sửa nhãn Sprint 2: ${s2Fixed}`);
  console.log(`- Thao tác sửa nhãn Sprint 1: ${s1Fixed}`);
  console.log(`- Thao tác bổ sung nhãn AI: ${aiFixed}`);
}

run();
