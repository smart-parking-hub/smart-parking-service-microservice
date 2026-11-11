import fs from 'node:fs';

const envContent = fs.readFileSync('smart-parking-microservice/.trello.env', 'utf-8');
const env = {};
for (const line of envContent.split('\n')) {
  const trimmed = line.trim();
  if (trimmed && !trimmed.startsWith('#') && trimmed.includes('=')) {
    const idx = trimmed.indexOf('=');
    env[trimmed.substring(0, idx).trim()] = trimmed.substring(idx + 1).trim();
  }
}
const key = env.TRELLO_API_KEY;
const token = env.TRELLO_API_TOKEN;

const cardIds = [
  '6ae174ea57079234fc9fb738', // SP-INP-03
  '6ae36efa4fcde41de1c4e18a', // SP-PK-01-T02
  '6ae1758f72e89bbb46b93264', // SP-REV-03
  '6ae1778bfff86d8702ffc3ce', // SP-BLK-01
  '6ae173d8a3528a7fa48cc1f6', // SP-S2-02
  '6ae36ef13f050d7c5931d1e2', // SP-ID-01-T03
  '6ae17430e9d42742c0082d2d', // SP-S2-03
  '6aead771da479daab813d1ee', // RISK-03
  '6ae36f0455004faa2680233f'  // SP-AI-01-T02
];

async function run() {
  for (const id of cardIds) {
    const res = await fetch(`https://api.trello.com/1/cards/${id}?key=${key}&token=${token}`);
    const c = await res.json();
    console.log('----------------------------------------');
    console.log(`CARD: [${c.id}] ${c.name}`);
    console.log(`LIST: ${c.idList}`);
    console.log('DESC:', c.desc ? c.desc.substring(0, 300) : '(empty)');
  }
}
run();
