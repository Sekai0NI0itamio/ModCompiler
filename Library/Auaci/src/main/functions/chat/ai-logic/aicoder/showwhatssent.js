// src/main/functions/chat/ai-logic/aicoder/showwhatssent.js
// Writes the exact request (user_prompt) that will be sent to GPT
// into .auaci/chathistory/userrequest.txt for one-time debugging/inspection.

const fs = require('fs').promises;
const path = require('path');

const historyDir = path.join(process.cwd(), '.auaci', 'chathistory');
const outPath = path.join(historyDir, 'userrequest.txt');

/**
 * writeUserRequest(text)
 * Overwrites userrequest.txt with the provided text.
 * This is not a history log; it's just the latest request snapshot.
 */
async function writeUserRequest(text) {
  try {
    await fs.mkdir(historyDir, { recursive: true });
    await fs.writeFile(outPath, String(text || ''), 'utf8');
  } catch (err) {
    console.error('[showwhatssent] Failed to write userrequest.txt:', err && err.message ? err.message : err);
  }
}

module.exports = { writeUserRequest };