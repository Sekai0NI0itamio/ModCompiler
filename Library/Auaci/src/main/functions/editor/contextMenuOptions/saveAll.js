// src/main/functions/editor/contextMenuOptions/saveAll.js
const { getAllTabs, setTabSaved } = require('../tabManagement');
const { saveFile } = require('../fileOperations');
const fs = require('fs').promises;
const monacoManager = require('../monacoManager');

/**
 * Save all unsaved tabs.
 * Called from the tabs menu.
 */
async function saveAllOption() {
  const tabs = getAllTabs();
  if (!Array.isArray(tabs) || tabs.length === 0) return;

  const unsaved = tabs.filter(t => t.unsaved);
  if (unsaved.length === 0) return;

  for (const tab of unsaved) {
    try {
      // Always save from the Monaco model when available (tab.content may be null or stale).
      let contentToSave = '';
      try {
        const model = tab && tab.model;
        if (model && typeof model.getValue === 'function') {
          contentToSave = model.getValue();
        } else if (typeof tab.content === 'string') {
          contentToSave = tab.content;
        } else {
          // Last resort: if this tab is currently active in the editor, pull from the editor model.
          const editor = monacoManager.getEditor();
          const m = editor && editor.getModel && editor.getModel();
          if (m && typeof m.getValue === 'function') contentToSave = m.getValue();
        }
      } catch (_) {}

      const success = await saveFile(tab.path, contentToSave);
      if (success) {
        setTabSaved(tab.path);
        // Update disk signature so smartSync can skip expensive reads.
        try {
          const st = await fs.stat(tab.path);
          if (st && st.isFile && st.isFile()) {
            try { tab.diskMtimeMs = st.mtimeMs; } catch (_) {}
            try { tab.diskSize = st.size; } catch (_) {}
            try { tab.size = st.size; } catch (_) {}
            try {
              tab.externalChange = false;
              tab.externalDiskMtimeMs = null;
              tab.externalDiskSize = null;
            } catch (_) {}
          }
        } catch (_) {}
      } else {
        // Log failure; leave tab marked unsaved
        await fs.appendFile('/tmp/editor.log', `[${new Date().toISOString()}] SaveAll: Failed to save ${tab.path}\n`);
      }
    } catch (err) {
      await fs.appendFile('/tmp/editor.log', `[${new Date().toISOString()}] SaveAll: Error saving ${tab.path}: ${err.message}\n`);
    }
  }
}

module.exports = { saveAllOption };
