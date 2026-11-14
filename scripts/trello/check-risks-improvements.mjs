import { key, token, boardId } from './trello-client.mjs';

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
