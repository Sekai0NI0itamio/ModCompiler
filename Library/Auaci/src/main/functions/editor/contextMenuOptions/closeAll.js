// src/main/functions/editor/contextMenuOptions/closeAll.js
const { getAllTabs, closeTab } = require('../tabManagement');
const fs = require('fs').promises;

/**
 * Close all tabs. If there are unsaved tabs, ask for confirmation:
 * "There are unsaved files. Are you sure you want to discard them and close all?"
 * If confirmed, or if no unsaved tabs exist, close all tabs.
 *
 * Uses closeTab which updates cache via tabManagement._queueSaveCache.
 */
async function closeAllOption() {
  const tabs = getAllTabs();
  if (!Array.isArray(tabs) || tabs.length === 0) return;

  const unsaved = tabs.filter(t => t.unsaved);
  if (unsaved.length > 0) {
    const msg = 'There are unsaved files. Are you sure you want to discard them and close all?';
    if (!confirm(msg)) return;
  }

  // Close all tabs from end to start to avoid index shifts (closeTab mutates the array).
  // Since closeTab is async and updates cache, await each close to keep deterministic behavior.
  // Use a snapshot loop that keeps retrieving the current tabs array length.
  let currentTabs = getAllTabs();
  for (let i = currentTabs.length - 1; i >= 0; i--) {
    try {
      await closeTab(i);
      // refresh snapshot
      currentTabs = getAllTabs();
    } catch (err) {
      // Log error and continue closing others
      try {
        await fs.appendFile('/tmp/editor.log', `[${new Date().toISOString()}] CloseAll: Error closing tab index ${i}: ${err && err.message ? err.message : String(err)}\n`);
      } catch (_) {}
    }
  }
}

module.exports = { closeAllOption };