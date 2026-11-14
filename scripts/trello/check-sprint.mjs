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

async function run() {
  const res = await fetch(`https://api.trello.com/1/boards/${boardId}/lists?key=${key}&token=${token}`);
  const lists = await res.json();
  const membersRes = await fetch(`https://api.trello.com/1/boards/${boardId}/members?key=${key}&token=${token}`);
  const members = await membersRes.json();
  const memberMap = {};
  for (const m of members) {
    memberMap[m.id] = m.fullName || m.username;
  }
  console.log('=== BOARD MEMBERS ===');
  for (const [id, name] of Object.entries(memberMap)) {
    console.log(`- ${id}: ${name}`);
  }

  console.log('\n=== LISTS & CARDS ===');
  for (const l of lists) {
    const cRes = await fetch(`https://api.trello.com/1/lists/${l.id}/cards?key=${key}&token=${token}&fields=id,name,idMembers,labels`);
    const cards = await cRes.json();
    console.log(`\nList: [${l.name}] (id: ${l.id}) - ${cards.length} cards`);
    for (const c of cards) {
      const assigned = (c.idMembers || []).map(mId => memberMap[mId] || mId).join(', ') || 'Unassigned';
      const labels = (c.labels || []).map(lb => lb.name).join(', ');
      console.log(`  - [${c.id}] ${c.name} | Assigned: [${assigned}] | Labels: [${labels}]`);
    }
  }
}
run();
