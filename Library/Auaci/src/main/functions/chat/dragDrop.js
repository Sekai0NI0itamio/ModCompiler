// src/main/functions/chat/dragDrop.js
// Uses united/fsUtils so both chat & editor share the same file reading behavior.

const fs = require('fs').promises;
const path = require('path');
const { ipcRenderer } = require('electron');
const { saveCurrentInputDraft } = require('./incrementalHistoryStorage');
const { appendLog } = require('../united/logger');

let isDragging = false;
let dragEnterTimeout = null;
let cachedDragPath = null;
const logPath = '/tmp/events.log';

// Try to extract filesystem paths from a DataTransfer representing an external drop.
// Handles Electron's File.path, as well as text/uri-list & text/plain containing file:// URLs.
function extractPathsFromDataTransfer(dt) {
  const paths = [];
  if (!dt) return paths;

  // 1) Preferred: Electron File.path entries
  try {
    if (dt.files && dt.files.length) {
      for (const f of Array.from(dt.files)) {
        if (f && typeof f.path === 'string' && f.path) {
          paths.push(f.path);
        }
      }
    }
  } catch (_) {}

  // 2) Fallback: text/uri-list (common for Finder drags)
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

  // 3) Last resort: text/plain containing a file URL or absolute path
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

  // De-duplicate & drop empties
  return Array.from(new Set(paths.filter(Boolean)));
}

function setupDragDrop() {
  const userInput = document.getElementById('user-input');
  const gptResponseIndicator = document.getElementById('gpt-response-indicator');
  const filePreviews = document.getElementById('file-previews');

  const showDuplicateNotification = (fileName) => {
    if (!gptResponseIndicator) return;
    gptResponseIndicator.innerHTML = `
      <span class="gpt-indicator-text">This file is already pasted: ${fileName}</span>
    `;
    gptResponseIndicator.classList.add('visible');
    setTimeout(() => {
      if (!gptResponseIndicator) return;
      gptResponseIndicator.classList.remove('visible');
      gptResponseIndicator.innerHTML = `
        <span class="gpt-indicator-text">GPT is responding</span>
        <span class="gpt-indicator-dots">
          <span class="dot"></span>
          <span class="dot"></span>
          <span class="dot"></span>
        </span>
      `;
    }, 2000);
  };

  userInput.addEventListener('dragenter', async (e) => {
    e.preventDefault();
    e.stopPropagation();
    clearTimeout(dragEnterTimeout);
    const types = e.dataTransfer && Array.from(e.dataTransfer.types || []);
    await appendLog(logPath, `Dragenter on user-input, types: ${JSON.stringify(types)}`);
    const hasFiles = types.includes('Files');

    if (!isDragging) {
      isDragging = true;
      userInput.style.border = '2px dashed #007bff';
      if (hasFiles) {
        await appendLog(logPath, `External drag detected (using direct DataTransfer files)`);
      } else {
        await appendLog(logPath, `Internal drag detected (no Files type)`);
      }
    }
  }, false);

  userInput.addEventListener('dragover', async (e) => {
    e.preventDefault();
    e.stopPropagation();
    e.dataTransfer.dropEffect = 'copy';
    clearTimeout(dragEnterTimeout);
    const dataTypes = Array.from(e.dataTransfer.types || []);
    await appendLog(logPath, `Dragover on user-input, types: ${JSON.stringify(dataTypes)}`);
    if (window.isDirectoryViewDrag && window.draggedItem) {
      cachedDragPath = window.draggedItem.dataset.path;
      await appendLog(logPath, `Cached path for internal drag: ${cachedDragPath}`);
    }
  }, false);

  userInput.addEventListener('dragleave', async (e) => {
    e.preventDefault();
    e.stopPropagation();
    clearTimeout(dragEnterTimeout);
    dragEnterTimeout = setTimeout(async () => {
      isDragging = false;
      userInput.style.border = '';
      await appendLog(logPath, `Dragleave on user-input`);
    }, 100);
  }, false);

  userInput.addEventListener('drop', async (e) => {
    clearTimeout(dragEnterTimeout);
    isDragging = false;
    userInput.style.border = '';
    await appendLog(logPath, `🔴 DROP START`);
    const dataTypes = Array.from(e.dataTransfer.types || []);
    await appendLog(logPath, `Available types: ${JSON.stringify(dataTypes)}`);
    let filePath = null;
    let isInternalDrag = false;

    try {
      const internalFlag = e.dataTransfer.getData('application/x-internal-drag');
      const sourcePath = e.dataTransfer.getData('source');
      const textPath = e.dataTransfer.getData('text/plain');
      if (internalFlag === 'true' || (sourcePath && sourcePath !== 'directory-viewer') || (textPath && (textPath.includes('/') || textPath.includes('\\')))) {
        isInternalDrag = true;
        filePath = sourcePath || textPath;
        await appendLog(logPath, `✅ Got path from dataTransfer: ${filePath}`);
      }
    } catch (err) {
      await appendLog(logPath, `Error getting dataTransfer: ${err && err.message ? err.message : String(err)}`);
    }

    if (!filePath && (dataTypes.length === 0 || (dataTypes.length === 1 && dataTypes[0] === 'Files')) && cachedDragPath) {
      isInternalDrag = true;
      filePath = cachedDragPath;
      await appendLog(logPath, `✅ Using cached path for internal drag: ${filePath}`);
    }

    if (isInternalDrag && filePath) {
      // Internal drags: insert the full path as plain text into the chat input.
      e.preventDefault();
      e.stopPropagation();
      await appendLog(logPath, `🔄 Inserting internal drag path into chat input: ${filePath}`);
      // Ensure the chat input has focus so insertion goes to the right place.
      try { userInput && userInput.focus && userInput.focus(); } catch (_) {}
      try {
        if (userInput && userInput.tagName !== 'TEXTAREA') {
          try { document.execCommand('insertText', false, filePath + ' '); } catch (_) {
            const tn = document.createTextNode(filePath + ' ');
            const sel = window.getSelection();
            if (sel && sel.rangeCount) sel.getRangeAt(0).insertNode(tn);
            else userInput.appendChild(tn);
          }
          try { userInput.dispatchEvent(new Event('input', { bubbles: true })); } catch (_) {}
        } else if (userInput) {
          const currentValue = userInput.value;
          const cursorPos = userInput.selectionStart || currentValue.length;
          const insert = filePath + ' ';
          const newValue = currentValue.slice(0, cursorPos) + insert + currentValue.slice(cursorPos);
          userInput.value = newValue;
          userInput.selectionStart = userInput.selectionEnd = cursorPos + insert.length;
        }
      } catch (statError) {
        await appendLog(logPath, `❌ Error inserting internal drag path: ${statError && statError.message ? statError.message : String(statError)}`);
      }
    } else {
      // External drags from Finder etc. are handled via the main-process will-navigate
      // interceptor, which emits 'external-file-drop-paths' with resolved filesystem paths.
      // Do NOT prevent default here so the navigation event can fire.
      window.__externalDropTarget = 'chat';
      await appendLog(logPath, `External drop detected in chat; delegating to main-process will-navigate handler`);
    }
    cachedDragPath = null;
    await appendLog(logPath, `🔴 DROP END`);
  }, false);

  // Handle external file drops routed from main-process via will-navigate (file:// URLs).
  // For chat, instead of attaching files or reading their contents, we simply insert
  // the full filesystem path(s) as plain text into the input.
  ipcRenderer.on('external-file-drop-paths', async (_event, paths) => {
    try {
      if (!Array.isArray(paths) || !paths.length) return;
      // Only handle when chat is the last known external drop target
      if (window.__externalDropTarget && window.__externalDropTarget !== 'chat') return;
      const userInputEl = document.getElementById('user-input');
      if (!userInputEl) return;
      // Ensure chat input is focused before inserting paths so they appear on first drop.
      try { userInputEl.focus && userInputEl.focus(); } catch (_) {}

      for (const candidatePath of paths) {
        const p = typeof candidatePath === 'string' ? candidatePath : '';
        if (!p) continue;
        try {
          await appendLog(logPath, `Inserting external IPC drop path into chat input: ${p}`);
          if (userInputEl && userInputEl.tagName !== 'TEXTAREA') {
            const text = p + ' ';
            try { document.execCommand('insertText', false, text); } catch (_) {
              const tn = document.createTextNode(text);
              const sel = window.getSelection();
              if (sel && sel.rangeCount) sel.getRangeAt(0).insertNode(tn);
              else userInputEl.appendChild(tn);
            }
            try { userInputEl.dispatchEvent(new Event('input', { bubbles: true })); } catch (_) {}
          } else if (userInputEl) {
            const currentValue = userInputEl.value;
            const cursorPos = userInputEl.selectionStart || currentValue.length;
            const insert = p + ' ';
            const newValue = currentValue.slice(0, cursorPos) + insert + currentValue.slice(cursorPos);
            userInputEl.value = newValue;
            userInputEl.selectionStart = userInputEl.selectionEnd = cursorPos + insert.length;
          }
        } catch (err) {
          await appendLog(logPath, `Error inserting external IPC drop path into chat input ${p}: ${err && err.message ? err.message : String(err)}`);
        }
      }

      // Persist the updated draft text without any file attachments.
      try {
        const text = (userInputEl.value != null) ? userInputEl.value : (userInputEl.textContent || '');
        await saveCurrentInputDraft(text, []);
      } catch (_) {}
    } catch (err) {
      await appendLog(logPath, `Error handling external IPC drop via will-navigate (path-only mode): ${err && err.message ? err.message : String(err)}`);
    } finally {
      try { window.__externalDropTarget = null; } catch (_) {}
    }
  });
}

module.exports = { setupDragDrop };