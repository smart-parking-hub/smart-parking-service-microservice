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

async function takeSnapshot() {
  console.log('Taking full snapshot before restructuring...');
  const listsRes = await fetch(`https://api.trello.com/1/boards/${boardId}/lists?key=${key}&token=${token}`);
  const lists = await listsRes.json();
  const listMap = Object.fromEntries(lists.map(l => [l.id, l.name]));

  const cardsRes = await fetch(`https://api.trello.com/1/boards/${boardId}/cards?checklists=all&attachments=true&customFieldItems=true&members=true&member_fields=fullName,username&key=${key}&token=${token}`);
  const cards = await cardsRes.json();

  const labelsRes = await fetch(`https://api.trello.com/1/boards/${boardId}/labels?key=${key}&token=${token}`);
  const labels = await labelsRes.json();

  const snapshot = {
    timestamp: new Date().toISOString(),
    boardId,
    totalLists: lists.length,
    totalCards: cards.length,
    lists,
    labels,
    cards: cards.map(c => ({
      id: c.id,
      name: c.name,
      idList: c.idList,
      listName: listMap[c.idList] || 'Unknown',
      desc: c.desc,
      idLabels: c.idLabels,
      labels: (c.labels || []).map(l => l.name || l.color),
      idMembers: c.idMembers,
      members: (c.members || []).map(m => m.fullName),
      due: c.due,
      checklists: c.checklists,
      attachments: c.attachments,
      pos: c.pos
    }))
  };

  fs.writeFileSync('before_restructure_snapshot.json', JSON.stringify(snapshot, null, 2), 'utf-8');
  console.log(`Snapshot saved: ${cards.length} cards, ${lists.length} lists, ${labels.length} labels.`);
}

takeSnapshot();
