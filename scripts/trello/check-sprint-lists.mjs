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

  console.log('=== SPRINT RELATED LISTS ===');
  for (const l of lists) {
    const cRes = await fetch(`https://api.trello.com/1/lists/${l.id}/cards?key=${key}&token=${token}&fields=id,name,idMembers,labels,idList`);
    const cards = await cRes.json();
    console.log(`\n======================================================`);
    console.log(`LIST: "${l.name}" (id: ${l.id}) - ${cards.length} cards`);
    console.log(`======================================================`);
    for (const c of cards) {
      const assigned = (c.idMembers || []).map(mId => memberMap[mId] || mId).join(', ') || 'Unassigned';
      const labels = (c.labels || []).map(lb => lb.name).join(', ');
      console.log(`- [${c.id}] ${c.name}\n  Assigned: ${assigned} | Labels: [${labels}]`);
    }
  }
}
run();
