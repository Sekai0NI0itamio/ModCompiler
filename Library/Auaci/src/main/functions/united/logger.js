// src/main/functions/united/logger.js
// Simple log helper used across the united utilities.

const fs = require('fs').promises;

async function appendLog(logPath, msg) {
  if (!logPath) logPath = '/tmp/events.log';
  try {
    await fs.appendFile(logPath, `[${new Date().toISOString()}] ${String(msg)}\n`);
  } catch (err) {
    // best-effort: avoid throwing from logging
    try { console.warn('[united/logger] appendLog failed:', err && err.message ? err.message : String(err)); } catch (_) {}
  }
}

module.exports = { appendLog };