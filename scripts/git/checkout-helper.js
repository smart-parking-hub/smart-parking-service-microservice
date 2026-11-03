#!/usr/bin/env node

/**
 * Interactive Git Checkout & Branch Management Helper
 * SmartParkingHub Microservices
 * 
 * Hỗ trợ chuyển nhánh an toàn, nhanh chóng và thông minh:
 * - Chọn nhánh theo số thứ tự (1, 2, 3...) hoặc gõ từ khóa tìm kiếm
 * - Tự động phát hiện trạng thái đồng bộ (Ahead, Behind, In Sync)
 * - Cảnh báo và hỗ trợ auto-stash nếu working directory đang dở dang
 * - Tự động pull cập nhật mới nhất từ remote sau khi checkout
 */

import { execSync } from 'node:child_process';
import readline from 'node:readline';

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

function ask(question) {
  const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout,
  });
  return new Promise((resolve) => {
    rl.question(question, (answer) => {
      rl.close();
      resolve(answer.trim());
    });
  });
}

async function main() {
  console.log(`\n${colors.bold}${colors.cyan}========================================================================${colors.reset}`);
  console.log(`${colors.bold}${colors.cyan}   SMARTPARKINGHUB — SMART GIT CHECKOUT HELPER                         ${colors.reset}`);
  console.log(`${colors.bold}${colors.cyan}========================================================================${colors.reset}\n`);

  const currentBranch = run('git rev-parse --abbrev-ref HEAD');
  const workingTreeStatus = run('git status --short');

  // 1. Kiểm tra working directory
  if (workingTreeStatus) {
    console.log(`${colors.yellow}⚠ CẢNH BÁO: Working directory đang có thay đổi chưa commit:${colors.reset}`);
    workingTreeStatus.split('\n').forEach((l) => console.log(`   ${colors.yellow}${l}${colors.reset}`));
    console.log('');
    console.log(`Lựa chọn xử lý an toàn:`);
    console.log(`  [1] Tự động lưu tạm vào Stash (git stash) rồi mới chuyển nhánh`);
    console.log(`  [2] Cố gắng chuyển nhánh (nếu không xung đột)`);
    console.log(`  [0] Hủy bỏ thao tác`);
    const action = await ask(`\nNhập lựa chọn [1/2/0] (mặc định: 1): `);
    if (action === '0') {
      console.log(`${colors.dim}Đã hủy thao tác checkout.${colors.reset}\n`);
      process.exit(0);
    }
    if (action === '1' || action === '') {
      const stashMsg = `Auto-stash before checkout at ${new Date().toLocaleTimeString()}`;
      run(`git stash push -m "${stashMsg}"`);
      console.log(`${colors.green}✓ Đã lưu tạm các thay đổi vào stash ("${stashMsg}").${colors.reset}\n`);
    }
  }

  // 2. Lấy danh sách nhánh local
  const rawLocal = run('git for-each-ref --format="%(refname:short)|%(upstream:short)" refs/heads/');
  const localLines = rawLocal ? rawLocal.split('\n').map((l) => l.trim()).filter(Boolean) : [];

  const branches = [];
  for (const l of localLines) {
    const [name, upstream] = l.split('|').map((s) => s.trim());
    let ahead = 0;
    let behind = 0;
    if (upstream) {
      const aStr = run(`git rev-list --count "${upstream}..${name}"`);
      const bStr = run(`git rev-list --count "${name}..${upstream}"`);
      ahead = parseInt(aStr || '0', 10);
      behind = parseInt(bStr || '0', 10);
    }
    branches.push({ name, upstream, ahead, behind, isCurrent: name === currentBranch });
  }

  // 3. Nếu người dùng truyền argument từ khóa trực tiếp
  const queryArg = process.argv.slice(2).find((a) => !a.startsWith('--'));
  if (queryArg) {
    const matched = branches.filter((b) => b.name.toLowerCase().includes(queryArg.toLowerCase()));
    if (matched.length === 1) {
      return performCheckout(matched[0]);
    }
    if (matched.length > 1) {
      console.log(`Tìm thấy ${matched.length} nhánh khớp với từ khóa "${queryArg}":\n`);
      return selectAndCheckout(matched);
    }
    // Thử tìm nhánh remote
    const remoteMatches = run(`git branch -r`)
      .split('\n')
      .map((r) => r.trim().replace(/^origin\//, ''))
      .filter((r) => r && !r.includes('HEAD') && r.toLowerCase().includes(queryArg.toLowerCase()));
    if (remoteMatches.length > 0) {
      console.log(`Tìm thấy nhánh trên remote origin/${remoteMatches[0]}:`);
      run(`git checkout -b ${remoteMatches[0]} origin/${remoteMatches[0]}`);
      console.log(`${colors.green}✓ Đã tạo và chuyển sang nhánh local mới: ${remoteMatches[0]}${colors.reset}\n`);
      return;
    }
    console.log(`${colors.red}Không tìm thấy nhánh nào khớp với "${queryArg}"!${colors.reset}\n`);
  }

  return selectAndCheckout(branches);
}

async function selectAndCheckout(branchList) {
  console.log(`${colors.bold}DANH SÁCH NHÁNH KHẢ DỤNG:${colors.reset}`);
  console.log(`${'-'.repeat(85)}`);

  branchList.forEach((b, idx) => {
    const num = `[${idx + 1}]`.padStart(4);
    const currentMark = b.isCurrent ? `${colors.green}* ${colors.bold}` : '  ';
    const nameStr = `${currentMark}${b.name}${colors.reset}`.padEnd(b.isCurrent ? 48 : 40);

    let status = `${colors.green}🟢 Đồng bộ 100%${colors.reset}`;
    if (!b.upstream) {
      status = `${colors.yellow}⚪ Chưa có upstream${colors.reset}`;
    } else if (b.ahead > 0 && b.behind > 0) {
      status = `${colors.red}🔴 Lệch: ↑${b.ahead} ↓${b.behind}${colors.reset}`;
    } else if (b.ahead > 0) {
      status = `${colors.yellow}🟡 Chưa push: ↑${b.ahead}${colors.reset}`;
    } else if (b.behind > 0) {
      status = `${colors.blue}🔵 Cần pull: ↓${b.behind}${colors.reset}`;
    }

    console.log(`${colors.cyan}${num}${colors.reset} ${nameStr} ${status}`);
  });
  console.log(`${'-'.repeat(85)}\n`);

  const choice = await ask(`Nhập số thứ tự (1-${branchList.length}) hoặc từ khóa tìm kiếm (Enter để hủy): `);
  if (!choice) {
    console.log(`${colors.dim}Đã hủy thao tác.${colors.reset}\n`);
    process.exit(0);
  }

  const num = parseInt(choice, 10);
  if (!isNaN(num) && num >= 1 && num <= branchList.length) {
    return performCheckout(branchList[num - 1]);
  }

  const matched = branchList.filter((b) => b.name.toLowerCase().includes(choice.toLowerCase()));
  if (matched.length === 1) {
    return performCheckout(matched[0]);
  }
  if (matched.length > 1) {
    console.log(`\nCó nhiều nhánh khớp với "${choice}", vui lòng chọn chính xác:`);
    return selectAndCheckout(matched);
  }

  console.log(`${colors.red}Lựa chọn không hợp lệ!${colors.reset}\n`);
}

async function performCheckout(targetBranch) {
  if (targetBranch.isCurrent) {
    console.log(`\n${colors.yellow}Bạn đang ở sẵn trên nhánh "${targetBranch.name}".${colors.reset}`);
  } else {
    process.stdout.write(`\nĐang chuyển sang nhánh ${colors.cyan}${targetBranch.name}${colors.reset}... `);
    try {
      run(`git checkout "${targetBranch.name}"`, { throwOnError: true });
      console.log(`${colors.green}✓ Thành công!${colors.reset}\n`);
    } catch (err) {
      console.log(`${colors.red}✗ Thất bại!${colors.reset}`);
      console.error(err.message);
      process.exit(1);
    }
  }

  // Kiểm tra nếu cần pull
  if (targetBranch.upstream && targetBranch.behind > 0) {
    console.log(`${colors.blue}Nhánh này đang có ${targetBranch.behind} commit mới trên remote chưa pull.${colors.reset}`);
    const autoPull = await ask(`Bạn có muốn tự động pull ngay bây giờ không? [Y/n]: `);
    if (autoPull.toLowerCase() !== 'n') {
      process.stdout.write(`Đang kéo code mới (git pull)... `);
      try {
        run('git pull');
        console.log(`${colors.green}✓ Đã cập nhật code mới nhất!${colors.reset}\n`);
      } catch (err) {
        console.log(`${colors.yellow}⚠ Lỗi pull: ${err.message}${colors.reset}\n`);
      }
    }
  }

  // Hiển thị commit mới nhất của nhánh sau khi checkout
  const lastCommit = run('git log -1 --format="%h — %s (%cr bởi %an)"');
  console.log(`${colors.bold}📌 Nhánh hiện tại:${colors.reset} ${colors.green}${targetBranch.name}${colors.reset}`);
  console.log(`${colors.bold}📝 Commit HEAD:${colors.reset}    ${colors.cyan}${lastCommit}${colors.reset}\n`);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
