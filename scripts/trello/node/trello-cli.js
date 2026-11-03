/**
 * Trello CLI Assistant for SmartParkingHub Microservices
 * Chạy bằng: node scripts/trello-cli.js [command]
 */

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

// Tìm file .trello.env ở các vị trí khả dĩ
const possiblePaths = [
  path.resolve(process.cwd(), '.trello.env'),
  path.resolve(__dirname, '..', '.trello.env'),
  path.resolve(__dirname, '..', '..', '.trello.env')
];

let envPath = possiblePaths.find(p => fs.existsSync(p));

if (!envPath) {
  console.error('❌ Không tìm thấy file .trello.env!');
  process.exit(1);
}

const envContent = fs.readFileSync(envPath, 'utf-8');
const env = {};
for (const line of envContent.split('\n')) {
  const trimmed = line.trim();
  if (trimmed && !trimmed.startsWith('#') && trimmed.includes('=')) {
    const idx = trimmed.indexOf('=');
    env[trimmed.substring(0, idx).trim()] = trimmed.substring(idx + 1).trim();
  }
}

const API_KEY = env.TRELLO_API_KEY;
const API_TOKEN = env.TRELLO_API_TOKEN;
const ACTIVE_BOARD_ID = env.TRELLO_ACTIVE_BOARD_ID;
const SPH_BOARD_ID = env.TRELLO_SPH_BOARD_ID;

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

async function showStatus(boardId) {
  const board = await trelloFetch(`boards/${boardId}`);
  console.log('\n========================================================');
  console.log(`📌 BẢNG TRELLO: ${board.name}`);
  console.log(`🔗 LINK: ${board.url}`);
  console.log('========================================================\n');

  const lists = await trelloFetch(`boards/${boardId}/lists`);
  const cards = await trelloFetch(`boards/${boardId}/cards?members=true&member_fields=fullName,username`);

  for (const list of lists) {
    const listCards = cards.filter(c => c.idList === list.id);
    let icon = '📁';
    if (list.name.toLowerCase().includes('in progress')) icon = '⏳';
    else if (list.name.toLowerCase().includes('review')) icon = '🔍';
    else if (list.name.toLowerCase().includes('done')) icon = '✅';
    else if (list.name.toLowerCase().includes('blocked')) icon = '🛑';
    else if (list.name.toLowerCase().includes('backlog')) icon = '📋';

    console.log(`${icon} [${list.name}] (${listCards.length} thẻ)`);
    for (const card of listCards) {
      const shortId = card.id.slice(-6);
      const assignees = (card.members && card.members.length > 0)
        ? ` -> 👤 ${card.members.map(m => m.fullName).join(', ')}`
        : ' -> ⚠️ Chưa gán';
      console.log(`   • [${shortId}] ${card.name}${assignees}`);
    }
    console.log('');
  }
}

async function showMembers(boardId) {
  const members = await trelloFetch(`boards/${boardId}/members`);
  const cards = await trelloFetch(`boards/${boardId}/cards?members=true&member_fields=fullName,username`);
  const lists = await trelloFetch(`boards/${boardId}/lists`);
  const listMap = Object.fromEntries(lists.map(l => [l.id, l.name]));

  console.log('\n👥 ================= PHÂN BỔ TASK TỪNG THÀNH VIÊN ================= 👥\n');
  const report = {};
  for (const m of members) {
    report[m.id] = { name: m.fullName, username: m.username, total: 0, byStatus: {}, cards: [] };
  }
  report['unassigned'] = { name: 'Chưa phân công', username: 'none', total: 0, byStatus: {}, cards: [] };

  for (const c of cards) {
    const listName = listMap[c.idList] || 'Khác';
    if (!c.idMembers || c.idMembers.length === 0) {
      report['unassigned'].total++;
      report['unassigned'].byStatus[listName] = (report['unassigned'].byStatus[listName] || 0) + 1;
      report['unassigned'].cards.push({ name: c.name, list: listName });
    } else {
      for (const mId of c.idMembers) {
        if (!report[mId]) {
          report[mId] = { name: mId, username: mId, total: 0, byStatus: {}, cards: [] };
        }
        report[mId].total++;
        report[mId].byStatus[listName] = (report[mId].byStatus[listName] || 0) + 1;
        report[mId].cards.push({ name: c.name, list: listName });
      }
    }
  }

  // Sắp xếp theo số lượng task giảm dần
  const sorted = Object.values(report).sort((a, b) => b.total - a.total);

  for (const item of sorted) {
    if (item.total === 0 && item.username === 'none') continue;
    const bar = '█'.repeat(Math.min(item.total, 25));
    console.log(`👤 ${item.name.padEnd(30)} | Tổng: ${String(item.total).padStart(2)} tasks | ${bar}`);
    for (const [st, count] of Object.entries(item.byStatus)) {
      console.log(`     └─ ${st}: ${count}`);
    }
    console.log('');
  }
}

async function showBlocked(boardId) {
  const lists = await trelloFetch(`boards/${boardId}/lists`);
  const blockedList = lists.find(l => l.name.toLowerCase().includes('blocked'));
  if (!blockedList) {
    console.log('Không tìm thấy danh sách Blocked!');
    return;
  }
  const cards = await trelloFetch(`lists/${blockedList.id}/cards?members=true&member_fields=fullName`);
  console.log(`\n🛑 ================= CÁC TASK ĐANG BỊ BLOCKED (${cards.length} cards) ================= 🛑\n`);
  for (const c of cards) {
    const assignees = (c.members && c.members.length > 0)
      ? c.members.map(m => m.fullName).join(', ')
      : 'Chưa gán';
    console.log('------------------------------------------------------------');
    console.log(`❌ ${c.name}`);
    console.log(`👤 Phụ trách: ${assignees}`);
    console.log(`🔗 ${c.url}`);
    if (c.desc) {
      const snippet = c.desc.split('\n').slice(0, 4).join('\n');
      console.log(`📝 Mô tả: ${snippet}\n...`);
    }
  }
}

async function moveCard(boardId, query, toList) {
  const lists = await trelloFetch(`boards/${boardId}/lists`);
  const dest = lists.find(l => l.name.toLowerCase().includes(toList.toLowerCase()));
  if (!dest) {
    console.error(`❌ Không tìm thấy cột nào khớp với "${toList}"!`);
    return;
  }

  const cards = await trelloFetch(`boards/${boardId}/cards`);
  const card = cards.find(c => c.id.endsWith(query) || c.name.toLowerCase().includes(query.toLowerCase()));
  if (!card) {
    console.error(`❌ Không tìm thấy thẻ nào khớp với query "${query}"!`);
    return;
  }

  await trelloFetch(`cards/${card.id}?idList=${dest.id}`, 'PUT');
  console.log(`✅ Đã chuyển thẻ "${card.name}" sang cột "${dest.name}"!`);
}

async function main() {
  const args = process.argv.slice(2);
  const action = args[0] || 'status';

  try {
    switch (action) {
      case 'status':
        await showStatus(ACTIVE_BOARD_ID);
        break;
      case 'members':
      case 'team':
        await showMembers(ACTIVE_BOARD_ID);
        break;
      case 'blocked':
        await showBlocked(ACTIVE_BOARD_ID);
        break;
      case 'move': {
        const query = args[1];
        const dest = args[2];
        if (!query || !dest) {
          console.error('Cú pháp: node scripts/trello-cli.js move <CardQuery/ID> <TargetList>');
          process.exit(1);
        }
        await moveCard(ACTIVE_BOARD_ID, query, dest);
        break;
      }
      default:
        console.log(`
Lệnh hỗ trợ:
  node scripts/trello-cli.js status           # Xem trạng thái các cột và thẻ
  node scripts/trello-cli.js members          # Biểu đồ và phân bổ task từng thành viên
  node scripts/trello-cli.js blocked          # Xem chi tiết các task đang bị Blocked
  node scripts/trello-cli.js move <ID> <List> # Chuyển card sang cột khác
        `);
    }
  } catch (err) {
    console.error('❌ Lỗi:', err.message);
  }
}

main();
