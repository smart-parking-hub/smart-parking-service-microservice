import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const envPath = path.resolve(__dirname, '..', '..', '.trello.env');

const envContent = fs.readFileSync(envPath, 'utf-8');
const env = {};
for (const line of envContent.split('\n')) {
  const trimmed = line.trim();
  if (trimmed && !trimmed.startsWith('#') && trimmed.includes('=')) {
    const idx = trimmed.indexOf('=');
    env[trimmed.substring(0, idx).trim()] = trimmed.substring(idx + 1).trim();
  }
}

const apiKey = env.TRELLO_API_KEY;
const apiToken = env.TRELLO_API_TOKEN;
const boardId = env.TRELLO_ACTIVE_BOARD_ID;

async function run() {
  const [board, members, lists, cards] = await Promise.all([
    fetch(`https://api.trello.com/1/boards/${boardId}?key=${apiKey}&token=${apiToken}`).then(r => r.json()),
    fetch(`https://api.trello.com/1/boards/${boardId}/members?key=${apiKey}&token=${apiToken}`).then(r => r.json()),
    fetch(`https://api.trello.com/1/boards/${boardId}/lists?key=${apiKey}&token=${apiToken}`).then(r => r.json()),
    fetch(`https://api.trello.com/1/boards/${boardId}/cards?key=${apiKey}&token=${apiToken}&members=true&member_fields=fullName,username`).then(r => r.json())
  ]);

  const outDir = path.resolve(__dirname, '..', '..', 'docs');
  if (!fs.existsSync(outDir)) fs.mkdirSync(outDir, { recursive: true });

  const data = {
    board: { id: board.id, name: board.name, url: board.url },
    members,
    lists: lists.map(l => ({ id: l.id, name: l.name })),
    cards: cards.map(c => ({
      id: c.id,
      name: c.name,
      idList: c.idList,
      idMembers: c.idMembers,
      url: c.url,
      labels: c.labels ? c.labels.map(lb => ({ name: lb.name, color: lb.color })) : [],
      desc: c.desc ? c.desc.slice(0, 300) : ''
    })),
    generatedAt: new Date().toISOString()
  };

  const jsonPath = path.join(outDir, 'trello-snapshot.json');
  fs.writeFileSync(jsonPath, JSON.stringify(data, null, 2), 'utf-8');
  console.log(`✅ Đã xuất dữ liệu ${cards.length} cards vào: ${jsonPath}`);
}

run();
