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
  const checkLists = ['Project Risks', 'Project Improvements'];
  const res = await fetch(`https://api.trello.com/1/boards/${boardId}/lists?key=${key}&token=${token}`);
  const lists = await res.json();
  for (const l of lists.filter(x => checkLists.includes(x.name))) {
    const cRes = await fetch(`https://api.trello.com/1/lists/${l.id}/cards?key=${key}&token=${token}&fields=id,name,labels`);
    const cards = await cRes.json();
    console.log(`\nList: "${l.name}" (${cards.length} cards):`);
    for (const c of cards) console.log(`- ${c.name}`);
  }
}
run();
