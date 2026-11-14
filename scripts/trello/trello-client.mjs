/**
 * Shared Trello Client & Configuration Loader
 * Phục vụ các scripts tự động hóa Trello trong scripts/trello/
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Tìm kiếm file .trello.env ở các vị trí khả dĩ
function findTrelloEnv() {
  const customPath = process.env.TRELLO_ENV_FILE;
  if (customPath && fs.existsSync(customPath)) {
    return path.resolve(customPath);
  }

  const candidatePaths = [
    path.resolve(process.cwd(), '.trello.env'),
    path.resolve(process.cwd(), 'smart-parking-microservice', '.trello.env'),
    path.resolve(__dirname, '.trello.env'),
    path.resolve(__dirname, '..', '.trello.env'),
    path.resolve(__dirname, '..', '..', '.trello.env'),
    path.resolve(__dirname, '..', '..', '..', '.trello.env')
  ];

  for (const candidate of candidatePaths) {
    if (fs.existsSync(candidate)) {
      return candidate;
    }
  }
  return null;
}

function loadTrelloConfig() {
  const envPath = findTrelloEnv();
  const envVars = { ...process.env };

  if (envPath) {
    try {
      const content = fs.readFileSync(envPath, 'utf-8');
      for (const line of content.split('\n')) {
        const trimmed = line.trim();
        if (trimmed && !trimmed.startsWith('#') && trimmed.includes('=')) {
          const idx = trimmed.indexOf('=');
          const k = trimmed.substring(0, idx).trim();
          const v = trimmed.substring(idx + 1).trim();
          if (k) envVars[k] = v;
        }
      }
    } catch (err) {
      console.error(`⚠️ Lỗi khi đọc file cấu hình tại ${envPath}:`, err.message);
    }
  }

  const key = envVars.TRELLO_API_KEY;
  const token = envVars.TRELLO_API_TOKEN;
  const boardId = envVars.TRELLO_ACTIVE_BOARD_ID || envVars.TRELLO_SPH_BOARD_ID;

  if (!key || !token || !boardId) {
    console.error('❌ Lỗi cấu hình Trello API:');
    if (!envPath) {
      console.error('   Không tìm thấy file .trello.env!');
      console.error('   Vui lòng tạo file .trello.env từ template scripts/trello/.trello.env.example:');
      console.error('     cp scripts/trello/.trello.env.example .trello.env');
    } else {
      console.error(`   File cấu hình: ${envPath}`);
      if (!key) console.error('   - Thiếu biến TRELLO_API_KEY');
      if (!token) console.error('   - Thiếu biến TRELLO_API_TOKEN');
      if (!boardId) console.error('   - Thiếu biến TRELLO_ACTIVE_BOARD_ID hoặc TRELLO_SPH_BOARD_ID');
    }
    process.exit(1);
  }

  return { key, token, boardId, envPath, env: envVars };
}

const config = loadTrelloConfig();

export const key = config.key;
export const token = config.token;
export const boardId = config.boardId;
export const envPath = config.envPath;
export const env = config.env;

export const MEMBERS = {
  HUY: '69016d7a8c58ffe25753e224',
  TUNG: '6930e5b55d3528613be81a82',
  LOC: '68f0354b7e270498cfc5666a',
  KHAI: '6a6b571e2274ff581eb61742'
};

export default {
  key,
  token,
  boardId,
  envPath,
  env,
  MEMBERS
};
