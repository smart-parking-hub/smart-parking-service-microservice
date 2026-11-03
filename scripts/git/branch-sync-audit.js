#!/usr/bin/env node

/**
 * Git Branch Synchronization & Health Audit Tool
 * SmartParkingHub Microservices
 * 
 * Kiểm tra toàn diện tất cả các nhánh local:
 * 1. Commit chưa push lên remote (ahead)
 * 2. Commit trên remote chưa pull về (behind)
 * 3. Nhánh chưa cấu hình upstream
 * 4. Nhánh đã được merge an toàn vào main (có thể dọn dẹp)
 * 5. Tình trạng working directory (uncommitted changes)
 */

import { execSync } from 'node:child_process';

const colors = {
  reset: '\x1b[0m',
  bold: '\x1b[1m',
  dim: '\x1b[2m',
  green: '\x1b[32m',
  yellow: '\x1b[33m',
  red: '\x1b[31m',
  cyan: '\x1b[36m',
  blue: '\x1b[34m',
  magenta: '\x1b[35m',
};

function run(cmd, options = {}) {
  try {
    return execSync(cmd, { encoding: 'utf-8', stdio: ['pipe', 'pipe', 'pipe'], ...options }).trim();
  } catch (err) {
    if (options.throwOnError) throw err;
    return '';
  }
}

console.log(`\n${colors.bold}${colors.cyan}========================================================================${colors.reset}`);
console.log(`${colors.bold}${colors.cyan}   SMARTPARKINGHUB — GIT BRANCH HEALTH & SYNC AUDIT TOOL               ${colors.reset}`);
console.log(`${colors.bold}${colors.cyan}========================================================================${colors.reset}\n`);

// 1. Fetch all with prune (nếu không có cờ --no-fetch)
const skipFetch = process.argv.includes('--no-fetch');
if (!skipFetch) {
  process.stdout.write(`${colors.dim}Đang cập nhật metadata từ remote (git fetch --all --prune)... ${colors.reset}`);
  try {
    run('git fetch --all --prune');
    console.log(`${colors.green}✓ Hoàn tất${colors.reset}\n`);
  } catch {
    console.log(`${colors.yellow}⚠ Không thể kết nối remote (tiếp tục kiểm tra local)${colors.reset}\n`);
  }
}

// 2. Check current branch & working tree
const workingTreeStatus = run('git status --short');
const currentBranch = run('git rev-parse --abbrev-ref HEAD');

console.log(`${colors.bold}Nhánh đang làm việc:${colors.reset} ${colors.green}${colors.bold}${currentBranch}${colors.reset}`);
if (workingTreeStatus) {
  console.log(`${colors.yellow}⚠ Cảnh báo: Có thay đổi chưa commit trong working directory:${colors.reset}`);
  workingTreeStatus.split('\n').forEach((line) => {
    console.log(`  ${colors.yellow}${line}${colors.reset}`);
  });
} else {
  console.log(`${colors.green}✓ Working directory sạch sẽ (không có thay đổi dở dang).${colors.reset}`);
}
console.log('');

// 3. Get all local branches with upstream info via git for-each-ref
const rawRefData = run('git for-each-ref --format="%(refname:short)|%(upstream:short)|%(upstream:track)" refs/heads/');
const lines = rawRefData ? rawRefData.split('\n').map((l) => l.trim()).filter(Boolean) : [];

if (lines.length === 0) {
  console.log(`${colors.red}Không tìm thấy nhánh local nào!${colors.reset}\n`);
  process.exit(0);
}

// Check merged branches into main
const mergedIntoMain = run('git branch --merged main')
  .split('\n')
  .map((b) => b.replace('*', '').trim().replace(/^['"]|['"]$/g, ''))
  .filter(Boolean);

const reports = [];

for (const line of lines) {
  const parts = line.split('|');
  const branch = parts[0]?.trim() || '';
  const upstream = parts[1]?.trim() || null;
  const isCurrent = branch === currentBranch;

  let ahead = 0;
  let behind = 0;
  let aheadLogs = [];
  let behindLogs = [];

  if (upstream) {
    const aheadCountStr = run(`git rev-list --count "${upstream}..${branch}"`);
    ahead = parseInt(aheadCountStr || '0', 10);

    const behindCountStr = run(`git rev-list --count "${branch}..${upstream}"`);
    behind = parseInt(behindCountStr || '0', 10);

    if (ahead > 0) {
      const logs = run(`git log --oneline "${upstream}..${branch}"`);
      aheadLogs = logs ? logs.split('\n').filter(Boolean) : [];
    }

    if (behind > 0) {
      const logs = run(`git log --oneline "${branch}..${upstream}"`);
      behindLogs = logs ? logs.split('\n').filter(Boolean) : [];
    }
  }

  const isMerged = branch !== 'main' && mergedIntoMain.includes(branch);

  const lastCommitRaw = run(`git log -1 --format="%h|%cr|%s" "refs/heads/${branch}"`);
  const [lastHash, lastRel, ...lastMsgParts] = lastCommitRaw.split('|');
  const lastSubject = lastMsgParts.join('|') || '';

  reports.push({
    branch,
    isCurrent,
    upstream,
    ahead,
    behind,
    isMergedIntoMain: isMerged,
    aheadLogs,
    behindLogs,
    lastHash,
    lastRel,
    lastSubject,
  });
}

// 4. Print Summary Table
console.log(`${colors.bold}BẢNG TỔNG HỢP ĐỒNG BỘ CÁC NHÁNH:${colors.reset}`);
console.log(`${'-'.repeat(110)}`);
console.log(
  `${colors.bold}${'TÊN NHÁNH'.padEnd(36)} ${'UPSTREAM'.padEnd(20)} ${'TRẠNG THÁI'.padEnd(26)} ${'COMMIT MỚI NHẤT'}${colors.reset}`
);
console.log(`${'-'.repeat(110)}`);

let hasIssues = false;
const branchesNeedingPush = [];
const branchesNeedingPull = [];
const mergedBranches = [];

for (const r of reports) {
  const prefix = r.isCurrent ? `${colors.green}* ` : '  ';
  const name = `${prefix}${r.branch}${colors.reset}`.padEnd(r.isCurrent ? 45 : 36);
  const upstream = (r.upstream ? `${colors.dim}${r.upstream}${colors.reset}` : `${colors.yellow}[Chưa có]${colors.reset}`).padEnd(r.upstream ? 29 : 20);

  let statusText = '';
  if (!r.upstream) {
    statusText = `${colors.yellow}⚠ Chưa set upstream${colors.reset}`;
    hasIssues = true;
  } else if (r.ahead > 0 && r.behind > 0) {
    statusText = `${colors.red}⚠ Lệch: ↑${r.ahead} ↓${r.behind}${colors.reset}`;
    hasIssues = true;
    branchesNeedingPush.push(r);
    branchesNeedingPull.push(r);
  } else if (r.ahead > 0) {
    statusText = `${colors.yellow}↑ Chưa push: ${r.ahead} commit${colors.reset}`;
    hasIssues = true;
    branchesNeedingPush.push(r);
  } else if (r.behind > 0) {
    statusText = `${colors.blue}↓ Cần pull: ${r.behind} commit${colors.reset}`;
    hasIssues = true;
    branchesNeedingPull.push(r);
  } else {
    statusText = `${colors.green}✓ Đồng bộ 100%${colors.reset}`;
  }

  if (r.isMergedIntoMain) {
    statusText += ` ${colors.magenta}[Đã merge]${colors.reset}`;
    mergedBranches.push(r.branch);
  }

  const truncatedSubject = r.lastSubject.length > 38 ? `${r.lastSubject.slice(0, 35)}...` : r.lastSubject;
  const commitSnippet = r.lastHash ? `${colors.cyan}${r.lastHash}${colors.reset} ${colors.dim}(${r.lastRel}):${colors.reset} ${truncatedSubject}` : '';

  console.log(`${name} ${upstream} ${statusText.padEnd(35)} ${commitSnippet}`);
}
console.log(`${'-'.repeat(110)}\n`);

// 5. Chi tiết các commit chưa push
if (branchesNeedingPush.length > 0) {
  console.log(`${colors.bold}${colors.yellow}=== CHI TIẾT CÁC COMMIT CHƯA PUSH LÊN REMOTE (AHEAD) ===${colors.reset}`);
  for (const b of branchesNeedingPush) {
    console.log(`\n${colors.bold}Nhánh: ${colors.yellow}${b.branch}${colors.reset} (${b.ahead} commit chưa push lên ${b.upstream}):`);
    b.aheadLogs.forEach((log) => console.log(`  ${colors.yellow}↑${colors.reset} ${log}`));
    console.log(`  ${colors.dim}Gợi ý lệnh push:${colors.reset} ${colors.cyan}git push origin ${b.branch}${colors.reset}`);
  }
  console.log('');
}

// 6. Chi tiết các commit trên remote chưa kéo về
if (branchesNeedingPull.length > 0) {
  console.log(`${colors.bold}${colors.blue}=== CHI TIẾT CÁC COMMIT MỚI TRÊN REMOTE CHƯA PULL (BEHIND) ===${colors.reset}`);
  for (const b of branchesNeedingPull) {
    console.log(`\n${colors.bold}Nhánh: ${colors.blue}${b.branch}${colors.reset} (${b.behind} commit mới trên ${b.upstream}):`);
    b.behindLogs.forEach((log) => console.log(`  ${colors.blue}↓${colors.reset} ${log}`));
    console.log(`  ${colors.dim}Gợi ý lệnh pull:${colors.reset} ${colors.cyan}git checkout ${b.branch} && git pull${colors.reset}`);
  }
  console.log('');
}

// 7. Danh sách nhánh đã merge xong
if (mergedBranches.length > 0) {
  console.log(`${colors.bold}${colors.magenta}=== CÁC NHÁNH ĐÃ MERGE XONG VÀO MAIN (CÓ THỂ DỌN DẸP) ===${colors.reset}`);
  mergedBranches.forEach((mb) => {
    console.log(`  ${colors.magenta}• ${mb}${colors.reset} -> ${colors.dim}git branch -d ${mb}${colors.reset}`);
  });
  console.log('');
}

// 8. Tổng kết
if (!hasIssues) {
  console.log(`${colors.green}${colors.bold}🎉 TẤT CẢ CÁC NHÁNH ĐÃ ĐỒNG BỘ HOÀN TOÀN VỚI REMOTE!${colors.reset}\n`);
} else {
  console.log(`${colors.cyan}${colors.bold}💡 HÀNH ĐỘNG KHUYẾN NGHỊ:${colors.reset}`);
  if (branchesNeedingPush.length > 0) {
    console.log(`- Bạn có ${colors.yellow}${branchesNeedingPush.length} nhánh${colors.reset} có commit mới ở local chưa đẩy lên GitHub.`);
  }
  if (branchesNeedingPull.length > 0) {
    console.log(`- Bạn có ${colors.blue}${branchesNeedingPull.length} nhánh${colors.reset} có cập nhật từ remote cần kéo về.`);
  }
  if (mergedBranches.length > 0) {
    console.log(`- Bạn có ${colors.magenta}${mergedBranches.length} nhánh${colors.reset} đã merge vào main, có thể xóa gọn bớt nếu không dùng nữa.`);
  }
  console.log('');
}
