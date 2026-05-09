// src/chat/functions/history.js
// Delegator to the main session-based historyStorage so older imports still work.

const path = require('path');

try {
  // If your main/session history implementation is at this path:
  // src/main/functions/chat/historyStorage.js
  module.exports = require(path.join(process.cwd(), 'src', 'main', 'functions', 'chat', 'historyStorage.js'));
} catch (err) {
  // Fallback: minimal stub to avoid runtime crashes if file missing
  console.error('[history delegator] Failed to load main historyStorage:', err && err.message ? err.message : err);
  module.exports = {
    async loadChatHistory() { return { creationdate: new Date().toISOString(), lastused: new Date().toISOString(), chat: [] }; },
    async saveChatHistory() { /* no-op fallback */ }
  };
}