// src/main/functions/editor/init.js
// Initializes the editor UI and wires Monaco / fallback textarea behaviors,
// drag & drop, keyboard shortcuts (save), tab interactions, and "Apply changes" tab action.

const { setupDragDrop } = require('./dragDrop');
const { saveFile } = require('./fileOperations');
const tabManagement = require('./tabManagement');
const { initTabsMenu } = require('./contentMenu');
const fs = require('fs').promises;
const path = require('path');
const monacoManager = require('./monacoManager');
const editorCache = require('./editorCache');

// Smart sync that compares disk <-> displayed content
const smartSync = require('./smartSync');

const {
  addFiles,
  switchTab,
  closeTab,
  markTabUnsaved,
  markActiveTabDirtyFromModel,
  clearUnsaved,
  getActiveTab,
  getTabByPath,
  openFileInNewTab,
  getAllTabs,
  setTabSaved,
  createUntitledTab,
  previewFile,
  updateActiveTabViewState
} = tabManagement;

// NEW: file tab options (right-click)
const { initFileTabOptions } = require('./fileTabOptions');

// New media utils for preview/rendering
const mediaUtils = require('./mediaUtils');

// NOTE: We rely on Monaco's native undo/redo stacks (per model). Custom snapshot-based
// undo caused large memory usage and jank for big files.

function initEditor() {
  const start = async () => {
    // Ensure the DOM container exists
    let container = document.getElementById('editor-content');
    if (!container) {
      const editorEl = document.getElementById('editor');
      container = document.createElement('div');
      container.id = 'editor-content';
      container.className = 'monaco-editor-container';
      if (editorEl) editorEl.appendChild(container);
      else document.body.appendChild(container);
    }

    // If the container is a textarea fallback, ensure autocorrect/spellcheck off
    if (container && container.tagName === 'TEXTAREA') {
      container.setAttribute('autocorrect', 'off');
      container.setAttribute('autocapitalize', 'off');
      container.setAttribute('spellcheck', 'false');
    }

    // Initialize Monaco editor (non-blocking)
    try {
      await monacoManager.initEditor('editor-content', {
        language: 'plaintext',
        value: '',
        quickSuggestions: false,
        suggestOnTriggerCharacters: false,
        wordBasedSuggestions: false,
        acceptSuggestionOnEnter: 'off'
      });

      // Wire Monaco change events -> mark tab unsaved
      monacoManager.onDidChangeModelContent(() => {
        try { if (typeof markActiveTabDirtyFromModel === 'function') markActiveTabDirtyFromModel(); } catch (_) {}
      });

      // After Monaco is ready, attach view-state listeners (selection + scroll)
      try {
        const editor = monacoManager.getEditor();
        if (editor) {
          try {
            editor.onDidChangeCursorSelection(() => {
              try {
                const sel = editor.getSelection();
                if (sel) {
                  const selectionObj = {
                    startLineNumber: sel.startLineNumber,
                    startColumn: sel.startColumn,
                    endLineNumber: sel.endLineNumber,
                    endColumn: sel.endColumn
                  };
                  updateActiveTabViewState({ selection: selectionObj });
                }
              } catch (_) {}
            });
          } catch (_) {}

          try {
            editor.onDidScrollChange(() => {
              try {
                const top = editor.getScrollTop();
                const left = editor.getScrollLeft();
                updateActiveTabViewState({ scrollTop: top, scrollLeft: left });
              } catch (_) {}
            });
          } catch (_) {}
        }
      } catch (err) {
        // ignore
      }

      // Restore cached tabs (best-effort)
      try {
        const cache = await editorCache.loadCache();
        if (cache && Array.isArray(cache.tabs) && cache.tabs.length) {
          // pass through name/temp/content/unsaved/selection/scroll/wrap so addFiles can create appropriate tabs
          const filesToAdd = cache.tabs.map(t => ({
            path: t.path,
            size: (typeof t.size === 'number') ? t.size : null,
            diskSize: (typeof t.diskSize === 'number') ? t.diskSize : null,
            diskMtimeMs: (typeof t.diskMtimeMs === 'number') ? t.diskMtimeMs : null,
            content: (t.content != null ? t.content : (t.savedContent != null ? t.savedContent : '')),
            savedContent: (t.savedContent != null ? t.savedContent : (t.content != null ? t.content : '')),
            name: t.name,
            temp: !!t.temp,
            unsaved: !!t.unsaved,
            id: t.id,
            selection: t.selection || null,
            cursorIndex: (typeof t.cursorIndex === 'number') ? t.cursorIndex : null,
            scrollTop: (typeof t.scrollTop === 'number') ? t.scrollTop : null,
            scrollLeft: (typeof t.scrollLeft === 'number') ? t.scrollLeft : null,
            mediaType: (t.mediaType === 'image' || t.mediaType === 'audio') ? t.mediaType : null,
            mime: t.mime || null,
            dataUrl: t.dataUrl || null,
            wrap: !!t.wrap
          }));
          addFiles(filesToAdd);
          if (cache.activePath) {
            const all = getAllTabs();
            const idx = all.findIndex(t => t && t.path === cache.activePath);
            if (idx !== -1) switchTab(idx);
          }
        }
      } catch (err) {
        console.warn('Failed to restore editor cache:', err);
      }

      // If there are no tabs after restoring cache, create an untitled editable buffer
      try {
        const allTabs = getAllTabs ? getAllTabs() : [];
        const active = getActiveTab();
        if ((!allTabs || allTabs.length === 0) || !active) {
          try { createUntitledTab('Untitled'); } catch (e) { /* ignore */ }
        }
      } catch (_) {
        // ignore
      }

      // Start smart sync that keeps the displayed file in sync with disk (see smartSync.js)
      // Adjust interval based on app activity: 1s when active, 2s when inactive
      try {
        const setSmartSyncIntervalByActivity = () => {
          try {
            const isActive = !document.hidden && (typeof document.hasFocus !== 'function' || document.hasFocus());
            const interval = isActive ? 1000 : 2000;
            if (smartSync && typeof smartSync.start === 'function') smartSync.start(interval);
          } catch (_) { /* ignore */ }
        };
        setSmartSyncIntervalByActivity();
        window.addEventListener('focus', setSmartSyncIntervalByActivity);
        window.addEventListener('blur', setSmartSyncIntervalByActivity);
        document.addEventListener('visibilitychange', setSmartSyncIntervalByActivity);
      } catch (e) { /* ignore */ }

    } catch (err) {
      console.warn('[initEditor] Monaco initialization failed, continuing with fallback textarea behavior:', err && err.message ? err.message : err);
      try {
        await fs.appendFile('/tmp/editor.log', `[${new Date().toISOString()}] Monaco init failed: ${err && err.message ? err.message : String(err)}\n`);
      } catch (_) {}
    }

    // Skip custom paste handling; rely on Monaco's native paste behavior.

    // Wire drag & drop functionality provided by the app (directory viewer -> editor)
    try {
      if (typeof setupDragDrop === 'function') setupDragDrop();
    } catch (e) { /* ignore */ }

    // Initialize the tabs "more" menu (⋯)
    try {
      if (typeof initTabsMenu === 'function') initTabsMenu();
    } catch (e) { /* ignore */ }

    // Initialize file tab right-click options
    try {
      if (typeof initFileTabOptions === 'function') initFileTabOptions();
    } catch (e) { /* ignore */ }

    // Fallback: if Monaco isn't available, wire textarea input to mark unsaved and save view-state
    const tryWireTextarea = () => {
      const contentArea = document.getElementById('editor-content');
      if (contentArea && contentArea.tagName === 'TEXTAREA') {
        contentArea.setAttribute('autocorrect', 'off');
        contentArea.setAttribute('autocapitalize', 'off');
        contentArea.setAttribute('spellcheck', 'false');

        // If there is no active tab, ensure the textarea stays disabled
        try {
          const active = getActiveTab();
          const allTabs = getAllTabs ? getAllTabs() : [];
          if (!active || !allTabs || allTabs.length === 0) {
            try { contentArea.setAttribute('disabled', 'disabled'); } catch (_) {}
          } else {
            try { contentArea.removeAttribute('disabled'); } catch (_) {}
          }
        } catch (_) {}

        contentArea.addEventListener('input', () => {
          if (window.__editor_suppress_input) return;
          markTabUnsaved(contentArea.value);
          // update caret index
          try {
            const pos = contentArea.selectionStart || 0;
            updateActiveTabViewState({ cursorIndex: pos });
          } catch (_) {}
        });

        contentArea.addEventListener('scroll', () => {
          try {
            updateActiveTabViewState({ scrollTop: contentArea.scrollTop, scrollLeft: contentArea.scrollLeft || 0 });
          } catch (_) {}
        });

        contentArea.addEventListener('mouseup', () => {
          try {
            const pos = contentArea.selectionStart || 0;
            updateActiveTabViewState({ cursorIndex: pos });
          } catch (_) {}
        });
      }
    };
    tryWireTextarea();

    // Save (Cmd/Ctrl+S)
    document.addEventListener('keydown', async (e) => {
      if ((e.metaKey || e.ctrlKey) && e.key && e.key.toLowerCase() === 's') {
        e.preventDefault();
        const activeTab = getActiveTab();
        if (!activeTab) return;

        if (!activeTab.unsaved) return;

        try {
          // Pull current content only at save time (avoids per-keystroke getValue()).
          let contentToSave = '';
          try {
            const editor = monacoManager.getEditor();
            if (editor && monacoManager.getMonaco()) {
              const model = editor.getModel && editor.getModel();
              if (model && typeof model.getValue === 'function') {
                contentToSave = model.getValue();
                // Keep tab <-> model linkage stable
                try { activeTab.model = model; } catch (_) {}
              }

              // Update view-state before saving (best-effort)
              try {
                const sel = editor.getSelection && editor.getSelection();
                if (sel) updateActiveTabViewState({
                  selection: {
                    startLineNumber: sel.startLineNumber,
                    startColumn: sel.startColumn,
                    endLineNumber: sel.endLineNumber,
                    endColumn: sel.endColumn
                  },
                  scrollTop: editor.getScrollTop(),
                  scrollLeft: editor.getScrollLeft()
                });
              } catch (_) {}
            } else {
              const contentArea = document.getElementById('editor-content');
              if (contentArea && contentArea.tagName === 'TEXTAREA') {
                contentToSave = contentArea.value || '';
                try { updateActiveTabViewState({ cursorIndex: contentArea.selectionStart || 0, scrollTop: contentArea.scrollTop || 0 }); } catch (_) {}
              }
            }
          } catch (_) {}

          // If the tab has no real file path (untitled), ask the user for a save path with autocomplete
          const needsSaveAs = !activeTab.path || String(activeTab.path).startsWith('untitled://');
          let targetPath = activeTab.path;
          if (needsSaveAs) {
            try {
              const { promptSavePath } = require('./saveAsPrompt');
              const chosen = await promptSavePath({ title: 'Save File As' });
              if (!chosen) return; // user canceled
              targetPath = chosen;
            } catch (err) {
              console.error('Save As prompt failed:', err);
              return;
            }
          }

          const success = await saveFile(targetPath, contentToSave);
          if (success) {
            // If this was a Save As, bind the tab to the new path and update Monaco model
            if (needsSaveAs) {
              try {
                const editor = monacoManager.getEditor();
                const oldModel = activeTab.model || (editor && editor.getModel && editor.getModel());

                activeTab.path = targetPath;
                activeTab.name = path.basename(targetPath);
                const lang = monacoManager.detectLanguageFromPath(targetPath);

                // Rebind to a model whose URI matches the new file path.
                if (editor && monacoManager.getMonaco()) {
                  let newModel = null;
                  monacoManager.ignoreModelContentChanges(() => {
                    newModel = monacoManager.createModelForPath(targetPath, contentToSave || '', lang, { update: true, setLanguage: true });
                    try { editor.setModel(newModel); } catch (_) { try { monacoManager.setModel(newModel); } catch (_) {} }
                  });
                  if (newModel) {
                    activeTab.model = newModel;
                    try { activeTab.savedVersionId = newModel.getAlternativeVersionId(); } catch (_) {}
                    // Untitled buffers are unique; safe to dispose the old model.
                    try { if (oldModel && oldModel !== newModel && typeof oldModel.dispose === 'function') oldModel.dispose(); } catch (_) {}
                  }
                }
              } catch (e) {
                console.warn('Failed to update Monaco model after Save As:', e);
              }
            }

            // Update disk signature so smartSync can skip expensive reads.
            try {
              const st = await fs.stat(targetPath);
              if (st && st.isFile && st.isFile()) {
                try { activeTab.diskMtimeMs = st.mtimeMs; } catch (_) {}
                try { activeTab.diskSize = st.size; } catch (_) {}
                try { activeTab.size = st.size; } catch (_) {}
                try {
                  activeTab.externalChange = false;
                  activeTab.externalDiskMtimeMs = null;
                  activeTab.externalDiskSize = null;
                } catch (_) {}
              }
            } catch (_) {}

            clearUnsaved();
          } else {
            // Log failure
            await fs.appendFile(
              '/tmp/editor.log',
              `[${new Date().toISOString()}] Save: Failed to save file ${targetPath}\n`
            );
          }
        } catch (err) {
          await fs.appendFile(
            '/tmp/editor.log',
            `[${new Date().toISOString()}] Save: Exception saving ${activeTab.path}: ${err && err.message ? err.message : String(err)}\n`
          );
        }
      }
    });

    // New: Create untitled tab with Cmd/Ctrl+N
    document.addEventListener('keydown', (e) => {
      if ((e.metaKey || e.ctrlKey) && e.key && e.key.toLowerCase() === 'n') {
        // Avoid intercepting normal typing in inputs/selects/textarea that are NOT the Monaco editor.
        const ae = document.activeElement;
        const tag = ae && ae.tagName ? ae.tagName.toUpperCase() : '';
        const insideMonaco = ae && typeof ae.closest === 'function' && ae.closest('.monaco-editor');
        // If focus is in a plain input/textarea/select and focus is not inside monaco, ignore
        if ((tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || (ae && ae.isContentEditable)) && !insideMonaco) {
          return;
        }

        e.preventDefault();
        try {
          if (typeof createUntitledTab === 'function') createUntitledTab('Untitled');
        } catch (err) {
          console.error('Failed to create untitled tab:', err);
        }
      }
    });

    // Undo / Redo (Cmd/Ctrl+Z, Cmd/Ctrl+Shift+Z or Ctrl+Y)
    document.addEventListener('keydown', (e) => {
      if (!(e.metaKey || e.ctrlKey)) return;
      const key = (e.key || '').toLowerCase();
      const isUndo = !e.shiftKey && key === 'z';
      const isRedo = (e.shiftKey && key === 'z') || key === 'y';
      if (!isUndo && !isRedo) return;

      // Avoid intercepting browser/native undo inside simple form controls unless inside Monaco
      const ae = document.activeElement;
      const tag = ae && ae.tagName ? ae.tagName.toUpperCase() : '';
      const insideMonaco = ae && typeof ae.closest === 'function' && ae.closest('.monaco-editor');
      if ((tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || (ae && ae.isContentEditable)) && !insideMonaco) {
        return;
      }

      try {
        const editor = monacoManager.getEditor();
        if (!editor || !monacoManager.getMonaco()) return;

        e.preventDefault();
        const command = isUndo ? 'undo' : 'redo';
        // Trigger Monaco's native undo/redo even if focus isn't currently inside the editor.
        try { editor.trigger('keyboard', command, null); } catch (_) {}
      } catch (err) {
        console.error('Undo/Redo operation failed:', err);
      }
    });

    // Open file in editor event (emitted by directory viewer, drag-drop handler, etc.)
    document.addEventListener('open-file-in-editor', (e) => {
      const detail = e && e.detail ? e.detail : {};
      const filePath = detail.path;
      const content = detail.content;

      if (!filePath) {
        // If no path provided, but content exists, put into current buffer
        const editor = monacoManager.getEditor();
        if (editor) {
          monacoManager.ignoreModelContentChanges(() => {
            monacoManager.setValue(content || '');
          });
        } else {
          const contentArea = document.getElementById('editor-content');
          if (contentArea && contentArea.tagName === 'TEXTAREA') {
            window.__editor_suppress_input = true;
            try { contentArea.value = content || ''; } finally {
              setTimeout(() => { window.__editor_suppress_input = false; }, 0);
            }
          }
        }
        clearUnsaved();
        return;
      }

      // If a tab for this path already exists, activate it and ensure its model/value is loaded
      const existingTab = getTabByPath(filePath);
      if (existingTab) {
        // Activate the tab by finding its index and calling switchTab
        const all = getAllTabs ? getAllTabs() : [];
        const idx = all.findIndex(t => t && t.path === existingTab.path);
        if (idx !== -1) switchTab(idx);

        // Nothing else to do: tabManagement.switchTab will ensure the model is attached.
        return;
      }

      // Otherwise, open in a new tab (delegates to tabManagement which handles reading file if needed)
      if (typeof openFileInNewTab === 'function') {
        openFileInNewTab(filePath, content);
        return;
      }

      // Fallback: set content into the single-buffer editor
      const editor = monacoManager.getEditor();
      if (editor) {
        monacoManager.ignoreModelContentChanges(() => {
          monacoManager.setValue(content || '');
        });
      } else {
        const contentArea = document.getElementById('editor-content');
        if (contentArea && contentArea.tagName === 'TEXTAREA') {
          window.__editor_suppress_input = true;
          try { contentArea.value = content || ''; } finally {
            setTimeout(() => { window.__editor_suppress_input = false; }, 0);
          }
        }
      }
      clearUnsaved();
    });

    // PREVIEW: single-click preview from the directory viewer
    document.addEventListener('preview-file', async (e) => {
      const detail = e && e.detail ? e.detail : {};
      const filePath = detail.path;
      if (!filePath) return;

      try {
        const stats = await fs.stat(filePath);
        if (!stats.isFile()) return;

        let buffer = null;
        try {
          buffer = await fs.readFile(filePath);
        } catch (readErr) {
          console.warn('preview-file: failed reading as buffer:', readErr);
          try { await fs.appendFile('/tmp/editor.log', `[${new Date().toISOString()}] preview-file read failed for ${filePath}: ${readErr.message}\n`); } catch (_) {}
          buffer = null;
        }

        if (!buffer) {
          if (typeof previewFile === 'function') {
            previewFile(filePath, '', stats.size, { mediaType: 'unknown', mtimeMs: stats.mtimeMs });
          }
          return;
        }

        const det = mediaUtils.detectMediaType(buffer, filePath);

        if (det.type === 'image') {
          const dataUrl = mediaUtils.bufferToDataUrl(buffer, det.mime);
          if (typeof previewFile === 'function') {
            previewFile(filePath, dataUrl, stats.size, { mediaType: 'image', mime: det.mime, dataUrl, mtimeMs: stats.mtimeMs });
          } else {
            const ev = new CustomEvent('open-file-in-editor', { detail: { path: filePath, content: dataUrl } });
            document.dispatchEvent(ev);
          }
          return;
        }

        if (det.type === 'audio') {
          // We do not attempt to embed audio now — show unsupported message via previewFile
          if (typeof previewFile === 'function') {
            previewFile(filePath, '', stats.size, { mediaType: 'audio', mime: det.mime, mtimeMs: stats.mtimeMs });
          } else {
            const ev = new CustomEvent('open-file-in-editor', { detail: { path: filePath, content: '' } });
            document.dispatchEvent(ev);
          }
          return;
        }

        // text OR binary (treat binary as text)
        let content = '';
        try {
          content = buffer.toString('utf8');
        } catch (_) {
          content = '';
        }
        if (typeof previewFile === 'function') {
          previewFile(filePath, content, stats.size, { mime: det.mime, mtimeMs: stats.mtimeMs });
        } else {
          const ev = new CustomEvent('open-file-in-editor', { detail: { path: filePath, content } });
          document.dispatchEvent(ev);
        }
      } catch (err) {
        console.error('preview-file handler error:', err);
      }
    });

    // Optional: when the window resizes, layout Monaco
    window.addEventListener('resize', () => {
      try { monacoManager.layout(); } catch (e) { /* ignore */ }
    });

    // Ensure smartSync is stopped on unload
    window.addEventListener('beforeunload', () => {
      try { if (smartSync && typeof smartSync.stop === 'function') smartSync.stop(); } catch (_) {}
    });
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', start, { once: true });
  } else {
    start();
  }
}

module.exports = { initEditor };
