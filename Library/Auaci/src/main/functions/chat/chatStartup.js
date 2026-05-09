// src/main/functions/chat/chatStartup.js
// Chat startup initialization and cleanup
// This should be called when the chat module initializes to clean up
// any incomplete entries from previous sessions

const { cleanupIncompleteEntries } = require('./incrementalHistoryStorage');

/**
 * initializeChatModule()
 * Performs initialization tasks for the chat module:
 * - Cleans up any incomplete entries from previous sessions
 * - Sets up any necessary event handlers
 */
async function initializeChatModule() {
  console.log('[chatStartup] Initializing chat module...');
  
  try {
    await cleanupIncompleteEntries();
    console.log('[chatStartup] Chat module initialization completed');
  } catch (err) {
    console.error('[chatStartup] Error during chat module initialization:', err?.message || err);
  }
}

module.exports = {
  initializeChatModule
};