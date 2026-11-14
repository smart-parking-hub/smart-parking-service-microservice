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

  console.log('=== ALL BOARD LISTS ===');
  for (const l of lists) {
    console.log(`- [${l.id}] "${l.name}"`);
  }

  const s3Cards = [];
  for (const l of lists) {
    const cRes = await fetch(`https://api.trello.com/1/lists/${l.id}/cards?key=${key}&token=${token}&fields=id,name,idMembers,labels,idList`);
    const cards = await cRes.json();
    for (const c of cards) {
      const labels = (c.labels || []).map(lb => lb.name);
      const isS3 = labels.some(lb => lb.toLowerCase().includes('sprint 03') || lb.toLowerCase().includes('sprint 3')) || c.name.includes('SP-S3');
      if (isS3) {
        s3Cards.push({
          id: c.id,
          name: c.name,
          listName: l.name,
          listId: l.id,
          assigned: (c.idMembers || []).map(mId => memberMap[mId] || mId).join(', ') || 'Unassigned',
          assignedIds: c.idMembers || [],
          labels: labels.join(', ')
        });
      }
    }
  }

  console.log(`\n=== TOTAL SPRINT 3 CARDS FOUND: ${s3Cards.length} ===`);
  const byList = {};
  for (const c of s3Cards) {
    byList[c.listName] = byList[c.listName] || [];
    byList[c.listName].push(c);
  }

  for (const [listName, cards] of Object.entries(byList)) {
    console.log(`\n>>> List: "${listName}" (${cards.length} cards):`);
    for (const c of cards) {
      console.log(`  * [${c.id}] ${c.name} | [${c.assigned}]`);
    }
  }
}
run();
