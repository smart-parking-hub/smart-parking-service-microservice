import fs from 'node:fs';
import path from 'node:path';

// Read .trello.env
const envPath = 'd:/Documents/JAVA WEB SERVICE/SmartParkingHub/smart-parking-microservice/.trello.env';
const content = fs.readFileSync(envPath, 'utf8');
const env = {};
for (const line of content.split('\n')) {
  const trimmed = line.trim();
  if (trimmed && !trimmed.startsWith('#') && trimmed.includes('=')) {
    const idx = trimmed.indexOf('=');
    env[trimmed.substring(0, idx).trim()] = trimmed.substring(idx + 1).trim();
  }
}

const API_KEY = env.TRELLO_API_KEY;
const API_TOKEN = env.TRELLO_API_TOKEN;
const ACTIVE_BOARD_ID = env.TRELLO_ACTIVE_BOARD_ID;
const SPRINT_BACKLOG_2_LIST_ID = '6ae1717ab0d54f6059bc253e';
const FE_LABEL_ID = '6adecae3ac6c046e8c80ed8c'; // FE label (blue)

async function trelloFetch(apiPath, method = 'GET', body = null) {
  const sep = apiPath.includes('?') ? '&' : '?';
  const url = `https://api.trello.com/1/${apiPath}${sep}key=${API_KEY}&token=${API_TOKEN}`;
  const opts = {
    method,
    headers: { 'Content-Type': 'application/json' }
  };
  if (body) opts.body = JSON.stringify(body);
  const res = await fetch(url, opts);
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`Trello API Error (${res.status}): ${text}`);
  }
  return res.json();
}

async function syncTasks(dryRun = true) {
  const tasks = JSON.parse(fs.readFileSync('C:/Users/Admin/.gemini/antigravity/brain/fee6201a-24e3-424a-b5a7-e43f996f6007/scratch/generated_tasks.json', 'utf8'));
  console.log(`Loaded ${tasks.length} tasks to sync.`);

  // Get current cards on board
  const currentCards = await trelloFetch(`boards/${ACTIVE_BOARD_ID}/cards?fields=name,idList`);
  console.log(`Current cards on board: ${currentCards.length}`);

  let createdCount = 0;
  let skippedCount = 0;

  for (const task of tasks) {
    const existing = currentCards.find(c => c.name.includes(task.taskId) || c.name.includes(`(${task.screenId})`) || c.name.includes(`(${task.screenId}:`));
    if (existing) {
      console.log(`⏩ [SKIP] Card already exists: ${existing.name}`);
      skippedCount++;
      continue;
    }

    if (dryRun) {
      console.log(`[DRY RUN] Would create: ${task.taskName}`);
      createdCount++;
    } else {
      console.log(`🚀 Creating: ${task.taskName}`);
      const body = {
        name: task.taskName,
        desc: task.desc,
        idList: SPRINT_BACKLOG_2_LIST_ID,
        idLabels: [FE_LABEL_ID],
        pos: 'bottom'
      };
      await trelloFetch('cards', 'POST', body);
      createdCount++;
      // Wait 120ms to respect rate limit
      await new Promise(r => setTimeout(r, 120));
    }
  }

  console.log(`\n========================================`);
  console.log(`Mode: ${dryRun ? 'DRY RUN' : 'LIVE SYNC'}`);
  console.log(`Created: ${createdCount}, Skipped: ${skippedCount}`);
  console.log(`========================================\n`);
}

const isLive = process.argv.includes('--live');
syncTasks(!isLive).catch(err => {
  console.error('❌ Error syncing to Trello:', err);
  process.exit(1);
});
