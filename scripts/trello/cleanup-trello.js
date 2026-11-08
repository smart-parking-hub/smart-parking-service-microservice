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

const DONE_LIST_ID = '6adecc33f1b35f096a0e9871'; // Sprint Backlog 2 (Done)
const PRODUCT_BACKLOG_LIST_ID = '6adecb83c59ce2c7fc21ec4f'; // Product Backlog

async function updateCard(cardId, updateData, actionName) {
  const url = `https://api.trello.com/1/cards/${cardId}?key=${key}&token=${token}`;
  const res = await fetch(url, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(updateData)
  });
  if (res.ok) {
    const card = await res.json();
    console.log(`✅ [${actionName}] Thẻ "${card.name}" -> Thành công!`);
  } else {
    const err = await res.text();
    console.error(`❌ [${actionName}] Thất bại (${res.status}): ${err}`);
  }
}

async function main() {
  console.log('--- BẮT ĐẦU DỌN DẸP BẢNG TRELLO ---');

  // 1. Chuyển SP-INP-03 sang Done
  await updateCard('6ae174ea57079234fc9fb738', { idList: DONE_LIST_ID }, 'MOVE_TO_DONE: SP-INP-03');

  // 2. Chuyển SP-PK-01-T02 sang Done
  await updateCard('6ae36efa4fcde41de1c4e18a', { idList: DONE_LIST_ID }, 'MOVE_TO_DONE: SP-PK-01-T02');

  // 3. Chuyển SP-ID-01-T03 sang Done
  await updateCard('6ae36ef13f050d7c5931d1e2', { idList: DONE_LIST_ID }, 'MOVE_TO_DONE: SP-ID-01-T03');

  // 4. Chuyển SP-REV-03 sang Done
  await updateCard('6ae1758f72e89bbb46b93264', { idList: DONE_LIST_ID }, 'MOVE_TO_DONE: SP-REV-03');

  // 5. Chuyển SP-AI-01-T02 sang Product Backlog (đồng bộ cùng SP-AI-01-T03)
  await updateCard('6ae36f0455004faa2680233f', { idList: PRODUCT_BACKLOG_LIST_ID }, 'MOVE_TO_PRODUCT_BACKLOG: SP-AI-01-T02');

  // 6. Archive rủi ro RISK-03 (Admin portal chưa chuyển xong v2 - nay đã xong 100%)
  await updateCard('6aead771da479daab813d1ee', { closed: true }, 'ARCHIVE_OBSOLETE_RISK: RISK-03');

  console.log('--- HOÀN TẤT DỌN DẸP BẢNG TRELLO ---');
}

main();
