// src/main/functions/editor/externalfile.js
// Handles external file drops directly via DataTransfer (no native filedroplistener binary).

const { addFiles } = require('./tabManagement');
const fs = require('fs').promises;
const path = require('path');
const { ipcRenderer } = require('electron');
const { readFileContent } = require('../united/fsUtils');
const { appendLog } = require('../united/logger');

async function setupExternalDragDrop() {
  const editorContent = document.getElementById('editor-content');
  const logPath = '/tmp/editor.log';
  if (!editorContent) {
    await fs.appendFile(logPath, `[${new Date().toISOString()}] Error: #editor-content not found\n`).catch(() => {});
    return;
  }

  let isDragging = false;
  let dragLock = false;

  editorContent.setAttribute('draggable', 'false');

  // Shared with chat: extract potential filesystem paths from a DataTransfer.
  function extractPathsFromDataTransfer(dt) {
    const paths = [];
    if (!dt) return paths;

    try {
      if (dt.files && dt.files.length) {
        for (const f of Array.from(dt.files)) {
          if (f && typeof f.path === 'string' && f.path) {
            paths.push(f.path);
          }
        }
      }
    } catch (_) {}

    if (!paths.length && dt.getData) {
      try {
        const uriList = dt.getData('text/uri-list') || '';
        if (uriList && uriList.trim()) {
          uriList.split(/\r?\n/).forEach((line) => {
            const s = (line || '').trim();
            if (!s || s.startsWith('#')) return;
            if (/^file:\/\//i.test(s)) {
              let p = s;
              try { p = decodeURI(s); } catch (_) {}
              p = p.replace(/^file:\/\//i, '');
              if (p) paths.push(p);
            }
          });
        }
      } catch (_) {}
    }

    if (!paths.length && dt.getData) {
      try {
        const plain = dt.getData('text/plain') || '';
        const s = plain.trim();
        if (s) {
          if (/^file:\/\//i.test(s)) {
            let p = s;
            try { p = decodeURI(s); } catch (_) {}
            p = p.replace(/^file:\/\//i, '');
            if (p) paths.push(p);
          } else if (s.startsWith('/')) {
            paths.push(s);
          }
        }
      } catch (_) {}
    }

    return Array.from(new Set(paths.filter(Boolean)));
  }

  const resetDragState = async () => {
    isDragging = false;
    dragLock = false;
    editorContent.classList.remove('dragover');
    await appendLog(logPath, `State reset`).catch(() => {});
  };

  const dragEnterHandler = async (e) => {
    if (dragLock) return;
    const source = e.dataTransfer && e.dataTransfer.getData ? e.dataTransfer.getData('source') : null;
    if (source === 'directory-viewer' || window.isDirectoryViewDrag) return;

    e.preventDefault();
    e.stopImmediatePropagation();

    if (!isDragging) {
      isDragging = true;
      dragLock = true;
      editorContent.classList.add('dragover');
      await appendLog(logPath, `External drag entered editor-content`).catch(() => {});
    }
  };

  const dropHandler = async (e) => {
    const source = e.dataTransfer && e.dataTransfer.getData ? e.dataTransfer.getData('source') : null;
    const isInternal = (source === 'directory-viewer' || window.isDirectoryViewDrag);

    if (isInternal) {
      // Let internal drags fall through (other handlers may process them), but keep the
      // visual state consistent.
      await appendLog(logPath, `Internal drag dropped on editor-content (no-op)`).catch(() => {});
    } else {
      // External drags: let the default navigation to file:// happen so main-process
      // can intercept it via will-navigate and send the resolved path back over IPC.
      window.__externalDropTarget = 'editor';
      await appendLog(logPath, `External drop on editor-content; delegating to main-process will-navigate handler`).catch(() => {});
      // IMPORTANT: do NOT call preventDefault here for external drops.
    }

    await resetDragState();
  };

  editorContent.addEventListener('dragenter', dragEnterHandler, { capture: true });
  editorContent.addEventListener('dragover', (e) => {
    if (!dragLock || (e.dataTransfer && e.dataTransfer.getData && e.dataTransfer.getData('source') !== 'directory-viewer' && !window.isDirectoryViewDrag)) {
      e.preventDefault();
      e.stopImmediatePropagation();
    }
  }, { capture: true });
  editorContent.addEventListener('drop', dropHandler, { capture: true });

  // Handle external file drops routed from main-process via will-navigate (file:// URLs).
  ipcRenderer.on('external-file-drop-paths', async (_event, paths) => {
    try {
      if (!Array.isArray(paths) || !paths.length) return;
      if (window.__externalDropTarget && window.__externalDropTarget !== 'editor') return;

      const validFiles = [];
      for (const candidatePath of paths) {
        const p = typeof candidatePath === 'string' ? candidatePath : '';
        if (!p) continue;
        try {
          const stats = await fs.stat(p);
          if (!stats.isFile()) {
            await appendLog(logPath, `Skipping non-file path from external IPC drop (editor): ${p}`).catch(() => {});
            continue;
          }
          const contentObj = await readFileContent(p, { logPath });
          if (!contentObj) {
            await appendLog(logPath, `Failed to read content for external IPC drop file (editor): ${p}`).catch(() => {});
            continue;
          }
          validFiles.push({
            name: contentObj.name || path.basename(p),
            size: typeof contentObj.size === 'number' ? contentObj.size : stats.size,
            path: contentObj.path || p,
            content: contentObj.content
          });
        } catch (err) {
          await appendLog(logPath, `Error processing external IPC drop file (editor) ${p}: ${err && err.message ? err.message : String(err)}`).catch(() => {});
        }
      }

      if (!validFiles.length) {
        await appendLog(logPath, `No valid files found from external IPC drop (editor)`).catch(() => {});
        return;
      }

      addFiles(validFiles);
      await appendLog(logPath, `Added ${validFiles.length} files to editor from external IPC drop`).catch(() => {});
    } catch (err) {
      await appendLog(logPath, `Error handling external IPC drop via will-navigate (editor): ${err && err.message ? err.message : String(err)}`).catch(() => {});
    } finally {
      try { window.__externalDropTarget = null; } catch (_) {}
    }
  });
}

module.exports = { setupExternalDragDrop };
