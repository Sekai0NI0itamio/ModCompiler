// src/main/functions/terminal/state-bridge.js
// Small helper used early on app startup to read terminal saved state
// without importing the heavier terminal init module.

const fs = require('fs');
const path = require('path');

function getProjectRootSync() {
  try {
    // Best effort: the renderer CWD is project root in this app setup
    return process.cwd();
  } catch (e) {
    return process.cwd();
  }
}

function getSavedTerminalState() {
  try {
    const root = getProjectRootSync();
    const file = path.join(root, '.auaci', 'terminal', 'sessions.json');
    const raw = fs.readFileSync(file, 'utf8');
    return JSON.parse(raw);
  } catch (_) {
    return null;
  }
}

module.exports = { getSavedTerminalState };
