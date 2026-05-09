// src/main/functions/editor/smartSync.js
// Periodically compare the currently displayed file with the on-disk file.
// If the active tab has no unsaved local changes and the on-disk file differs,
// update the displayed content programmatically (without marking it unsaved).
//
// If the active tab has unsaved changes and the on-disk file changed, mark the
// tab with an externalChange flag and dispatch a 'external-file-changed' event
// so the UI may decide how to present/merge the change.

const fs = require('fs').promises;
const { fileURLToPath } = require('url');
const tabManagement = require('./tabManagement');
const monacoManager = require('./monacoManager');

let _timer = null;
let _intervalMs = 2000;

// Keep smart sync cheap for large files.
const LARGE_FILE_BYTES = 1 * 1024 * 1024; // 1 MB
const VERY_LARGE_FILE_BYTES = 5 * 1024 * 1024; // 5 MB

// Re-rendering the entire tab strip is surprisingly expensive (sync fs.existsSync checks, DOM churn).
// Throttle it aggressively.
let _lastTabsRefreshMs = 0;
const TAB_REFRESH_INTERVAL_MS = 5000;

async function _toFsPath(p) {
  if (!p) return null;
  if (typeof p !== 'string') return null;
  if (p.startsWith('file://')) {
    try {
      return fileURLToPath(p);
    } catch (e) {
      // fallback to naive strip
      return p.replace(/^file:\/\//, '');
    }
  }
  return p;
}

async function _checkOnce() {
  try {
    // Always refresh the tab strip so missing/deleted file states and naming are updated promptly
    try {
      if (typeof tabManagement.refreshTabsRender === 'function') {
        const now = Date.now();
        if ((now - _lastTabsRefreshMs) > TAB_REFRESH_INTERVAL_MS) {
          _lastTabsRefreshMs = now;
          tabManagement.refreshTabsRender();
        }
      }
    } catch (_) { /* ignore */ }

    const active = tabManagement.getActiveTab();
    if (!active || !active.path) return;

    // skip untitled scheme
    if (String(active.path).startsWith('untitled://')) return;

    const fsPath = await _toFsPath(active.path);
    if (!fsPath) return;

    // Ensure file exists and is a file. Stat is cheap; full reads are not.
    let st = null;
    try {
      st = await fs.stat(fsPath);
      if (!st || !st.isFile()) return;
    } catch (err) {
      // file missing or inaccessible
      return;
    }

    // Re-fetch active (it may have changed during async IO)
    const nowActive = tabManagement.getActiveTab();
    if (!nowActive || nowActive.path !== active.path) return;

    // If disk signature hasn't changed, there's nothing to do.
    const prevMtime = (typeof nowActive.diskMtimeMs === 'number') ? nowActive.diskMtimeMs : null;
    const prevSize = (typeof nowActive.diskSize === 'number') ? nowActive.diskSize : null;
    const mtimeMs = (typeof st.mtimeMs === 'number') ? st.mtimeMs : null;
    const size = (typeof st.size === 'number') ? st.size : null;
    if (prevMtime != null && prevSize != null && mtimeMs != null && size != null) {
      if (prevMtime === mtimeMs && prevSize === size) return;
    }

    // If we reach here, disk signature changed.
    // - Clean tabs: we may update the buffer (small files only).
    // - Dirty tabs: flag external change, don't overwrite.

    // Dirty: never overwrite editor content.
    if (nowActive.unsaved) {
      try {
        const t = tabManagement.getTabByPath(nowActive.path);
        if (t) {
          t.externalChange = true;
          t.externalDiskMtimeMs = mtimeMs;
          t.externalDiskSize = size;
          // Update signature so we don't re-process the same change every tick.
          t.diskMtimeMs = mtimeMs;
          t.diskSize = size;
        }
        try {
          const ev = new CustomEvent('external-file-changed', {
            detail: { path: nowActive.path, mtimeMs, size, hasLocalEdits: true }
          });
          document.dispatchEvent(ev);
        } catch (_) {}
      } catch (err) {
        console.warn('[smartSync] error flagging external change:', err);
      }
      return;
    }

    // Clean: avoid background full reads for large files.
    // Exception: if the editor hasn't loaded any content yet (common after restoring tabs
    // without persisted content), allow a one-time load so the user can see the file.
    if ((typeof size === 'number' && size >= LARGE_FILE_BYTES)) {
      let needsInitialLoad = false;
      try {
        const editor = monacoManager.getEditor();
        const model = (nowActive && nowActive.model) ? nowActive.model : (editor && editor.getModel && editor.getModel());
        if (model && typeof model.getValueLength === 'function') {
          needsInitialLoad = (model.getValueLength() === 0 && size > 0);
        }
      } catch (_) {
        needsInitialLoad = false;
      }

      if (!needsInitialLoad) {
      try {
        const t = tabManagement.getTabByPath(nowActive.path);
        if (t) {
          t.externalChange = true;
          t.externalDiskMtimeMs = mtimeMs;
          t.externalDiskSize = size;
          // Update signature so we don't re-process the same change every tick.
          t.diskMtimeMs = mtimeMs;
          t.diskSize = size;
        }
        try {
          const ev = new CustomEvent('external-file-changed', {
            detail: { path: nowActive.path, mtimeMs, size, hasLocalEdits: false, largeFile: true }
          });
          document.dispatchEvent(ev);
        } catch (_) {}
      } catch (_) {}
      return;
      }
    }

    // Small clean file: read and apply.
    let diskContent = null;
    try {
      diskContent = await fs.readFile(fsPath, 'utf8');
    } catch (err) {
      // Could be binary or unreadable; ignore
      return;
    }

    // Re-fetch active again after I/O (it may have changed)
    const stillActive = tabManagement.getActiveTab();
    if (!stillActive || stillActive.path !== nowActive.path) return;

    const diskNormalized = diskContent == null ? '' : String(diskContent);

    try {
      // Update tab size hints early so tabManagement can apply large-file mode before we inject text.
      try {
        const t = tabManagement.getTabByPath(stillActive.path);
        if (t) {
          t.size = (typeof size === 'number') ? size : t.size;
          t.diskMtimeMs = mtimeMs;
          t.diskSize = size;
          t.isLarge = (typeof size === 'number') ? (size >= LARGE_FILE_BYTES) : !!t.isLarge;
          t.isVeryLarge = (typeof size === 'number') ? (size >= VERY_LARGE_FILE_BYTES) : !!t.isVeryLarge;
        }
        if (typeof tabManagement.refreshActiveTabEditorMode === 'function') {
          tabManagement.refreshActiveTabEditorMode();
        }
      } catch (_) {}

      const editor = monacoManager.getEditor();
      const lang = (typeof size === 'number' && size >= VERY_LARGE_FILE_BYTES)
        ? 'plaintext'
        : monacoManager.detectLanguageFromPath(stillActive.path);
      if (editor && monacoManager.getMonaco()) {
        monacoManager.ignoreModelContentChanges(() => {
          const updatedModel = monacoManager.createModelForPath(stillActive.path, diskNormalized, lang, { update: true, setLanguage: true });
          try { editor.setModel(updatedModel); } catch (_) {}
          try { stillActive.model = updatedModel; } catch (_) {}
          try { stillActive.savedVersionId = updatedModel.getAlternativeVersionId(); } catch (_) {}
        });
      } else {
        // textarea fallback
        const contentArea = document.getElementById('editor-content');
        if (contentArea && contentArea.tagName === 'TEXTAREA') {
          window.__editor_suppress_input = true;
          try {
            contentArea.value = diskNormalized;
          } finally {
            setTimeout(() => { window.__editor_suppress_input = false; }, 0);
          }
        }
      }

      // Update disk signature baseline so future ticks stay cheap.
      try {
        const t = tabManagement.getTabByPath(stillActive.path);
        if (t) {
          t.diskMtimeMs = mtimeMs;
          t.diskSize = size;
          t.size = (typeof size === 'number') ? size : t.size;
          t.externalChange = false;
          t.externalDiskMtimeMs = null;
          t.externalDiskSize = null;
        }
      } catch (_) {}

      // Mark saved/clean in the tab system.
      try { tabManagement.setTabSaved(stillActive.path); } catch (_) {}
    } catch (err) {
      console.warn('[smartSync] failed to apply disk update:', err);
    }
  } catch (err) {
    console.warn('[smartSync] tick error:', err);
  }
}

function start(intervalMs = 1500) {
  _intervalMs = typeof intervalMs === 'number' && intervalMs > 0 ? intervalMs : 1500;
  if (_timer) clearInterval(_timer);
  _timer = setInterval(() => {
    _checkOnce().catch(e => {
      console.warn('[smartSync] tick failed:', e);
    });
  }, _intervalMs);

  // Run an immediate check
  _checkOnce().catch(() => {});
}

function stop() {
  if (_timer) {
    clearInterval(_timer);
    _timer = null;
  }
}

module.exports = { start, stop };
