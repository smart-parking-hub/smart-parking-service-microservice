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
const boardId = env.TRELLO_ACTIVE_BOARD_ID;

async function syncSnapshot() {
  console.log(`Đang tải snapshot mới nhất từ board ${boardId}...`);
  const [boardRes, listsRes, cardsRes] = await Promise.all([
    fetch(`https://api.trello.com/1/boards/${boardId}?key=${key}&token=${token}`),
    fetch(`https://api.trello.com/1/boards/${boardId}/lists?key=${key}&token=${token}`),
    fetch(`https://api.trello.com/1/boards/${boardId}/cards?key=${key}&token=${token}`)
  ]);

  const board = await boardRes.json();
  const lists = await listsRes.json();
  const cards = await cardsRes.json();

  const snapshot = {
    board: { id: board.id, name: board.name, url: board.url },
    lists,
    cards
  };

  fs.writeFileSync('smart-parking-microservice/docs/trello-snapshot.json', JSON.stringify(snapshot, null, 2), 'utf-8');
  console.log(`Đã cập nhật snapshot: ${lists.length} lists, ${cards.length} cards.`);
}

syncSnapshot();
