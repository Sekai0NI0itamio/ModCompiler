// src/main/functions/editor/saveAsPrompt.js
// An overlay prompt that asks for a file path to save to, with Tab autocompletion similar to directory_viewer createFile.

const fs = require('fs').promises;
const path = require('path');
const { ipcRenderer } = require('electron');

let createPopupWithTolerance = null;
try {
  // Reuse the same tolerance/overlay behavior used by the directory viewer popups
  createPopupWithTolerance = require('../directory_viewer/popupUtils').createPopupWithTolerance;
} catch (_) {
  createPopupWithTolerance = null;
}

async function getProjectRoot() {
  if (window.projectRoot) return window.projectRoot;
  try {
    const pr = await ipcRenderer.invoke('get-project-root');
    if (pr) window.projectRoot = pr;
    return pr || process.cwd();
  } catch (_) {
    return process.cwd();
  }
}

/**
 * promptSavePath(opts) => Promise<string|null>
 * opts: { projectRoot?: string, relativePath?: string, title?: string, placeholder?: string }
 */
async function promptSavePath(opts = {}) {
  const projectRoot = opts.projectRoot || await getProjectRoot();
  const relativePath = opts.relativePath || '';
  const title = opts.title || 'Save As';
  const placeholder = opts.placeholder || 'Enter file path (e.g., src/utils/file.txt) — press Tab to autocomplete';

  return new Promise((resolve) => {
    let resolved = false;
    function finish(value) {
      if (resolved) return;
      resolved = true;
      try {
        if (document.body.contains(overlay)) document.body.removeChild(overlay);
      } catch (_) {}
      resolve(value || null);
    }

    const overlay = document.createElement('div');
    Object.assign(overlay.style, {
      position: 'fixed', top: '0', left: '0', width: '100vw', height: '100vh',
      background: 'rgba(0, 0, 0, 0.5)', display: 'flex', justifyContent: 'center', alignItems: 'center', zIndex: '1000'
    });

    const container = document.createElement('div');
    Object.assign(container.style, {
      display: 'flex', flexDirection: 'column', gap: '8px', background: '#fff', padding: '14px', borderRadius: '8px',
      boxShadow: '0 6px 18px rgba(0, 0, 0, 0.12)', width: 'min(92vw, 720px)', maxWidth: '720px', boxSizing: 'border-box'
    });

    const header = document.createElement('div');
    header.textContent = title;
    Object.assign(header.style, { fontSize: '14px', fontWeight: '600', color: '#111' });

    const input = document.createElement('input');
    input.type = 'text';
    input.placeholder = placeholder;
    Object.assign(input.style, {
      padding: '10px', fontSize: '14px', borderRadius: '6px', border: '1px solid #d9e1ec', outline: 'none', boxSizing: 'border-box'
    });

    const targetInfo = document.createElement('div');
    Object.assign(targetInfo.style, {
      fontSize: '12px', color: '#333', background: '#f7f9fb', padding: '8px 10px', borderRadius: '6px', border: '1px solid #eef4ff',
      display: 'none', wordBreak: 'break-all'
    });

    function updateTargetInfo() {
      const val = input.value.trim();
      if (!val) {
        targetInfo.style.display = 'block';
        targetInfo.textContent = `File will be saved under: ${path.join(projectRoot, relativePath)}`;
        return;
      }
      const lastChar = val.slice(-1);
      if (lastChar === '/' || lastChar === '\\') {
        const dirSegments = val.split(/[\\/]+/).filter(Boolean);
        const dirPath = path.join(projectRoot, relativePath, ...dirSegments);
        targetInfo.style.display = 'block';
        targetInfo.textContent = `Save directory: ${dirPath}`;
        return;
      }
      const segments = val.split(/[\\/]+/).filter(Boolean);
      const dirSegments = segments.slice(0, -1);
      const dirPath = dirSegments.length ? path.join(projectRoot, relativePath, ...dirSegments) : path.join(projectRoot, relativePath);
      targetInfo.style.display = 'block';
      targetInfo.textContent = `Save directory: ${dirPath}`;
    }

    const suggestionsContainer = document.createElement('div');
    Object.assign(suggestionsContainer.style, {
      maxHeight: '260px', overflowY: 'auto', borderRadius: '6px', border: '1px solid transparent', boxShadow: '0 6px 18px rgba(0,0,0,0.08)',
      background: '#fff', display: 'none', flexDirection: 'column', gap: '0', padding: '6px 0', boxSizing: 'border-box'
    });

    const hint = document.createElement('div');
    hint.textContent = 'Tip: press Tab to autocomplete; click a suggestion to insert it. Folders append a trailing "/"';
    Object.assign(hint.style, { fontSize: '12px', color: '#666', marginTop: '4px' });

    container.appendChild(header);
    container.appendChild(input);
    container.appendChild(targetInfo);
    container.appendChild(suggestionsContainer);
    container.appendChild(hint);
    overlay.appendChild(container);
    document.body.appendChild(overlay);

    input.focus();

    let currentSuggestions = [];
    let selectedIndex = -1;

    function clearSuggestions() {
      suggestionsContainer.innerHTML = '';
      suggestionsContainer.style.display = 'none';
      currentSuggestions = [];
      selectedIndex = -1;
    }

    function renderSuggestions(list, displaySep) {
      suggestionsContainer.innerHTML = '';
      currentSuggestions = Array.isArray(list) ? list.slice() : [];
      if (!currentSuggestions.length) {
        suggestionsContainer.style.display = 'none';
        selectedIndex = -1;
        return;
      }
      currentSuggestions.forEach((item, idx) => {
        const el = document.createElement('div');
        el.className = 'sa-suggestion-item';
        el.dataset.index = String(idx);
        const displayName = item.isDir ? item.name + displaySep : item.name;
        Object.assign(el.style, { padding: '8px 12px', cursor: 'pointer', fontSize: '14px', color: '#111', display: 'flex', alignItems: 'center', gap: '8px', userSelect: 'none' });
        el.textContent = displayName;
        el.addEventListener('pointerdown', (e) => { e.preventDefault(); e.stopPropagation(); acceptSuggestion(idx); });
        el.addEventListener('mousedown', (e) => { if (e.button === 0) { e.preventDefault(); e.stopPropagation(); acceptSuggestion(idx); } });
        el.addEventListener('mouseover', () => setSelectedIndex(idx));
        el.addEventListener('mouseout', () => setSelectedIndex(-1));
        suggestionsContainer.appendChild(el);
      });
      suggestionsContainer.style.display = 'flex';
    }

    function setSelectedIndex(idx) {
      const items = suggestionsContainer.querySelectorAll('.sa-suggestion-item');
      selectedIndex = idx;
      items.forEach((it, i) => {
        Object.assign(it.style, { background: (i === idx ? '#eef8ff' : '#fff') });
      });
    }

    function acceptSuggestion(idx) {
      if (!currentSuggestions || !currentSuggestions[idx]) return;
      const item = currentSuggestions[idx];
      const val = input.value;
      const lastSlash = val.lastIndexOf('/');
      const lastBack = val.lastIndexOf('\\');
      const sep = lastBack > lastSlash ? '\\' : '/';
      const sepIndex = Math.max(lastSlash, lastBack);
      const dirPart = sepIndex >= 0 ? val.slice(0, sepIndex) : '';
      const left = dirPart ? dirPart + sep : '';
      const newVal = left + item.name + (item.isDir ? sep : '');
      input.value = newVal;
      input.focus();
      clearSuggestions();
      updateTargetInfo();
    }

    function splitInputToDirAndFragment(value) {
      const lastSlash = value.lastIndexOf('/');
      const lastBack = value.lastIndexOf('\\');
      const sepIndex = Math.max(lastSlash, lastBack);
      const sep = sepIndex >= 0 ? value[sepIndex] : '/';
      const dirPart = sepIndex >= 0 ? value.slice(0, sepIndex) : '';
      const fragment = value.slice(sepIndex + 1);
      const dirSegments = dirPart ? dirPart.split(/[\\/]+/).filter(Boolean) : [];
      return { dirSegments, fragment, sep };
    }

    async function attemptAutocomplete(explicit = false) {
      const raw = input.value;
      const { dirSegments, fragment, sep } = splitInputToDirAndFragment(raw);
      const baseDirPath = path.join(projectRoot, relativePath, ...dirSegments);

      let entries = [];
      try {
        const rawList = await fs.readdir(baseDirPath, { withFileTypes: true });
        if (rawList && rawList.length && typeof rawList[0] === 'object') {
          entries = rawList.map(d => ({ name: d.name, isDir: (typeof d.isDirectory === 'function') ? d.isDirectory() : !!d.isDirectory }));
        } else {
          const names = await fs.readdir(baseDirPath);
          entries = await Promise.all(names.map(async (n) => {
            try {
              const s = await fs.stat(path.join(baseDirPath, n));
              return { name: n, isDir: s.isDirectory() };
            } catch (e) {
              return { name: n, isDir: false };
            }
          }));
        }
      } catch (err) {
        clearSuggestions();
        return;
      }

      const fragLower = fragment.toLowerCase();
      let matches = entries.filter(e => e.name.toLowerCase().startsWith(fragLower));
      if (!matches.length && fragment === '') matches = entries;

      if (!matches.length) { clearSuggestions(); return; }

      if (matches.length === 1) {
        const single = matches[0];
        if (explicit) {
          const left = dirSegments.length ? dirSegments.join(sep) + sep : '';
          input.value = left + single.name + (single.isDir ? sep : '');
          input.focus();
          clearSuggestions();
          updateTargetInfo();
          return;
        } else {
          renderSuggestions(matches, sep);
          setSelectedIndex(-1);
          return;
        }
      }

      renderSuggestions(matches, sep);
      setSelectedIndex(-1);
    }

    input.addEventListener('keydown', async (e) => {
      if (suggestionsContainer.style.display !== 'none' && (e.key === 'ArrowDown' || e.key === 'ArrowUp')) {
        e.preventDefault();
        const items = suggestionsContainer.querySelectorAll('.sa-suggestion-item');
        if (!items.length) return;
        if (e.key === 'ArrowDown') {
          const next = selectedIndex + 1 >= items.length ? 0 : selectedIndex + 1;
          setSelectedIndex(next);
        } else {
          const prev = selectedIndex - 1 < 0 ? items.length - 1 : selectedIndex - 1;
          setSelectedIndex(prev);
        }
        return;
      }
      if (e.key === 'Tab') {
        e.preventDefault();
        await attemptAutocomplete(true);
        return;
      }
      if (e.key === 'Enter') {
        e.preventDefault();
        // If suggestion selected, accept instead
        if (suggestionsContainer.style.display !== 'none' && selectedIndex >= 0 && currentSuggestions[selectedIndex]) {
          acceptSuggestion(selectedIndex);
          return;
        }
        const val = input.value.trim();
        if (!val) { finish(null); return; }
        const segments = val.split(/[\\/]+/).filter(Boolean);
        const fullPath = path.join(projectRoot, relativePath, ...segments);
        finish(fullPath);
        return;
      }
      if (e.key === 'Escape') {
        e.preventDefault();
        finish(null);
        return;
      }
    });

    input.addEventListener('input', () => { updateTargetInfo(); });

    // Close if clicking outside, with tolerance if utility available
    const closePopup = () => finish(null);
    let cleanupTolerance = null;
    try {
      if (createPopupWithTolerance) cleanupTolerance = createPopupWithTolerance(overlay, container, closePopup, 30);
    } catch (_) { /* ignore */ }

    // Clean up on DOM removal
    const observer = new MutationObserver(() => {
      if (!document.body.contains(overlay)) {
        try { if (cleanupTolerance) cleanupTolerance(); } catch (_) {}
        observer.disconnect();
        finish(null);
      }
    });
    observer.observe(document.body, { childList: true });

    // Hide suggestions shortly after blur so clicks can register
    input.addEventListener('blur', () => { setTimeout(() => clearSuggestions(), 150); });

    updateTargetInfo();
  });
}

module.exports = { promptSavePath };