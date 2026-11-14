import { key, token, boardId } from './trello-client.mjs';

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
