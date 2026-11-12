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

async function trelloPut(path, body) {
  const url = `https://api.trello.com/1/${path}?key=${key}&token=${token}`;
  const res = await fetch(url, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`PUT ${path} failed (${res.status}): ${text}`);
  }
  return res.json();
}

async function runBatch1() {
  console.log('=== BATCH 1: LIST RESTRUCTURING & CARD MIGRATION ===');
  const log = [];

  // 1. Move cards from '03. Refinement' to 'Project Risks'
  const riskListId = '6aeadb649ff5dbddc487e023';
  console.log('Moving [RISK-04] (6aeafb7d6cabb592fbab4b07) to Project Risks...');
  await trelloPut('cards/6aeafb7d6cabb592fbab4b07', { idList: riskListId });
  log.push({ action: 'MOVE_CARD', cardId: '6aeafb7d6cabb592fbab4b07', from: '03. Refinement', to: 'Project Risks' });

  // 2. Move cards from 'Future Enhancements' to '02. Product Backlog'
  const backlogListId = '6ae95a51c9db9736f84f051b';
  console.log('Moving SP-S3-PAY-11 (6aeb0dd15bd1e31f85aa5a69) to Product Backlog...');
  await trelloPut('cards/6aeb0dd15bd1e31f85aa5a69', { idList: backlogListId });
  log.push({ action: 'MOVE_CARD', cardId: '6aeb0dd15bd1e31f85aa5a69', from: 'Future Enhancements', to: '02. Product Backlog' });

  console.log('Moving SP-S3-PAY-12 (6aeb0db6432d6bb22552d4cf) to Product Backlog...');
  await trelloPut('cards/6aeb0db6432d6bb22552d4cf', { idList: backlogListId });
  log.push({ action: 'MOVE_CARD', cardId: '6aeb0db6432d6bb22552d4cf', from: 'Future Enhancements', to: '02. Product Backlog' });

  // 3. Rename and reorder active lists
  const renames = [
    { id: '6ae95a51c9db9736f84f051b', name: '00 - Product Backlog', pos: 1000 },
    { id: '6aeadb63a58adab303946d14', name: '01 - Ready for Sprint', pos: 2000 },
    { id: '6aeadb649ff5dbddc487e023', name: 'Project Risks', pos: 3000 },
    { id: '6aeadb6464596a21a7015039', name: 'Project Improvements', pos: 4000 },
    { id: '6aeaf971e0f1d67966fe4d60', name: 'Sprint 03 - Backlog', pos: 5000 },
    { id: '6aeae165c801fb49a1b11ea1', name: 'Sprint 03 - To Do', pos: 6000 },
    { id: '6aeae166059d345d345b29d4', name: 'Sprint 03 - In Progress', pos: 7000 },
    { id: '6aeae1670b91d613b33e2693', name: 'Sprint 03 - Review', pos: 8000 },
    { id: '6aeae1677ae0edf5d0ef51b0', name: 'Sprint 03 - QA', pos: 9000 },
    { id: '6af12a6f0accbb0aea2ea9ec', name: 'Sprint 03 - Done', pos: 10000 },
    { id: '6aeae169e67627a7ecc25ea5', name: 'Sprint 02 - Done', pos: 11000 },
    { id: '6aeae169e8d6395e0370ef21', name: 'Sprint 01 - Done', pos: 12000 },
    { id: '6aeae16a302175ab568f52c3', name: 'Blocked', pos: 13000 },
  ];

  for (const r of renames) {
    console.log(`Renaming list ${r.id} to "${r.name}" (pos: ${r.pos})...`);
    await trelloPut(`lists/${r.id}`, { name: r.name, pos: r.pos });
    log.push({ action: 'RENAME_LIST', listId: r.id, newName: r.name, pos: r.pos });
    await new Promise(res => setTimeout(res, 150));
  }

  // 4. Close (archive) empty / obsolete lists
  const toClose = [
    { id: '6aeaf72b076797e67dd9396f', name: '01. New Requirements' },
    { id: '6aeaf72d98a7b2579c0f0542', name: '03. Refinement' },
    { id: '6af13ae345865de3c13e4944', name: 'Future Enhancements' },
    { id: '6aeaf72f3df8a239650411a4', name: '05. Sprint Backlog 01' },
    { id: '6af12a20e3b83851f90a29db', name: '05. Sprint Backlog 02' },
    { id: '6aeaf7353d2d1d8391cf6f7f', name: '12. Rejected / Cancelled' }
  ];

  for (const c of toClose) {
    console.log(`Archiving empty list ${c.id} ("${c.name}")...`);
    await trelloPut(`lists/${c.id}`, { closed: true });
    log.push({ action: 'ARCHIVE_EMPTY_LIST', listId: c.id, listName: c.name });
    await new Promise(res => setTimeout(res, 150));
  }

  fs.writeFileSync('batch1_list_log.json', JSON.stringify(log, null, 2), 'utf-8');
  console.log('Batch 1 completed successfully.');
}

runBatch1();
