const fs = require('fs').promises;
const path = require('path');
const { scanDirectoryTree } = require('../scan');
const { renderDirectoryTree } = require('../render');
const { clipboard } = require('electron');
const { createPopupWithTolerance } = require('../popupUtils');

async function createFile(projectRoot, relativePath = '') {
  // Detect clipboard content to auto-enable clipboard toggle
  let clipboardHasContent = false;
  let clipboardText = '';
  
  try {
    clipboardText = clipboard.readText() || '';
    clipboardHasContent = clipboardText.trim() !== '';
  } catch (err) {
    console.warn('Failed to read clipboard:', err);
  }
  
  const overlay = document.createElement('div');
  Object.assign(overlay.style, {
    position: 'fixed',
    top: '0',
    left: '0',
    width: '100vw',
    height: '100vh',
    background: 'rgba(0, 0, 0, 0.5)',
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
    zIndex: '1000'
  });

  // Outer card/container
  const container = document.createElement('div');
  Object.assign(container.style, {
    display: 'flex',
    flexDirection: 'column',
    gap: '8px',
    background: '#fff',
    padding: '14px',
    borderRadius: '8px',
    boxShadow: '0 6px 18px rgba(0, 0, 0, 0.12)',
    width: 'min(92vw, 720px)',
    maxWidth: '720px',
    boxSizing: 'border-box'
  });

  // Row with input + Use Clipboard toggle
  const row = document.createElement('div');
  Object.assign(row.style, {
    display: 'flex',
    alignItems: 'center',
    gap: '8px'
  });

  const input = document.createElement('input');
  input.type = 'text';
  input.placeholder = 'Enter file path (e.g., src/utils/hello.txt) — press Tab to autocomplete';
  Object.assign(input.style, {
    padding: '10px',
    flex: '1',
    fontSize: '14px',
    borderRadius: '6px',
    border: '1px solid #d9e1ec',
    outline: 'none',
    boxSizing: 'border-box'
  });

  // Use Clipboard toggle button
  const useClipboardBtn = document.createElement('button');
  let useClipboard = clipboardHasContent; // Auto-enable if clipboard has content
  function updateClipboardBtn() {
    useClipboardBtn.textContent = `Use Clipboard: ${useClipboard ? 'on' : 'off'}`;
    Object.assign(useClipboardBtn.style, {
      padding: '8px 12px',
      fontSize: '13px',
      borderRadius: '6px',
      border: '1px solid ' + (useClipboard ? '#0b66ff' : '#ddd'),
      background: useClipboard ? 'linear-gradient(180deg, #0b66ff, #075fe6)' : '#f5f7fa',
      color: useClipboard ? '#fff' : '#111',
      cursor: 'pointer',
      minWidth: '120px',
      height: '40px',
      boxSizing: 'border-box'
    });
  }
  useClipboardBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    useClipboard = !useClipboard;
    updateClipboardBtn();
    scheduleClipboardCheck();
    input.focus();
  });
  updateClipboardBtn();

  row.appendChild(input);
  row.appendChild(useClipboardBtn);

  // NEW: target path display (one-line info about where the file will be created)
  const targetInfo = document.createElement('div');
  Object.assign(targetInfo.style, {
    fontSize: '12px',
    color: '#333',
    background: '#f7f9fb',
    padding: '8px 10px',
    borderRadius: '6px',
    border: '1px solid #eef4ff',
    display: 'none',
    wordBreak: 'break-all'
  });
  function updateTargetInfo() {
    const val = input.value.trim();
    if (!val) {
      // show the folder where a new file would be created by default
      targetInfo.style.display = 'block';
      targetInfo.textContent = `The following file directory will be created in this folder: ${path.join(projectRoot, relativePath)}`;
      return;
    }

    // If ends with slash -> it's a directory; show that as target folder
    const lastChar = val.slice(-1);
    if (lastChar === '/' || lastChar === '\\') {
      const dirSegments = val.split(/[\\/]+/).filter(Boolean);
      const dirPath = path.join(projectRoot, relativePath, ...dirSegments);
      targetInfo.style.display = 'block';
      targetInfo.textContent = `The following file directory will be created in this folder: ${dirPath}`;
      return;
    }

    // Otherwise show the directory portion of the file path (where the file will be created)
    const segments = val.split(/[\\/]+/).filter(Boolean);
    const dirSegments = segments.slice(0, -1); // everything but last (filename)
    const dirPath = dirSegments.length ? path.join(projectRoot, relativePath, ...dirSegments) : path.join(projectRoot, relativePath);
    targetInfo.style.display = 'block';
    targetInfo.textContent = `The following file directory will be created in this folder: ${dirPath}`;
  }

  // Warning about overwriting content (hidden by default)
  const warningDiv = document.createElement('div');
  Object.assign(warningDiv.style, {
    display: 'none',
    color: '#7a0b0b',
    background: '#fff2f2',
    border: '1px solid #f5c6cb',
    padding: '8px 12px',
    borderRadius: '6px',
    fontSize: '13px',
    marginTop: '6px'
  });
  warningDiv.textContent = 'Warning: When Enter is pressed, the selected file\'s content will be replaced with the clipboard content.';

  // Suggestions container (shows autocomplete choices)
  const suggestionsContainer = document.createElement('div');
  Object.assign(suggestionsContainer.style, {
    maxHeight: '260px',
    overflowY: 'auto',
    borderRadius: '6px',
    border: '1px solid transparent',
    boxShadow: '0 6px 18px rgba(0,0,0,0.08)',
    background: '#fff',
    display: 'none', // hidden by default
    flexDirection: 'column',
    gap: '0',
    padding: '6px 0',
    boxSizing: 'border-box'
  });

  // Small hint line
  const hint = document.createElement('div');
  hint.textContent = 'Tip: press Tab to autocomplete; click a suggestion to insert it. Folders will append a trailing "/"';
  Object.assign(hint.style, {
    fontSize: '12px',
    color: '#666',
    marginTop: '4px'
  });

  container.appendChild(row);
  container.appendChild(targetInfo); // <-- new info line
  container.appendChild(warningDiv);
  container.appendChild(suggestionsContainer);
  container.appendChild(hint);
  overlay.appendChild(container);
  document.body.appendChild(overlay);

  input.focus();

  // State for suggestions / keyboard navigation
  let currentSuggestions = [];
  let selectedIndex = -1;

  function clearSuggestions() {
    suggestionsContainer.innerHTML = '';
    suggestionsContainer.style.display = 'none';
    currentSuggestions = [];
    selectedIndex = -1;
  }

  function renderSuggestions(list, displaySep = '/') {
    suggestionsContainer.innerHTML = '';
    currentSuggestions = Array.isArray(list) ? list.slice() : [];
    if (!currentSuggestions.length) {
      suggestionsContainer.style.display = 'none';
      selectedIndex = -1;
      return;
    }

    currentSuggestions.forEach((item, idx) => {
      const el = document.createElement('div');
      el.className = 'cf-suggestion-item';
      el.dataset.index = String(idx);
      const displayName = item.isDir ? item.name + displaySep : item.name;
      Object.assign(el.style, {
        padding: '8px 12px',
        cursor: 'pointer',
        fontSize: '14px',
        color: '#111',
        display: 'flex',
        alignItems: 'center',
        gap: '8px',
        userSelect: 'none'
      });

      el.textContent = displayName;

      // Use pointerdown so selection works before input blur hides suggestions (works for touch/mouse)
      el.addEventListener('pointerdown', (e) => {
        e.preventDefault();
        e.stopPropagation();
        acceptSuggestion(idx);
      });

      // Fallback for environments without pointer events
      el.addEventListener('mousedown', (e) => {
        // only run if pointerdown didn't run (prevent double-invoke); pointerdown runs first in modern browsers.
        if (e.button === 0) {
          e.preventDefault();
          e.stopPropagation();
          acceptSuggestion(idx);
        }
      });

      el.addEventListener('mouseover', () => {
        setSelectedIndex(idx);
      });
      el.addEventListener('mouseout', () => {
        setSelectedIndex(-1);
      });
      suggestionsContainer.appendChild(el);
    });
    suggestionsContainer.style.display = 'flex';
  }

  function setSelectedIndex(idx) {
    const items = suggestionsContainer.querySelectorAll('.cf-suggestion-item');
    selectedIndex = idx;
    items.forEach((it, i) => {
      if (i === idx) {
        Object.assign(it.style, {
          background: '#eef8ff'
        });
      } else {
        Object.assign(it.style, {
          background: '#fff'
        });
      }
    });
  }

  function acceptSuggestion(idx) {
    if (!currentSuggestions || !currentSuggestions[idx]) return;
    const item = currentSuggestions[idx];

    // Determine separator used in current input (prefer last separator)
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
    scheduleClipboardCheck();
    updateTargetInfo();
  }

  // Utility: get directory to list and the fragment to match
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

  // Attempt autocomplete logic - Tab triggers this.
  // explicit === true means user pressed Tab and wants immediate autofill when there is a single match.
  // explicit === false will only show suggestions and never auto-fill.
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

    // Filter matches (case-insensitive)
    const fragLower = fragment.toLowerCase();
    let matches = entries.filter(e => e.name.toLowerCase().startsWith(fragLower));
    // If nothing matches and fragment is empty, show all entries
    if (!matches.length && fragment === '') matches = entries;

    if (!matches.length) {
      clearSuggestions();
      return;
    }

    // If only one match, only autofill when explicit (Tab). Otherwise show suggestion list so user can choose.
    if (matches.length === 1) {
      const single = matches[0];
      if (explicit) {
        const left = dirSegments.length ? dirSegments.join(sep) + sep : '';
        input.value = left + single.name + (single.isDir ? sep : '');
        input.focus();
        clearSuggestions();
        scheduleClipboardCheck();
        updateTargetInfo();
        return;
      } else {
        // show the single match as a selectable suggestion (do not autofill)
        renderSuggestions(matches, sep);
        setSelectedIndex(-1);
        return;
      }
    }

    // More than one match: show possibilities
    renderSuggestions(matches, sep);
    setSelectedIndex(-1);
  }

  // Debounced clipboard-file-exists check
  let checkTimer = null;
  function scheduleClipboardCheck(delay = 250) {
    if (checkTimer) clearTimeout(checkTimer);
    checkTimer = setTimeout(async () => {
      checkTimer = null;
      await updateClipboardWarning();
      updateTargetInfo();
    }, delay);
  }

  async function updateClipboardWarning() {
    // Only show warning if "Use Clipboard" is turned on
    if (!useClipboard) {
      warningDiv.style.display = 'none';
      return;
    }

    const val = input.value.trim();
    if (!val) {
      warningDiv.style.display = 'none';
      return;
    }

    // If user clearly typed a directory (ends with slash), don't warn
    const lastChar = val.slice(-1);
    if (lastChar === '/' || lastChar === '\\') {
      warningDiv.style.display = 'none';
      return;
    }

    // Build full path under projectRoot + relativePath
    const segments = val.split(/[\\/]+/).filter(Boolean);
    const fullPath = path.join(projectRoot, relativePath, ...segments);

    try {
      const s = await fs.stat(fullPath);
      // fs.stat from fs.promises returns Stats; check if it's a file and has size > 0
      if (s.isFile && s.isFile() && s.size > 0) {
        // Show red warning
        warningDiv.textContent = `Warning: When Enter is pressed, the selected file's content will be replaced with the clipboard content.`;
        warningDiv.style.display = 'block';
      } else {
        warningDiv.style.display = 'none';
      }
    } catch (err) {
      // File doesn't exist or can't stat -> no warning
      warningDiv.style.display = 'none';
    }
  }

  // Keyboard handling: Tab (autocomplete), Up/Down to navigate suggestions, Enter to accept or create
  input.addEventListener('keydown', async (e) => {
    // If suggestions visible and arrow keys pressed, navigate
    if (suggestionsContainer.style.display !== 'none' && (e.key === 'ArrowDown' || e.key === 'ArrowUp')) {
      e.preventDefault();
      const items = suggestionsContainer.querySelectorAll('.cf-suggestion-item');
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
      // explicit Tab -> may autofill single match
      await attemptAutocomplete(true);
      return;
    }

    if (e.key === 'Enter') {
      // If a suggestion is selected, accept it instead of creating a file
      if (suggestionsContainer.style.display !== 'none' && selectedIndex >= 0 && currentSuggestions[selectedIndex]) {
        e.preventDefault();
        acceptSuggestion(selectedIndex);
        return;
      }

      // Otherwise proceed to create the file (this will overwrite if file exists)
      e.preventDefault();
      const fileName = input.value.trim();
      if (fileName) {
        try {
          // Build segments (split on both slashes)
          const segments = fileName.split(/[\\/]+/).filter(Boolean);
          const fullPath = path.join(projectRoot, relativePath, ...segments);
          const dirPath = path.dirname(fullPath);
          await fs.mkdir(dirPath, { recursive: true });

          let contents = '';
          if (useClipboard) {
            try {
              // Read clipboard fresh at the moment of file creation
              contents = clipboard.readText() || '';
            } catch (err) {
              console.error('Failed to read clipboard:', err);
              contents = '';
            }
          }

          // Write file with contents (overwrites if exists)
          await fs.writeFile(fullPath, contents, 'utf8');

          // Re-scan and re-render the directory tree to reflect the new file
          const tree = await scanDirectoryTree(projectRoot);
          const containerEl = document.getElementById('directory-viewer-content');
          if (containerEl) {
            containerEl.innerHTML = '';
            renderDirectoryTree(tree, [fullPath]);
          }

        } catch (err) {
          console.error('Failed to create file:', err);
          alert('Failed to create file.');
        }
      }
      // Close overlay in any case
      if (document.body.contains(overlay)) {
        document.body.removeChild(overlay);
      }
    }
  });

  // Also listen to input events to update the clipboard-warning check
  input.addEventListener('input', () => {
    scheduleClipboardCheck();
    updateTargetInfo();
  });

  // Set up tolerance zone for the createFile popup
  const closeCreateFilePopup = () => {
    if (document.body.contains(overlay)) {
      document.body.removeChild(overlay);
    }
  };
  
  const cleanupTolerance = createPopupWithTolerance(overlay, container, closeCreateFilePopup, 30);

  // Clean up when overlay is removed (defensive)
  function cleanup() {
    clearSuggestions();
    if (checkTimer) {
      clearTimeout(checkTimer);
      checkTimer = null;
    }
    if (cleanupTolerance) {
      cleanupTolerance();
    }
  }

  // In case overlay is removed externally, try to cleanup
  const observer = new MutationObserver(() => {
    if (!document.body.contains(overlay)) {
      cleanup();
      observer.disconnect();
    }
  });
  observer.observe(document.body, { childList: true });

  // Focus handling: hide suggestions when input loses focus (but keep if clicking a suggestion)
  input.addEventListener('blur', () => {
    // Delay clearing to allow pointerdown handlers on suggestions to run first
    setTimeout(() => {
      // if suggestions were just used (they handle their own pointerdown/accept), they'll have cleared themselves
      clearSuggestions();
    }, 150);
  });

  // initial target info
  updateTargetInfo();
}

module.exports = { createFile };