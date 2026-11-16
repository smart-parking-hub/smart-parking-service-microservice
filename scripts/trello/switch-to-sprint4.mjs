import { key, token, boardId } from './trello-client.mjs';

async function trello(path, method = 'GET', body = null) {
  const sep = path.includes('?') ? '&' : '?';
  const url = `https://api.trello.com/1/${path}${sep}key=${key}&token=${token}`;
  const opts = {
    method,
    headers: { 'Content-Type': 'application/json' },
  };
  if (body) opts.body = JSON.stringify(body);
  const res = await fetch(url, opts);
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`${method} ${path} failed (${res.status}): ${text}`);
  }
  return res.json();
}

async function main() {
  console.log('=== CHUYỂN BẢNG SANG SPRINT 04 ===\n');

  // 1. Fetch current lists
  const lists = await trello(`boards/${boardId}/lists?cards=open`);
  console.log(`Tìm thấy ${lists.length} danh sách trên bảng.`);

  const s3Backlog = lists.find(l => l.name === 'Sprint 03 - Backlog');
  const s3ToDo = lists.find(l => l.name === 'Sprint 03 - To Do');
  const s3InProgress = lists.find(l => l.name === 'Sprint 03 - In Progress');
  const s3Review = lists.find(l => l.name === 'Sprint 03 - Review');
  const s3QA = lists.find(l => l.name === 'Sprint 03 - QA');
  const s4Backlog = lists.find(l => l.name === 'Sprint 04 - Backlog');
  const s3Done = lists.find(l => l.name === 'Sprint 03 - Done');
  let s4Done = lists.find(l => l.name === 'Sprint 04 - Done');

  // 2. Chuyển thẻ [SPRINT-GOAL] Sprint 03 sang Sprint 03 - Done
  if (s3Backlog && s3Backlog.cards.length > 0 && s3Done) {
    for (const card of s3Backlog.cards) {
      console.log(`-> Di chuyển thẻ "${card.name}" sang "Sprint 03 - Done"...`);
      await trello(`cards/${card.id}`, 'PUT', { idList: s3Done.id });
    }
  }

  // 3. Đóng danh sách Sprint 03 - Backlog cũ
  if (s3Backlog) {
    console.log(`-> Đóng danh sách cũ: "${s3Backlog.name}"...`);
    await trello(`lists/${s3Backlog.id}/closed`, 'PUT', { value: true });
  }

  // 4. Định vị Sprint 04 - Backlog về vị trí chuẩn (pos: 5000)
  if (s4Backlog) {
    console.log(`-> Cập nhật vị trí "Sprint 04 - Backlog" về pos: 5000...`);
    await trello(`lists/${s4Backlog.id}`, 'PUT', { pos: 5000 });
  }

  // 5. Đổi tên các cột quy trình sang Sprint 04
  if (s3ToDo) {
    console.log(`-> Đổi tên "${s3ToDo.name}" -> "Sprint 04 - To Do" (pos: 6000)...`);
    await trello(`lists/${s3ToDo.id}`, 'PUT', { name: 'Sprint 04 - To Do', pos: 6000 });
  }
  if (s3InProgress) {
    console.log(`-> Đổi tên "${s3InProgress.name}" -> "Sprint 04 - In Progress" (pos: 7000)...`);
    await trello(`lists/${s3InProgress.id}`, 'PUT', { name: 'Sprint 04 - In Progress', pos: 7000 });
  }
  if (s3Review) {
    console.log(`-> Đổi tên "${s3Review.name}" -> "Sprint 04 - Review" (pos: 8000)...`);
    await trello(`lists/${s3Review.id}`, 'PUT', { name: 'Sprint 04 - Review', pos: 8000 });
  }
  if (s3QA) {
    console.log(`-> Đổi tên "${s3QA.name}" -> "Sprint 04 - QA" (pos: 9000)...`);
    await trello(`lists/${s3QA.id}`, 'PUT', { name: 'Sprint 04 - QA', pos: 9000 });
  }

  // 6. Tạo danh sách "Sprint 04 - Done" nếu chưa có (pos: 10000)
  if (!s4Done) {
    console.log(`-> Tạo mới danh sách "Sprint 04 - Done" (pos: 10000)...`);
    s4Done = await trello('lists', 'POST', {
      name: 'Sprint 04 - Done',
      idBoard: boardId,
      pos: 10000,
    });
    console.log(`   Đã tạo danh sách "Sprint 04 - Done" với ID: ${s4Done.id}`);
  } else {
    console.log(`-> Cập nhật vị trí "Sprint 04 - Done" về pos: 10000...`);
    await trello(`lists/${s4Done.id}`, 'PUT', { pos: 10000 });
  }

  // Cập nhật lại vị trí các Done cũ
  if (s3Done) {
    await trello(`lists/${s3Done.id}`, 'PUT', { pos: 10500 });
  }

  // 7. Tạo SPRINT-GOAL cho Sprint 04 ở đầu Sprint 04 - Backlog
  if (s4Backlog) {
    const s4Cards = await trello(`lists/${s4Backlog.id}/cards`);
    const hasGoal = s4Cards.some(c => c.name.includes('SPRINT-GOAL'));
    if (!hasGoal) {
      console.log(`-> Tạo thẻ [SPRINT-GOAL] cho Sprint 04...`);
      const goalDesc = `## 🎯 MỤC TIÊU SPRINT 04 (15/11/2026 - 22/11/2026)
- **Tổng dung lượng:** 25 Tasks | 79 Story Points (STP)
- **Trọng tâm Sprint 04:**
  1. Hoàn thiện kiểm thử E2E & Zero-Mock toàn diện trên smart-parking-app-v2.
  2. Bắt lỗi và gia cố luồng giữ chỗ 10 phút Hold TTL & Barie confirm-cash.
  3. Triển khai Bàn trọng tài Tranh chấp & Sổ cái đối soát tự động hoàn tiền.
  4. Chuẩn hóa phân quyền, hồ sơ người dùng & tài liệu API.

## 👥 Phân bổ thành viên:
- **Lâm Gia Huy:** 6 Tasks (19 STP) — Pricing Matrix, QR Camera, Landing Live Map, Fix Contract
- **Hoàng Đình Tùng:** 6 Tasks (15 STP) — Bàn trọng tài Admin, DevPortal Docs, 403 UX, Real-time Status
- **Huỳnh La Tiến Lộc:** 6 Tasks (25 STP) — Phán quyết Trọng tài, Auto-Refund SAGA, Sổ cái kép & Analytics
- **Hồ Quốc Khải:** 7 Tasks (20 STP) — Hàng đợi ảo, Khiếu nại Merchant, Quản lý bãi đỗ xe`;

      await trello('cards', 'POST', {
        idList: s4Backlog.id,
        name: '[SPRINT-GOAL] Sprint 04: Bắt Lỗi Hoàn Thiện, E2E Testing & Nghiệp Vụ Bàn Trọng Tài (79 STP)',
        desc: goalDesc,
        pos: 'top',
      });
      console.log(`   Đã tạo thẻ [SPRINT-GOAL] Sprint 04 thành công.`);
    }
  }

  console.log('\n✅ Hoàn tất chuyển đổi bảng sang SPRINT 04!');
}

main().catch(err => {
  console.error('\n❌ Thất bại:', err);
  process.exit(1);
});
