import { key, token, boardId } from './trello-client.mjs';

async function run() {
  const res = await fetch(`https://api.trello.com/1/boards/${boardId}/lists?key=${key}&token=${token}`);
  const lists = await res.json();
  for (const l of lists) {
    console.log(`- [${l.id}] pos: ${l.pos} -> "${l.name}"`);
  }
}
run();
