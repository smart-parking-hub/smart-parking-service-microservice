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
const boardId = env.TRELLO_ACTIVE_BOARD_ID || env.TRELLO_SPH_BOARD_ID;

const MEMBERS = {
  HUY: '69016d7a8c58ffe25753e224',
  TUNG: '6930e5b55d3528613be81a82',
  LOC: '68f0354b7e270498cfc5666a',
  KHAI: '6a6b571e2274ff581eb61742'
};

async function main() {
  console.log('Fetching lists on board...');
  const res = await fetch(`https://api.trello.com/1/boards/${boardId}/lists?key=${key}&token=${token}`);
  const lists = await res.json();
  
  const s3DoneList = lists.find(l => l.name === 'Sprint 03 - Done');
  let s4BacklogList = lists.find(l => l.name === 'Sprint 04 - Backlog');

  if (!s4BacklogList) {
    console.log('Creating list "Sprint 04 - Backlog"...');
    const createRes = await fetch(`https://api.trello.com/1/lists?name=${encodeURIComponent('Sprint 04 - Backlog')}&idBoard=${boardId}&pos=10700&key=${key}&token=${token}`, { method: 'POST' });
    s4BacklogList = await createRes.json();
    console.log(`Created "Sprint 04 - Backlog" with ID: ${s4BacklogList.id}`);
  } else {
    console.log(`Found existing "Sprint 04 - Backlog" with ID: ${s4BacklogList.id}`);
  }

  // 1. Move SP-S3-SEC-01 to Sprint 03 - Done
  const otpCardId = '6aeb0dd7eb63b5ead21a7b83';
  console.log(`Moving SP-S3-SEC-01 (${otpCardId}) to Sprint 03 - Done...`);
  await fetch(`https://api.trello.com/1/cards/${otpCardId}?idList=${s3DoneList.id}&key=${key}&token=${token}`, { method: 'PUT' });
  console.log('Moved SP-S3-SEC-01 to Sprint 03 - Done.');

  // 2. Define Card assignments for Sprint 04
  const assignments = [
    // LÂM GIA HUY
    { id: '6aeb0f2c7bece0719e972573', member: MEMBERS.HUY, name: '[SP-S3-PRC-01]' },
    { id: '6aeae18e7ab30a12756e0b4d', member: MEMBERS.HUY, name: '[SP-S3-PAY-01]' },
    { id: '6aeae1a4b87ce1894eaa842f', member: MEMBERS.HUY, name: '[SP-S3-INT-04]' },
    { id: '6aeb0e6af81c012127035f0d', member: MEMBERS.HUY, name: '[SP-S3-UI-04]' },
    { id: '6aec0ae2b584736e717c7d9e', member: MEMBERS.HUY, name: '[SP-S3-UI-09]' },
    { id: '6aec368f5ca048394a3bdb60', member: MEMBERS.HUY, name: '[SP-S3-UI-15]' },

    // HOÀNG ĐÌNH TÙNG
    { id: '6aeb0f9041d30bcd02604e02', member: MEMBERS.TUNG, name: '[SP-S3-GOV-05]' },
    { id: '6aeb0e81796cb8e86163ed8a', member: MEMBERS.TUNG, name: '[SP-S3-UI-17]' },
    { id: '6aeb0e9b9b05af63d8b7df57', member: MEMBERS.TUNG, name: '[SP-S3-UI-19]' },
    { id: '6aeb0eb378ada3de72fcb490', member: MEMBERS.TUNG, name: '[SP-S3-UI-20]' },
    { id: '6aec0ae5e05668d30f6c6996', member: MEMBERS.TUNG, name: '[SP-S3-DAT-02]' },
    { id: '6aeb0dc572d101c0e2d91da4', member: MEMBERS.TUNG, name: '[SP-S3-RT-01]' },

    // HUỲNH LA TIẾN LỘC
    { id: '6aeb0f9617aabd604a27a3bd', member: MEMBERS.LOC, name: '[SP-S3-PAY-07]' },
    { id: '6aeb0dcbfd4829d28a5fbc8c', member: MEMBERS.LOC, name: '[SP-S3-SAGA-01]' },
    { id: '6aeb0ed8cf41eb6b7287a249', member: MEMBERS.LOC, name: '[SP-S3-PAY-10]' },
    { id: '6af50aceb06764c1ef25a5dd', member: MEMBERS.LOC, name: '[SP-S3-L01]' },
    { id: '6aeb0ef6e0fd652d8a6439c5', member: MEMBERS.LOC, name: '[SP-S3-UI-08]' },
    { id: '6aeb0f1ad9ead4ce22829aca', member: MEMBERS.LOC, name: '[SP-S3-UI-11]' },

    // HỒ QUỐC KHẢI
    { id: '6aec0ae4c015dee2c6cf8cc9', member: MEMBERS.KHAI, name: '[SP-S3-BKG-02]' },
    { id: '6aeb0f51258c3f76952c34f0', member: MEMBERS.KHAI, name: '[SP-S3-GOV-06]' },
    { id: '6aeb0f57c3e486772b73701c', member: MEMBERS.KHAI, name: '[SP-S3-GOV-07]' },
    { id: '6aeb0f0fe79ce69670c43ac3', member: MEMBERS.KHAI, name: '[SP-S3-UI-10]' },
    { id: '6aeb0f20c156365db84b421b', member: MEMBERS.KHAI, name: '[SP-S3-UI-12]' },
    { id: '6aeb0f5d33fd061e88f19572', member: MEMBERS.KHAI, name: '[SP-S3-UI-07]' },
    { id: '6aeb0e8ef14ba0aee24fe3cc', member: MEMBERS.KHAI, name: '[SP-S3-UI-18]' }
  ];

  console.log(`\nMoving and assigning ${assignments.length} cards to "Sprint 04 - Backlog"...`);
  for (const item of assignments) {
    try {
      // Move to list
      await fetch(`https://api.trello.com/1/cards/${item.id}?idList=${s4BacklogList.id}&key=${key}&token=${token}`, { method: 'PUT' });
      // Set assigned member
      await fetch(`https://api.trello.com/1/cards/${item.id}?idMembers=${item.member}&key=${key}&token=${token}`, { method: 'PUT' });
      console.log(`[OK] Moved & assigned ${item.name} (${item.id})`);
    } catch (err) {
      console.error(`[ERROR] Failed for ${item.name}:`, err);
    }
  }

  // Also move the SPRINT-GOAL of Sprint 3 if needed, or leave it in Sprint 03 - Backlog
  console.log('\nAll cards successfully moved and assigned to Sprint 04 - Backlog!');
}

main();
