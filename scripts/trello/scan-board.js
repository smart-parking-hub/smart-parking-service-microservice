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
const boardId = env.TRELLO_BOARD_ID || '6adecae3ac6c046e8c80ed81';

async function main() {
  const listsRes = await fetch(`https://api.trello.com/1/boards/${boardId}/lists?key=${key}&token=${token}`);
  const lists = await listsRes.json();
  console.log('=== LISTS ===');
  for (const l of lists) {
    const cardsRes = await fetch(`https://api.trello.com/1/lists/${l.id}/cards?key=${key}&token=${token}`);
    const cards = await cardsRes.json();
    console.log(`\nList: [${l.name}] (id: ${l.id}) - ${cards.length} cards`);
    for (const c of cards) {
      console.log(`  - [${c.id}] ${c.name}`);
    }
  }
}
main();
