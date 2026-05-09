// src/main/functions/directory_viewer/clipboardMonitor.js
// Polls the clipboard every 2 seconds and highlights any displayed tree-item
// whose name appears in the first 100 characters of the clipboard.
// On macOS, prefers the native bin/clipgrab binary (projectRoot/bin/clipgrab).
// Falls back to /usr/bin/pbpaste or Electron's clipboard if needed.

const { execFile } = require('child_process');
const path = require('path');
const fsSync = require('fs');
let intervalHandle = null;

// Persisted in-memory set of highlighted paths (so we can reapply highlights on re-render)
let highlightedPaths = new Set();

function ensureStyle() {
  if (typeof document === 'undefined') return;
  if (document.getElementById('clipboard-monitor-style')) return;
  const style = document.createElement('style');
  style.id = 'clipboard-monitor-style';
  // Highlighted name text should be 30% black + 70% pure yellow => RGB(179,179,0) => #B3B300
  style.textContent = `
    /* Highlighted by clipboard monitor: set name text to 30% black + 70% yellow (RGB(179,179,0)) */
    .tree-item.clipboard-highlight .name {
      color: #B3B300 !important;
    }
  `;
  document.head.appendChild(style);
}

function highlightMatchesFromSnippet(snippet) {
  if (typeof document === 'undefined') return;
  const lower = (snippet || '').toLowerCase();

  // Build new set of matches based on displayed items.
  const items = Array.from(document.querySelectorAll('.tree-item'));
  const newMatches = new Set();

  items.forEach(item => {
    const nameEl = item.querySelector('.name');
    let name = nameEl ? (nameEl.textContent || '') : '';
    if (!name) {
      // fallback to last path segment
      const p = item.dataset && item.dataset.path ? item.dataset.path : '';
      name = p ? path.basename(p) : '';
    }
    if (!name) {
      // Nothing to match; ensure DOM class removed
      item.classList.remove('clipboard-highlight');
      return;
    }

    if (lower.includes(name.toLowerCase())) {
      newMatches.add(item.dataset.path);
      item.classList.add('clipboard-highlight');
    } else {
      item.classList.remove('clipboard-highlight');
    }
  });

  // Replace the highlighted paths set atomically so renders can read it to reapply highlights.
  highlightedPaths = newMatches;
}

// Read first 100 characters from clipboard.
// Prefer the bundled macOS binary at projectRoot/bin/clipgrab if present.
// Fallback order (macOS): clipgrab -> /usr/bin/pbpaste -> Electron clipboard module
function readClipboardSnippet() {
  return new Promise((resolve) => {
    if (typeof process === 'undefined' || typeof document === 'undefined') {
      return resolve('');
    }

    // Compute expected binary path: projectRoot/bin/clipgrab
    // __dirname is src/main/functions/directory_viewer
    const binPath = path.join(__dirname, '..', '..', '..', '..', 'bin', 'clipgrab');

    // Helper to finalize
    const finish = (txt) => {
      if (!txt) return resolve('');
      resolve(txt.slice(0, 100));
    };

    // Only attempt the native binary on macOS (darwin)
    if (process.platform === 'darwin') {
      if (fsSync.existsSync(binPath)) {
        execFile(binPath, { timeout: 1200 }, (err, stdout) => {
          if (!err && stdout) {
            return finish(stdout.toString());
          }
          // fallback to pbpaste
          execFile('/usr/bin/pbpaste', { timeout: 1200 }, (err2, stdout2) => {
            if (!err2 && stdout2) return finish(stdout2.toString());
            // last resort: Electron clipboard (dynamic require)
            try {
              // require here so module won't fail in environments without electron
              const { clipboard } = require('electron');
              const txt = clipboard.readText() || '';
              return finish(txt);
            } catch (e) {
              console.error('clipboardMonitor: failed to read clipboard', e);
              return finish('');
            }
          });
        });
        return;
      }

      // No clipgrab binary; use pbpaste
      execFile('/usr/bin/pbpaste', { timeout: 1200 }, (err, stdout) => {
        if (!err && stdout) return finish(stdout.toString());
        try {
          const { clipboard } = require('electron');
          const txt = clipboard.readText() || '';
          return finish(txt);
        } catch (e) {
          console.error('clipboardMonitor: failed to read clipboard', e);
          return finish('');
        }
      });
      return;
    }

    // Non-macOS: try Electron clipboard if available
    try {
      const { clipboard } = require('electron');
      const txt = clipboard.readText() || '';
      return finish(txt);
    } catch (e) {
      // No clipboard available
      return finish('');
    }
  });
}

async function pollOnce() {
  try {
    const snippet = await readClipboardSnippet();
    highlightMatchesFromSnippet(snippet || '');
  } catch (err) {
    console.error('clipboardMonitor: poll error', err);
  }
}

function startClipboardMonitor(intervalMs = 2000) {
  // ensure single instance
  if (intervalHandle) {
    clearInterval(intervalHandle);
  }
  if (typeof document === 'undefined') return;
  ensureStyle();
  // initial immediate run
  pollOnce();
  intervalHandle = setInterval(() => {
    pollOnce();
  }, intervalMs);
  // also expose on window for debugging/cleanup similar to other parts
  try { window.clipboardMonitorInterval = intervalHandle; } catch (e) { /* ignore */ }
}

function stopClipboardMonitor() {
  if (intervalHandle) {
    clearInterval(intervalHandle);
    intervalHandle = null;
  }
  try { if (window && window.clipboardMonitorInterval) delete window.clipboardMonitorInterval; } catch (e) {}
  // remove highlights from DOM
  if (typeof document !== 'undefined') {
    document.querySelectorAll('.tree-item.clipboard-highlight').forEach(el => el.classList.remove('clipboard-highlight'));
  }
  // clear internal set
  highlightedPaths.clear();
}

// public helpers to let render code reapply highlights on new DOM elements
function isPathHighlighted(p) {
  return highlightedPaths.has(p);
}
function getHighlightedPaths() {
  return new Set(highlightedPaths);
}

module.exports = {
  startClipboardMonitor,
  stopClipboardMonitor,
  isPathHighlighted,
  getHighlightedPaths,
  pollOnce
};