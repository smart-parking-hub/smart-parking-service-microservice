#!/usr/bin/env node

/**
 * Git Commit Deep Audit & Branch Health Inspection Tool
 * SmartParkingHub Microservices
 * 
 * Kiểm tra chuyên sâu tất cả commit của từng nhánh:
 * 1. Commit mới nhất (Hash, Tác giả, Thời gian tương đối, Message)
 * 2. So sánh tính toàn vẹn giữa Local HEAD và Remote HEAD
 * 3. So sánh khoảng cách với nhánh `main` (Ahead of main / Behind main)
 * 4. Liệt kê Timeline 3-5 commit gần nhất của từng nhánh
 * 5. Kiểm tra chuẩn Conventional Commits
 * 6. Đưa ra chứng chỉ "Trạng thái sẵn sàng (Ready to code)"
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
  gray: '\x1b[90m',
};

function run(cmd, options = {}) {
  try {
    return execSync(cmd, { encoding: 'utf-8', stdio: ['pipe', 'pipe', 'pipe'], ...options }).trim();
  } catch (err) {
    if (options.throwOnError) throw err;
    return '';
  }
}

// Parse CLI flags
const args = process.argv.slice(2);
const skipFetch = args.includes('--no-fetch');
const showAll = args.includes('--all');
const targetBranchArg = args.find((a) => a.startsWith('--branch='))?.split('=')[1] || null;
const countArg = parseInt(args.find((a) => a.startsWith('--count='))?.split('=')[1] || '4', 10);

console.log(`\n${colors.bold}${colors.cyan}========================================================================================${colors.reset}`);
console.log(`${colors.bold}${colors.cyan}   SMARTPARKINGHUB — GIT COMMIT AUDIT & HEALTH VERIFICATION TOOL                        ${colors.reset}`);
console.log(`${colors.bold}${colors.cyan}========================================================================================${colors.reset}\n`);

// 1. Fetch metadata từ remote
if (!skipFetch) {
  process.stdout.write(`${colors.dim}Đang đồng bộ metadata mới nhất từ GitHub (git fetch --all --prune)... ${colors.reset}`);
  try {
    run('git fetch --all --prune');
    console.log(`${colors.green}✓ Xong${colors.reset}\n`);
  } catch {
    console.log(`${colors.yellow}⚠ Không thể kết nối remote (dùng cache local)${colors.reset}\n`);
  }
}

// 2. Tình trạng working directory & Current branch
const workingTreeStatus = run('git status --short');
const currentBranch = run('git rev-parse --abbrev-ref HEAD');

console.log(`${colors.bold}Nhánh đang làm việc:${colors.reset} ${colors.green}${colors.bold}${currentBranch}${colors.reset}`);
if (workingTreeStatus) {
  console.log(`${colors.yellow}⚠ CẢNH BÁO: Còn thay đổi chưa commit trong working directory:${colors.reset}`);
  workingTreeStatus.split('\n').forEach((l) => console.log(`   ${colors.yellow}${l}${colors.reset}`));
} else {
  console.log(`${colors.green}✓ Working directory sạch 100% (Working tree clean).${colors.reset}`);
}
console.log('');

// 3. Lấy danh sách nhánh local
const rawRefData = run('git for-each-ref --format="%(refname:short)|%(upstream:short)" refs/heads/');
const branchLines = rawRefData ? rawRefData.split('\n').map((l) => l.trim()).filter(Boolean) : [];

if (branchLines.length === 0) {
  console.log(`${colors.red}Không tìm thấy nhánh local nào!${colors.reset}\n`);
  process.exit(0);
}

// Danh sách nhánh đã merge vào main
const mergedIntoMain = run('git branch --merged main')
  .split('\n')
  .map((b) => b.replace('*', '').trim().replace(/^['"]|['"]$/g, ''))
  .filter(Boolean);

const conventionalRegex = /^(feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert)(\([a-zA-Z0-9_\-./]+\))?: .+/;

const branchReports = [];

for (const line of branchLines) {
  const [branch, upstream] = line.split('|').map((s) => s.trim());
  if (targetBranchArg && branch !== targetBranchArg) continue;

  const isCurrent = branch === currentBranch;
  const isMerged = branch !== 'main' && mergedIntoMain.includes(branch);

  // Commit mới nhất của local branch
  const lastCommitRaw = run(`git log -1 --format="%h|%an|%cr|%cd|%s" "refs/heads/${branch}"`);
  const [shortHash, author, relTime, dateStr, ...msgParts] = lastCommitRaw.split('|');
  const subject = msgParts.join('|') || '';

  // Commit mới nhất của upstream (nếu có)
  let upstreamShortHash = null;
  let upstreamRelTime = null;
  let upstreamSubject = null;
  let ahead = 0;
  let behind = 0;
  let aheadCommits = [];
  let behindCommits = [];

  if (upstream) {
    const upstreamCommitRaw = run(`git log -1 --format="%h|%cr|%s" "${upstream}"`);
    if (upstreamCommitRaw) {
      const [uHash, uRel, ...uMsgParts] = upstreamCommitRaw.split('|');
      upstreamShortHash = uHash;
      upstreamRelTime = uRel;
      upstreamSubject = uMsgParts.join('|') || '';
    }

    const aheadCountStr = run(`git rev-list --count "${upstream}..${branch}"`);
    ahead = parseInt(aheadCountStr || '0', 10);

    const behindCountStr = run(`git rev-list --count "${branch}..${upstream}"`);
    behind = parseInt(behindCountStr || '0', 10);

    if (ahead > 0) {
      const logs = run(`git log --oneline "${upstream}..${branch}"`);
      aheadCommits = logs ? logs.split('\n').filter(Boolean) : [];
    }

    if (behind > 0) {
      const logs = run(`git log --oneline "${branch}..${upstream}"`);
      behindCommits = logs ? logs.split('\n').filter(Boolean) : [];
    }
  }

  // So sánh với main
  let aheadOfMain = 0;
  let behindMain = 0;
  let uniqueCommitsVsMain = [];
  if (branch !== 'main') {
    const aheadMainStr = run(`git rev-list --count "main..${branch}"`);
    aheadOfMain = parseInt(aheadMainStr || '0', 10);

    const behindMainStr = run(`git rev-list --count "${branch}..main"`);
    behindMain = parseInt(behindMainStr || '0', 10);

    if (aheadOfMain > 0 && aheadOfMain <= 8) {
      const uLogs = run(`git log --oneline "main..${branch}"`);
      uniqueCommitsVsMain = uLogs ? uLogs.split('\n').filter(Boolean) : [];
    }
  }

  // Lấy N commit gần nhất của nhánh
  const recentLogsRaw = run(`git log -n ${countArg} --format="%h|%an|%cr|%s" "refs/heads/${branch}"`);
  const recentCommits = recentLogsRaw
    ? recentLogsRaw.split('\n').filter(Boolean).map((l) => {
        const [h, a, r, ...m] = l.split('|');
        return { hash: h, author: a, relTime: r, subject: m.join('|') };
      })
    : [];

  const isConventional = conventionalRegex.test(subject);

  branchReports.push({
    branch,
    isCurrent,
    isMerged,
    upstream,
    shortHash,
    author,
    relTime,
    dateStr,
    subject,
    upstreamShortHash,
    upstreamRelTime,
    upstreamSubject,
    ahead,
    behind,
    aheadCommits,
    behindCommits,
    aheadOfMain,
    behindMain,
    uniqueCommitsVsMain,
    recentCommits,
    isConventional,
  });
}

// 4. BẢNG TỔNG HỢP COMMIT CỦA TẤT CẢ CÁC NHÁNH
console.log(`${colors.bold}BẢNG TỔNG QUAN COMMIT TẤT CẢ CÁC NHÁNH:${colors.reset}`);
console.log(`${'─'.repeat(108)}`);
console.log(
  `${colors.bold}${'NHÁNH'.padEnd(36)} ${'HASH'.padEnd(9)} ${'THỜI GIAN'.padEnd(16)} ${'ĐỒNG BỘ REMOTE'.padEnd(20)} ${'SO VỚI MAIN'.padEnd(14)} ${'LATEST COMMIT'}${colors.reset}`
);
console.log(`${'─'.repeat(108)}`);

for (const b of branchReports) {
  const currentMark = b.isCurrent ? `${colors.green}* ` : '  ';
  const nameStr = `${currentMark}${b.branch}${colors.reset}`.padEnd(b.isCurrent ? 45 : 36);
  const hashStr = `${colors.cyan}${b.shortHash || '-------'}${colors.reset}`.padEnd(18);
  const timeStr = `${colors.dim}${b.relTime || '-'}${colors.reset}`.padEnd(25);

  let syncStr = '';
  if (!b.upstream) {
    syncStr = `${colors.yellow}[Chưa upstream]${colors.reset}`;
  } else if (b.ahead > 0 && b.behind > 0) {
    syncStr = `${colors.red}↑${b.ahead} ↓${b.behind}${colors.reset}`;
  } else if (b.ahead > 0) {
    syncStr = `${colors.yellow}↑ Chưa push (${b.ahead})${colors.reset}`;
  } else if (b.behind > 0) {
    syncStr = `${colors.blue}↓ Cần pull (${b.behind})${colors.reset}`;
  } else {
    syncStr = `${colors.green}✓ Khớp 100%${colors.reset}`;
  }
  syncStr = syncStr.padEnd(29);

  let mainStr = '';
  if (b.branch === 'main') {
    mainStr = `${colors.bold}[Nhánh gốc]${colors.reset}`;
  } else if (b.isMerged) {
    mainStr = `${colors.magenta}Đã merge${colors.reset}`;
  } else if (b.aheadOfMain > 0) {
    mainStr = `${colors.yellow}+${b.aheadOfMain} commit${colors.reset}`;
  } else {
    mainStr = `${colors.dim}Ngang bằng${colors.reset}`;
  }
  mainStr = mainStr.padEnd(23);

  const truncatedSubject = b.subject.length > 40 ? `${b.subject.slice(0, 37)}...` : b.subject;
  const convIcon = b.isConventional ? `${colors.green}✓${colors.reset}` : `${colors.yellow}•${colors.reset}`;
  const commitStr = `${convIcon} ${truncatedSubject}`;

  console.log(`${nameStr} ${hashStr} ${timeStr} ${syncStr} ${mainStr} ${commitStr}`);
}
console.log(`${'─'.repeat(108)}\n`);

// 5. CHI TIẾT COMMIT TIMELINE CHO NHÁNH ĐANG LÀM VIỆC & NHÁNH ACTIVE CHƯA MERGE
const activeBranchesToDetail = branchReports.filter(
  (b) => b.isCurrent || (b.branch !== 'main' && (!b.isMerged || showAll || b.ahead > 0 || b.behind > 0))
);

console.log(`${colors.bold}${colors.cyan}=== CHI TIẾT COMMIT MỚI NHẤT & LỊCH SỬ TỪNG NHÁNH ACTIVE ===${colors.reset}\n`);

for (const b of activeBranchesToDetail) {
  const badge = b.isCurrent
    ? `${colors.green}[ĐANG LÀM VIỆC]${colors.reset}`
    : b.isMerged
      ? `${colors.magenta}[ĐÃ MERGE MAIN]${colors.reset}`
      : `${colors.yellow}[FEATURE ACTIVE]${colors.reset}`;

  console.log(`${colors.bold}📌 Nhánh: ${colors.cyan}${b.branch}${colors.reset} ${badge}`);
  console.log(`   ${colors.dim}Upstream:${colors.reset} ${b.upstream || 'Không có'} | ${colors.dim}Tác giả:${colors.reset} ${b.author}`);

  // So sánh hash
  if (b.upstream) {
    const isExactMatch = b.shortHash === b.upstreamShortHash;
    if (isExactMatch) {
      console.log(`   ${colors.green}✓ Trạng thái hash: Local [${b.shortHash}] === Remote [${b.upstreamShortHash}] (Đồng bộ tuyệt đối)${colors.reset}`);
    } else {
      console.log(`   ${colors.yellow}⚠ Lệch hash: Local [${b.shortHash}] != Remote [${b.upstreamShortHash || 'N/A'}]${colors.reset}`);
    }
  }

  // Commit mới nhất
  console.log(`   ${colors.bold}Commit mới nhất (HEAD):${colors.reset}`);
  console.log(`     • Hash:    ${colors.cyan}${b.shortHash}${colors.reset} (${colors.dim}${b.relTime}${colors.reset})`);
  console.log(`     • Tiêu đề: ${colors.bold}${b.subject}${colors.reset}`);
  console.log(`     • Chuẩn:   ${b.isConventional ? `${colors.green}✓ Hợp chuẩn Conventional Commits${colors.reset}` : `${colors.yellow}⚠ Chưa đúng format conventional (e.g. feat(...): ...)${colors.reset}`}`);

  // Nếu có commit chưa push
  if (b.ahead > 0) {
    console.log(`   ${colors.yellow}↑ ${b.ahead} commit chưa push lên remote:${colors.reset}`);
    const displayAhead = b.aheadCommits.slice(0, 6);
    displayAhead.forEach((c) => console.log(`       ${colors.yellow}↑${colors.reset} ${c}`));
    if (b.ahead > 6) {
      console.log(`       ${colors.dim}... và ${b.ahead - 6} commit khác (chạy git log để xem hết)${colors.reset}`);
    }
  }

  // Nếu có commit chưa pull
  if (b.behind > 0) {
    console.log(`   ${colors.blue}↓ ${b.behind} commit mới trên remote cần pull:${colors.reset}`);
    const displayBehind = b.behindCommits.slice(0, 6);
    displayBehind.forEach((c) => console.log(`       ${colors.blue}↓${colors.reset} ${c}`));
    if (b.behind > 6) {
      console.log(`       ${colors.dim}... và ${b.behind - 6} commit khác (chạy git log để xem hết)${colors.reset}`);
    }
  }

  // Các commit riêng so với main (nếu có)
  if (b.aheadOfMain > 0 && b.uniqueCommitsVsMain.length > 0) {
    console.log(`   ${colors.dim}Các commit riêng của nhánh so với main (${b.aheadOfMain} commit):${colors.reset}`);
    b.uniqueCommitsVsMain.forEach((c) => console.log(`       ${colors.gray}• ${c}${colors.reset}`));
  }

  // 3-4 commit gần nhất
  console.log(`   ${colors.dim}Lịch sử commit gần đây:${colors.reset}`);
  b.recentCommits.forEach((c, idx) => {
    const isLast = idx === b.recentCommits.length - 1;
    const branchChar = isLast ? '└─' : '├─';
    console.log(`     ${colors.gray}${branchChar}${colors.reset} [${colors.cyan}${c.hash}${colors.reset}] ${colors.dim}(${c.relTime})${colors.reset} ${c.subject}`);
  });
  console.log('');
}

// 6. KẾT LUẬN & CHỨNG NHẬN TRẠNG THÁI TỐT NHẤT
const currentBranchReport = branchReports.find((b) => b.isCurrent);
let isReadyToCode = true;
const blockers = [];

if (workingTreeStatus) {
  isReadyToCode = false;
  blockers.push('Working directory còn file chưa commit.');
}
if (currentBranchReport && currentBranchReport.ahead > 0) {
  isReadyToCode = false;
  blockers.push(`Nhánh hiện tại [${currentBranchReport.branch}] có ${currentBranchReport.ahead} commit chưa push lên GitHub.`);
}
if (currentBranchReport && currentBranchReport.behind > 0) {
  isReadyToCode = false;
  blockers.push(`Nhánh hiện tại [${currentBranchReport.branch}] bị trễ ${currentBranchReport.behind} commit so với remote, cần pull.`);
}

console.log(`${'─'.repeat(108)}`);
if (isReadyToCode) {
  console.log(`${colors.green}${colors.bold}🏆 XÁC NHẬN: REPOSITORY ĐANG Ở TRẠNG THÁI TỐT NHẤT (100% READY TO WORK)!${colors.reset}`);
  console.log(`   • Nhánh hiện tại: ${colors.cyan}${colors.bold}${currentBranch}${colors.reset}`);
  console.log(`   • Commit HEAD:    ${colors.cyan}${currentBranchReport?.shortHash}${colors.reset} — ${currentBranchReport?.subject}`);
  console.log(`   • Remote sync:    ${colors.green}✓ Trùng khớp hoàn toàn với origin/${currentBranch}${colors.reset}`);
  console.log(`   • Working tree:   ${colors.green}✓ Sạch sẽ, không có thay đổi dở dang.${colors.reset}\n`);
} else {
  console.log(`${colors.yellow}${colors.bold}⚠ CẦN XỬ LÝ TRƯỚC KHI BẮT ĐẦU CODE MỚI:${colors.reset}`);
  blockers.forEach((blk) => console.log(`   • ${colors.yellow}${blk}${colors.reset}`));
  console.log('');
}
