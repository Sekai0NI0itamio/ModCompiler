// src/main/functions/editor/contextMenuOptions/closeAllSaved.js
const { getAllTabs, closeTab } = require('../tabManagement');
const fs = require('fs').promises;

/**
 * Close all tabs that are NOT unsaved (i.e., saved/clean tabs).
 * Called from the tabs menu.
 */
async function closeAllSavedOption() {
  // Use a snapshot of tabs and iterate from end to start so indices remain valid
  let tabs = getAllTabs();
  if (!Array.isArray(tabs) || tabs.length === 0) return;

  // Since getAllTabs returns the internal array, compute length once and iterate backwards.
  for (let i = tabs.length - 1; i >= 0; i--) {
    try {
      const tab = tabs[i];
      if (!tab.unsaved) {
        // closeTab is async and will mutate the tabs array; iterating backwards avoids index issues.
        await closeTab(i);
      }
    } catch (err) {
      await fs.appendFile('/tmp/editor.log', `[${new Date().toISOString()}] CloseAllSaved: Error closing tab index ${i}: ${err.message}\n`);
    }

    // refresh the snapshot after each close (tabManagement modifies internal array)
    tabs = getAllTabs();
  }
}

module.exports = { closeAllSavedOption };