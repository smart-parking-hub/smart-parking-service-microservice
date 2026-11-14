import { key, token, boardId } from './trello-client.mjs';

async function run() {
  const res = await fetch(`https://api.trello.com/1/boards/${boardId}/lists?key=${key}&token=${token}`);
  const lists = await res.json();
  const membersRes = await fetch(`https://api.trello.com/1/boards/${boardId}/members?key=${key}&token=${token}`);
  const members = await membersRes.json();
  const memberMap = {};
  for (const m of members) memberMap[m.id] = m.fullName || m.username;

  // Find cards in Sprint 03 - To Do, Sprint 03 - Backlog, Blocked
  const targetListNames = ['Sprint 03 - To Do', 'Sprint 03 - Backlog', 'Blocked'];
  const targetLists = lists.filter(l => targetListNames.includes(l.name));
  
  console.log('=== UNFINISHED SPRINT 3 CARDS ===');
  for (const l of targetLists) {
    const cRes = await fetch(`https://api.trello.com/1/lists/${l.id}/cards?key=${key}&token=${token}&fields=id,name,idMembers,labels,desc`);
    const cards = await cRes.json();
    console.log(`\nList: "${l.name}" (${cards.length} cards):`);
    for (const c of cards) {
      if (c.name.includes('[SPRINT-GOAL]')) continue;
      const assigned = (c.idMembers || []).map(mId => memberMap[mId] || mId).join(', ') || 'Unassigned';
      console.log(`- ID: ${c.id}`);
      console.log(`  Name: ${c.name}`);
      console.log(`  Current Assigned: ${assigned}`);
    }
  }
}
run();
