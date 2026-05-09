// src/buttons/searchFile.js
const fs = require('fs').promises;
const path = require('path');
const { execFile } = require('child_process');
const { ipcRenderer, clipboard } = require('electron');
const monacoManager = require('../../editor/monacoManager');
const tabManagement = require('../../editor/tabManagement');

/**
 * Search files UI and actions.
 * projectRoot: absolute root path of the project.
 * relativePath: safe relative path (under projectRoot) that was right-clicked; can be ''.
 */
async function searchFile(projectRoot, relativePath = '') {
  // Search mode state: 'name' (file name) or 'content' (file content)
  let currentMode = 'name';
  let modeMenuVisible = false;
  let searchInput;

  // Overlay backdrop
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

  // Card container (main)
  const card = document.createElement('div');
  Object.assign(card.style, {
    display: 'flex',
    gap: '14px',
    background: '#fff',
    padding: '18px',
    borderRadius: '8px',
    boxShadow: '0 6px 24px rgba(0,0,0,0.12)',
    width: 'min(92vw, 980px)',
    maxWidth: '980px',
    height: 'min(86vh, 760px)',
    boxSizing: 'border-box'
  });

  // LEFT column (controls + results)
  const left = document.createElement('div');
  Object.assign(left.style, {
    display: 'flex',
    flexDirection: 'column',
    gap: '10px',
    flex: '1',
    minWidth: '360px',
    overflow: 'hidden'
  });

  // Title
  const title = document.createElement('div');
  title.textContent = 'Search a File';
  Object.assign(title.style, { fontSize: '16px', fontWeight: '600', color: '#111' });

  // Folder input row
  const folderRow = document.createElement('div');
  Object.assign(folderRow.style, { display: 'flex', gap: '8px', alignItems: 'center' });

  const folderLabel = document.createElement('div');
  folderLabel.textContent = 'Folder you\'re in:';
  Object.assign(folderLabel.style, { fontSize: '13px', color: '#333', minWidth: '120px' });

  const folderInput = document.createElement('input');
  folderInput.type = 'text';
  folderInput.placeholder = 'Enter folder path (relative to project root) — press Tab to autocomplete';
  folderInput.value = relativePath || '';
  Object.assign(folderInput.style, {
    padding: '10px',
    flex: '1',
    fontSize: '14px',
    borderRadius: '6px',
    border: '1px solid #d6dde6',
    background: '#fff',
    outline: 'none',
    boxSizing: 'border-box'
  });

  folderRow.appendChild(folderLabel);
  folderRow.appendChild(folderInput);

// Mode selection row (custom dropdown to avoid flicker and keep menu open until click elsewhere)
  const modeRow = document.createElement('div');
  Object.assign(modeRow.style, { display: 'flex', gap: '8px', alignItems: 'center' });

  const modeLabel = document.createElement('div');
  modeLabel.textContent = 'Mode:';
  Object.assign(modeLabel.style, { fontSize: '13px', color: '#333', minWidth: '60px' });

  const modeSelectWrapper = document.createElement('div');
  Object.assign(modeSelectWrapper.style, {
    position: 'relative',
    display: 'inline-flex',
    flexDirection: 'column'
  });

  const modeDisplayBtn = document.createElement('button');
  modeDisplayBtn.type = 'button';
  Object.assign(modeDisplayBtn.style, {
    padding: '8px',
    borderRadius: '6px',
    border: '1px solid #d6dde6',
    background: '#fff',
    fontSize: '13px',
    cursor: 'pointer',
    minWidth: '140px',
    textAlign: 'left'
  });

  const modeOptions = document.createElement('div');
  Object.assign(modeOptions.style, {
    position: 'absolute',
    top: '100%',
    left: '0',
    marginTop: '4px',
    minWidth: '140px',
    borderRadius: '6px',
    border: '1px solid #d6dde6',
    background: '#fff',
    boxShadow: '0 4px 16px rgba(0,0,0,0.15)',
    zIndex: '1100',
    display: 'none',
    flexDirection: 'column',
    overflow: 'hidden'
  });

  function updateModeUI() {
    const label = currentMode === 'content' ? 'File content' : 'File name';
    modeDisplayBtn.textContent = label;
    if (searchInput) {
      if (currentMode === 'content') {
        searchInput.placeholder = 'Type text to search inside files and press Enter';
      } else {
        searchInput.placeholder = 'Type file name (e.g., hello.txt) and press Enter';
      }
    }
  }

  function handleDocumentClickForMode(e) {
    if (!modeSelectWrapper.contains(e.target)) {
      hideModeMenu();
    }
  }

  function hideModeMenu() {
    if (!modeMenuVisible) return;
    modeMenuVisible = false;
    modeOptions.style.display = 'none';
    document.removeEventListener('click', handleDocumentClickForMode);
  }

  function showModeMenu() {
    if (modeMenuVisible) return;
    modeMenuVisible = true;
    modeOptions.style.display = 'flex';
    document.addEventListener('click', handleDocumentClickForMode);
  }

  function setMode(mode) {
    if (mode !== 'name' && mode !== 'content') return;
    currentMode = mode;
    updateModeUI();
    hideModeMenu();
  }

  const modeOptionName = document.createElement('div');
  modeOptionName.textContent = 'File name';
  Object.assign(modeOptionName.style, {
    padding: '6px 10px',
    fontSize: '13px',
    cursor: 'pointer'
  });
  modeOptionName.addEventListener('click', (e) => {
    e.stopPropagation();
    setMode('name');
  });

  const modeOptionContent = document.createElement('div');
  modeOptionContent.textContent = 'File content';
  Object.assign(modeOptionContent.style, {
    padding: '6px 10px',
    fontSize: '13px',
    cursor: 'pointer'
  });
  modeOptionContent.addEventListener('click', (e) => {
    e.stopPropagation();
    setMode('content');
  });

  modeOptions.appendChild(modeOptionName);
  modeOptions.appendChild(modeOptionContent);

  modeDisplayBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    if (modeMenuVisible) hideModeMenu();
    else showModeMenu();
  });

  modeSelectWrapper.appendChild(modeDisplayBtn);
  modeSelectWrapper.appendChild(modeOptions);

  modeRow.appendChild(modeLabel);
  modeRow.appendChild(modeSelectWrapper);

  // initialize label
  updateModeUI();

  // Small target info for the folder chosen
  const targetInfo = document.createElement('div');
  Object.assign(targetInfo.style, {
    fontSize: '12px',
    color: '#333',
    background: '#f7f9fb',
    padding: '8px 10px',
    borderRadius: '6px',
    border: '1px solid #eef4ff',
    display: 'block',
    wordBreak: 'break-all'
  });
  function updateTargetInfo() {
    const val = folderInput.value.trim();
    const base = val ? path.join(projectRoot, val) : path.join(projectRoot, relativePath || '');
    targetInfo.textContent = `Search base folder: ${base}`;
  }

  // Suggestions container for folder autocomplete
  const suggestionsContainer = document.createElement('div');
  Object.assign(suggestionsContainer.style, {
    maxHeight: '160px',
    overflowY: 'auto',
    borderRadius: '6px',
    border: '1px solid transparent',
    background: '#fff',
    display: 'none',
    flexDirection: 'column',
    gap: '0',
    padding: '6px 0',
    boxSizing: 'border-box'
  });

// Search input row
  const searchRow = document.createElement('div');
  Object.assign(searchRow.style, { display: 'flex', gap: '8px', alignItems: 'center' });

  searchInput = document.createElement('input');
  searchInput.type = 'text';
  searchInput.placeholder = 'Type file name (e.g., hello.txt) and press Enter';
  Object.assign(searchInput.style, {
    padding: '10px',
    flex: '1',
    fontSize: '14px',
    borderRadius: '6px',
    border: '1px solid #d6dde6',
    background: '#fff',
    outline: 'none',
    boxSizing: 'border-box'
  });
  // ensure placeholder matches current mode
  updateModeUI();

  const searchBtn = document.createElement('button');
  searchBtn.textContent = 'Search';
  Object.assign(searchBtn.style, {
    padding: '8px 12px',
    fontSize: '13px',
    borderRadius: '6px',
    border: '1px solid #bcd4ff',
    background: '#eef6ff',
    color: '#000',
    cursor: 'pointer'
  });

  searchRow.appendChild(searchInput);
  searchRow.appendChild(searchBtn);

  // Results container
  const resultsContainer = document.createElement('div');
  Object.assign(resultsContainer.style, {
    display: 'flex',
    flexDirection: 'column',
    gap: '6px',
    overflowY: 'auto',
    border: '1px solid #e6eef7',
    borderRadius: '6px',
    padding: '8px',
    background: '#fff',
    boxSizing: 'border-box',
    flex: '1'
  });

  // status line
  const statusDiv = document.createElement('div');
  Object.assign(statusDiv.style, { minHeight: '18px', color: '#b02a2a', fontSize: '13px' });

  left.appendChild(title);
  left.appendChild(folderRow);
  left.appendChild(modeRow);
  left.appendChild(targetInfo);
  left.appendChild(suggestionsContainer);
  left.appendChild(searchRow);
  left.appendChild(resultsContainer);
  left.appendChild(statusDiv);

  // RIGHT column (options/actions for selected items)
  const right = document.createElement('div');
  Object.assign(right.style, {
    width: '320px',
    minWidth: '260px',
    border: '1px solid #e0e6ee',
    borderRadius: '6px',
    overflow: 'auto',
    background: '#fafafa',
    padding: '10px',
    boxSizing: 'border-box',
    display: 'flex',
    flexDirection: 'column',
    gap: '10px'
  });

  const rightTitle = document.createElement('div');
  rightTitle.textContent = 'Options';
  Object.assign(rightTitle.style, { fontWeight: '600' });

  const selectedInfo = document.createElement('div');
  selectedInfo.textContent = 'No selection';
  Object.assign(selectedInfo.style, { fontSize: '13px', color: '#333' });

  // Buttons in right area
  function makeBtn(text) {
    const btn = document.createElement('button');
    btn.textContent = text;
    Object.assign(btn.style, {
      padding: '8px 12px',
      fontSize: '13px',
      borderRadius: '6px',
      border: '1px solid #d6dde6',
      background: '#f7f9fb',
      color: '#000',
      cursor: 'pointer',
      textAlign: 'left'
    });
    return btn;
  }

  const btnCopyAppend = makeBtn('Copy file(s) to clipboard (append)');
  const btnCopyReplace = makeBtn('Copy file(s) to clipboard (replace)');
  const btnCopyRel = makeBtn('Copy relative path(s)');
  const btnCopyFull = makeBtn('Copy full path(s)');
  const btnDelete = makeBtn('Delete selected file(s)');
  const btnRename = makeBtn('Rename selected file (single)');
  const btnReveal = makeBtn('Reveal in Folder');
  const btnOpenEditor = makeBtn('Open file(s) in editor');

  // Add all to right column
  right.appendChild(rightTitle);
  right.appendChild(selectedInfo);
  right.appendChild(btnCopyAppend);
  right.appendChild(btnCopyReplace);
  right.appendChild(btnCopyRel);
  right.appendChild(btnCopyFull);
  right.appendChild(btnDelete);
  right.appendChild(btnRename);
  right.appendChild(btnReveal);
  right.appendChild(btnOpenEditor);

  card.appendChild(left);
  card.appendChild(right);
  overlay.appendChild(card);
  document.body.appendChild(overlay);
  folderInput.focus();
  updateTargetInfo();

  // update placeholder based on mode happens via updateModeUI()/setMode in the custom dropdown.

  // State
  let currentFolderSuggestions = [];
  let selectedFiles = new Map(); // key: fullPath -> { fullPath, relPath, name, score }
  let lastResults = []; // array of {fullPath, relPath, name, score}
  let acTimer = null;

  // Helper: clear suggestions
  function clearSuggestions() {
    suggestionsContainer.innerHTML = '';
    suggestionsContainer.style.display = 'none';
    currentFolderSuggestions = [];
  }

  function renderFolderSuggestions(list, displaySep = '/') {
    suggestionsContainer.innerHTML = '';
    currentFolderSuggestions = Array.isArray(list) ? list.slice() : [];
    if (!currentFolderSuggestions.length) {
      suggestionsContainer.style.display = 'none';
      return;
    }
    currentFolderSuggestions.forEach((item, idx) => {
      const el = document.createElement('div');
      el.className = 'sf-folder-suggestion';
      el.dataset.index = String(idx);
      const displayName = item.name + (item.isDir ? displaySep : '');
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
      el.addEventListener('pointerdown', (e) => { e.preventDefault(); e.stopPropagation(); acceptFolderSuggestion(idx); });
      el.addEventListener('mousedown', (e) => { if (e.button === 0) { e.preventDefault(); e.stopPropagation(); acceptFolderSuggestion(idx); } });
      el.addEventListener('mouseover', () => el.style.background = '#eef6ff');
      el.addEventListener('mouseout', () => el.style.background = '');
      suggestionsContainer.appendChild(el);
    });
    suggestionsContainer.style.display = 'flex';
  }

  function acceptFolderSuggestion(idx) {
    if (!currentFolderSuggestions || !currentFolderSuggestions[idx]) return;
    const item = currentFolderSuggestions[idx];
    const val = folderInput.value;
    const lastSlash = Math.max(val.lastIndexOf('/'), val.lastIndexOf('\\'));
    const sep = lastSlash >= 0 && val[lastSlash] === '\\' ? '\\' : '/';
    const left = lastSlash >= 0 ? val.slice(0, lastSlash) + sep : '';
    folderInput.value = left + item.name + (item.isDir ? sep : '');
    folderInput.focus();
    clearSuggestions();
    updateTargetInfo();
  }

  // folder autocomplete logic (Tab triggers this)
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

  async function attemptFolderAutocomplete() {
    const raw = folderInput.value;
    const { dirSegments, fragment, sep } = splitInputToDirAndFragment(raw);
    const baseDirPath = path.join(projectRoot, relativePath || '', ...dirSegments);

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

    // Filter matches (case-insensitive) and only directories
    entries = entries.filter(e => e.isDir);
    const fragLower = fragment.toLowerCase();
    let matches = entries.filter(e => e.name.toLowerCase().startsWith(fragLower));
    if (!matches.length && fragment === '') matches = entries;
    if (!matches.length) { clearSuggestions(); return; }
    if (matches.length === 1) {
      const single = matches[0];
      const left = dirSegments.length ? dirSegments.join(sep) + sep : '';
      folderInput.value = left + single.name + sep;
      folderInput.focus();
      clearSuggestions();
      updateTargetInfo();
      return;
    }
    renderFolderSuggestions(matches, '/');
  }

  // debounce autocomplete on input
  folderInput.addEventListener('input', () => {
    if (acTimer) clearTimeout(acTimer);
    acTimer = setTimeout(() => { attemptFolderAutocomplete().catch(() => {}); }, 160);
    updateTargetInfo();
  });

  folderInput.addEventListener('keydown', async (e) => {
    if (e.key === 'Tab') {
      e.preventDefault();
      await attemptFolderAutocomplete();
    }
  });

  folderInput.addEventListener('blur', () => { setTimeout(() => clearSuggestions(), 120); });

  // Search logic (Levenshtein similarity)
  function levenshtein(a, b) {
    a = (a || '').toLowerCase();
    b = (b || '').toLowerCase();
    if (a === b) return 0;
    const al = a.length;
    const bl = b.length;
    if (al === 0) return bl;
    if (bl === 0) return al;
    const matrix = Array.from({ length: bl + 1 }, () => new Array(al + 1).fill(0));
    for (let i = 0; i <= bl; i++) matrix[i][0] = i;
    for (let j = 0; j <= al; j++) matrix[0][j] = j;
    for (let i = 1; i <= bl; i++) {
      for (let j = 1; j <= al; j++) {
        const cost = a[j - 1] === b[i - 1] ? 0 : 1;
        matrix[i][j] = Math.min(
          matrix[i - 1][j] + 1,
          matrix[i][j - 1] + 1,
          matrix[i - 1][j - 1] + cost
        );
      }
    }
    return matrix[bl][al];
  }

  function similarityPercent(a, b) {
    a = a || '';
    b = b || '';
    if (!a.length && !b.length) return 100;
    const dist = levenshtein(a, b);
    const maxLen = Math.max(a.length, b.length) || 1;
    const percent = Math.round((1 - (dist / maxLen)) * 100);
    return Math.max(0, Math.min(100, percent));
  }

  async function listFilesRecursive(dir, out) {
    try {
      const entries = await fs.readdir(dir, { withFileTypes: true });
      for (const ent of entries) {
        const name = ent.name;
        if (ent.isDirectory()) {
          const lower = name.toLowerCase();
          if (lower === 'node_modules' || lower === '.git' || lower === 'venv' || lower === '__pycache__') continue;
          await listFilesRecursive(path.join(dir, name), out);
        } else if (ent.isFile()) {
          out.push(path.join(dir, name));
        }
      }
    } catch (err) {
      // ignore permission/read errors for certain subfolders
    }
  }

  function renderResults(results) {
    resultsContainer.innerHTML = '';
    if (!results || !results.length) {
      const none = document.createElement('div');
      none.textContent = 'No results.';
      Object.assign(none.style, { color: '#666', padding: '10px', fontSize: '13px' });
      resultsContainer.appendChild(none);
      return;
    }

    results.forEach(r => {
      const item = document.createElement('div');
      Object.assign(item.style, {
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '8px',
        borderRadius: '6px',
        cursor: 'pointer',
        gap: '8px',
        background: selectedFiles.has(r.fullPath) ? '#eef6ff' : '#fff'
      });

      const leftBlock = document.createElement('div');
      Object.assign(leftBlock.style, { display: 'flex', alignItems: 'center', gap: '8px' });

      const checkbox = document.createElement('input');
      checkbox.type = 'checkbox';
      checkbox.checked = selectedFiles.has(r.fullPath);
      checkbox.addEventListener('change', (e) => {
        if (checkbox.checked) selectedFiles.set(r.fullPath, r);
        else selectedFiles.delete(r.fullPath);
        updateSelectionUI();
      });

      const textBlock = document.createElement('div');
      const nameEl = document.createElement('div');
      nameEl.textContent = r.name;
      Object.assign(nameEl.style, { fontSize: '14px', color: '#111' });
      const subEl = document.createElement('div');
      subEl.textContent = r.relPath;
      Object.assign(subEl.style, { fontSize: '12px', color: '#666' });

      textBlock.appendChild(nameEl);
      textBlock.appendChild(subEl);
      leftBlock.appendChild(checkbox);
      leftBlock.appendChild(textBlock);

      const rightBlock = document.createElement('div');
      Object.assign(rightBlock.style, { display: 'flex', alignItems: 'center', gap: '8px' });
      const pct = document.createElement('div');
      pct.textContent = `${r.score}%`;
      Object.assign(pct.style, { fontSize: '13px', color: '#0b66ff', minWidth: '48px', textAlign: 'right' });

      rightBlock.appendChild(pct);

      item.appendChild(leftBlock);
      item.appendChild(rightBlock);

      // Click toggles selection
      item.addEventListener('click', (e) => {
        if (e.target === checkbox) return;
        if (selectedFiles.has(r.fullPath)) selectedFiles.delete(r.fullPath);
        else selectedFiles.set(r.fullPath, r);
        updateSelectionUI();
        checkbox.checked = selectedFiles.has(r.fullPath);
      });

      // Double-click opens preview/editor
      item.addEventListener('dblclick', (e) => {
        if (e.target === checkbox) return;
        openPreviewForFile(r).catch(err => {
          console.error('Failed to open preview:', err);
          statusDiv.textContent = 'Failed to open file preview.';
        });
      });

      resultsContainer.appendChild(item);
    });
  }

  function updateSelectionUI() {
    const selCount = selectedFiles.size;
    selectedInfo.textContent = selCount ? `${selCount} selected` : 'No selection';
    renderResults(lastResults);
  }

  async function performSearch() {
    statusDiv.textContent = '';
    resultsContainer.innerHTML = '';
    lastResults = [];
    selectedFiles.clear();
    updateSelectionUI();

    const query = searchInput.value.trim();
    if (!query) {
      statusDiv.textContent = 'Please enter a search query.';
      return;
    }

    // compute base folder
    const folderVal = folderInput.value.trim();
    const baseSub = folderVal !== '' ? folderVal : (relativePath || '');
    const searchDir = path.join(projectRoot, baseSub);

    // verify folder exists
    try {
      const s = await fs.stat(searchDir);
      if (!s.isDirectory()) {
        statusDiv.textContent = 'Search folder is not a directory.';
        return;
      }
    } catch (err) {
      statusDiv.textContent = 'Search folder not found.';
      return;
    }

    // collect files
    statusDiv.textContent = 'Scanning files... (this may take a moment)';
    const collected = [];
    await listFilesRecursive(searchDir, collected);

    if (!collected.length) {
      statusDiv.textContent = 'No files found in folder.';
      return;
    }

    const mode = currentMode || 'name';

    if (mode === 'name') {
      // compute similarity by filename
      const results = collected.map(fp => {
        const name = path.basename(fp);
        const score = similarityPercent(query, name);
        const relPath = path.relative(projectRoot, fp);
        return { fullPath: fp, relPath, name, score };
      });

      // sort by score desc, then by name
      results.sort((a, b) => (b.score - a.score) || a.name.localeCompare(b.name));

      lastResults = results.slice(0, 1000); // limit to reasonable number
      renderResults(lastResults);
      statusDiv.textContent = `Found ${lastResults.length} files (showing top ${lastResults.length}).`;
      return;
    }

    // mode === 'content'
    // Read files and check if file content INCLUDES the query string (substring match).
    statusDiv.textContent = 'Scanning files for content matches...';
    const MAX_FILE_SIZE = 1024 * 1024 * 5; // 5 MB per file (skip very large files)
    const MAX_TOTAL_READ = 1024 * 1024 * 5; // 5 MB total read across files to avoid huge scans
    const results = [];
    let totalRead = 0;
    const q = query;
    const qLower = q.toLowerCase();
    let checked = 0;

    // A small set of binary-like extensions to skip quickly.
    const binaryExts = new Set([
      '.png', '.jpg', '.jpeg', '.gif', '.bmp', '.ico',
      '.class', '.exe', '.dll', '.so', '.dylib',
      '.pdf', '.zip', '.tar', '.gz', '.7z', '.rar',
      '.mp3', '.mp4', '.mov', '.avi', '.wmv',
      '.woff', '.woff2', '.ttf', '.otf', '.psd'
    ]);

    for (let i = 0; i < collected.length; i++) {
      const fp = collected[i];
      checked++;

      // progress update occasionally so UI doesn't freeze
      if (checked % 150 === 0) {
        statusDiv.textContent = `Scanning files for content matches... checked ${checked}/${collected.length}`;
        await new Promise((res) => setTimeout(res, 0)); // yield to UI
      }

      try {
        const st = await fs.stat(fp);
        if (!st.isFile()) continue;
        if (st.size > MAX_FILE_SIZE) continue; // skip very large files

        const ext = path.extname(fp).toLowerCase();
        if (ext && binaryExts.has(ext)) continue; // skip common binary formats

        let content;
        try {
          content = await fs.readFile(fp, 'utf8');
        } catch (err) {
          // unreadable as utf8 or permission issues - skip
          continue;
        }

        if (!content) continue;
        if (content.indexOf('\0') !== -1) continue; // binary-like file - skip

        totalRead += content.length;
        if (totalRead > MAX_TOTAL_READ) {
          statusDiv.textContent = 'Read limit reached; results may be incomplete.';
          break;
        }

        // Check for substring. First try a direct (case-sensitive) match,
        // then fall back to case-insensitive if not found.
        let matched = false;
        if (content.includes(q)) matched = true;
        else if (content.toLowerCase().includes(qLower)) matched = true;

        if (matched) {
          const relPath = path.relative(projectRoot, fp);
          results.push({ fullPath: fp, relPath, name: path.basename(fp), score: 100 });
        }
      } catch (err) {
        // ignore individual file errors
        continue;
      }
    }

    // sort results by relative path for predictable ordering
    results.sort((a, b) => a.relPath.localeCompare(b.relPath, undefined, { numeric: true, sensitivity: 'base' }));
    lastResults = results.slice(0, 1000);
    renderResults(lastResults);
    statusDiv.textContent = `Found ${lastResults.length} files containing your query (checked ${collected.length} files).`;
  }

  // Native copy helper runner
  async function runCopyBinary(mode, filePaths = []) {
    try {
      const appPath = await ipcRenderer.invoke('get-app-path');
      const binaryPath = path.join(appPath, 'bin', 'copy_files_to_clipboard');
      return await new Promise((resolve, reject) => {
        // increase buffer in case of many files
        execFile(binaryPath, [mode, ...filePaths], { maxBuffer: 1024 * 1024 * 50 }, (err, stdout, stderr) => {
          if (err) {
            reject(new Error(stderr || err.message || 'copy helper failed'));
            return;
          }
          resolve(stdout ? stdout.toString() : '');
        });
      });
    } catch (err) {
      throw err;
    }
  }

  // Copy helpers (use native binary when possible)
  async function copyRelativePaths(items) {
    const lines = items.map(i => i.relPath).join('\n');
    try {
      clipboard.writeText(lines);
      statusDiv.textContent = 'Copied relative path(s) to clipboard.';
    } catch (err) {
      console.error('copyRelativePaths failed', err);
      statusDiv.textContent = 'Failed to copy.';
    }
  }

  async function copyFullPaths(items) {
    const paths = items.map(i => i.fullPath);
    // try native replace with file URLs
    try {
      await runCopyBinary('replace', paths);
      statusDiv.textContent = 'Copied file(s) to clipboard (as file objects).';
    } catch (err) {
      // fallback to text
      try { clipboard.writeText(paths.join('\n')); statusDiv.textContent = 'Copied full path(s) as text (native helper missing).'; }
      catch (e) { statusDiv.textContent = 'Failed to copy.'; }
    }
  }

  async function copyAppendToClipboard(items) {
    const newLines = items.map(i => i.fullPath);

    // Get current clipboard file list (prefer native read)
    let prevLines = [];
    try {
      const stdout = await runCopyBinary('read', []);
      prevLines = stdout.split(/\r?\n/).filter(Boolean);
    } catch (err) {
      // fallback: text clipboard
      const prev = clipboard.readText() || '';
      prevLines = prev.split(/\r?\n/).filter(Boolean);
    }

    // confirm overlay
    const confirmOverlay = document.createElement('div');
    Object.assign(confirmOverlay.style, {
      position: 'fixed',
      top: '0',
      left: '0',
      width: '100vw',
      height: '100vh',
      background: 'rgba(0,0,0,0.45)',
      display: 'flex',
      justifyContent: 'center',
      alignItems: 'center',
      zIndex: '1200'
    });

    const confirmCard = document.createElement('div');
    Object.assign(confirmCard.style, {
      background: '#fff',
      padding: '14px',
      borderRadius: '8px',
      boxShadow: '0 8px 28px rgba(0,0,0,0.18)',
      width: 'min(90vw, 720px)',
      boxSizing: 'border-box'
    });

    const heading = document.createElement('div');
    heading.textContent = 'Confirm append to clipboard';
    Object.assign(heading.style, { fontWeight: '600', marginBottom: '8px' });

    const prevBox = document.createElement('div');
    prevBox.textContent = 'Current clipboard items:';
    Object.assign(prevBox.style, { fontSize: '13px', color: '#333', marginBottom: '6px' });

    const prevList = document.createElement('pre');
    prevList.textContent = prevLines.length ? prevLines.join('\n') : '(clipboard empty)';
    Object.assign(prevList.style, { whiteSpace: 'pre-wrap', fontFamily: 'monospace', background: '#fafafa', padding: '8px', borderRadius: '6px', border: '1px solid #eee', marginBottom: '8px', maxHeight: '160px', overflow: 'auto' });

    const newBox = document.createElement('div');
    newBox.textContent = 'Files to append:';
    Object.assign(newBox.style, { fontSize: '13px', color: '#333', marginBottom: '6px' });

    const newList = document.createElement('pre');
    newList.textContent = newLines.join('\n');
    Object.assign(newList.style, { whiteSpace: 'pre-wrap', fontFamily: 'monospace', background: '#fafafa', padding: '8px', borderRadius: '6px', border: '1px solid #eee', marginBottom: '8px', maxHeight: '160px', overflow: 'auto' });

    const btnRow = document.createElement('div');
    Object.assign(btnRow.style, { display: 'flex', gap: '8px', justifyContent: 'flex-end' });

    const cancelBtn = document.createElement('button');
    cancelBtn.textContent = 'Cancel';
    Object.assign(cancelBtn.style, { padding: '8px 12px', borderRadius: '6px', border: '1px solid #d6dde6', background: '#fff', cursor: 'pointer' });

    const confirmBtn = document.createElement('button');
    confirmBtn.textContent = 'Confirm Append';
    Object.assign(confirmBtn.style, { padding: '8px 12px', borderRadius: '6px', border: '1px solid #bcd4ff', background: '#eef6ff', cursor: 'pointer' });

    btnRow.appendChild(cancelBtn);
    btnRow.appendChild(confirmBtn);

    confirmCard.appendChild(heading);
    confirmCard.appendChild(prevBox);
    confirmCard.appendChild(prevList);
    confirmCard.appendChild(newBox);
    confirmCard.appendChild(newList);
    confirmCard.appendChild(btnRow);
    confirmOverlay.appendChild(confirmCard);
    document.body.appendChild(confirmOverlay);

    cancelBtn.addEventListener('click', () => {
      if (confirmOverlay.parentNode) document.body.removeChild(confirmOverlay);
    });

    confirmBtn.addEventListener('click', async () => {
      try {
        // Try native append; if fails fallback to text
        try {
          await runCopyBinary('append', newLines);
          statusDiv.textContent = 'Appended file(s) to clipboard (as file objects).';
        } catch (err) {
          // fallback: append text lines
          const combined = prevLines.concat(newLines);
          clipboard.writeText(combined.join('\n'));
          statusDiv.textContent = 'Appended file paths as text (native helper missing).';
        }
      } catch (err) {
        console.error('Failed to append to clipboard', err);
        statusDiv.textContent = 'Failed to update clipboard.';
      } finally {
        if (confirmOverlay.parentNode) document.body.removeChild(confirmOverlay);
      }
    });
  }

  // Hook up buttons
  btnCopyRel.addEventListener('click', () => {
    const items = Array.from(selectedFiles.values());
    if (!items.length) { statusDiv.textContent = 'No selection.'; return; }
    copyRelativePaths(items);
  });

  btnCopyFull.addEventListener('click', async () => {
    const items = Array.from(selectedFiles.values());
    if (!items.length) { statusDiv.textContent = 'No selection.'; return; }
    await copyFullPaths(items);
  });

  btnCopyReplace.addEventListener('click', async () => {
    const items = Array.from(selectedFiles.values());
    if (!items.length) { statusDiv.textContent = 'No selection.'; return; }
    const files = items.map(i => i.fullPath);
    try {
      await runCopyBinary('replace', files);
      statusDiv.textContent = 'Replaced clipboard with file(s) (as file objects).';
    } catch (err) {
      clipboard.writeText(files.join('\n'));
      statusDiv.textContent = 'Native helper failed; copied full path(s) as text.';
    }
  });

  btnCopyAppend.addEventListener('click', async () => {
    const items = Array.from(selectedFiles.values());
    if (!items.length) { statusDiv.textContent = 'No selection.'; return; }
    await copyAppendToClipboard(items);
  });

  // Delete selected files (with confirmation)
  btnDelete.addEventListener('click', async () => {
    const items = Array.from(selectedFiles.values());
    if (!items.length) { statusDiv.textContent = 'No selection.'; return; }

    // confirmation overlay
    const confirmOverlay = document.createElement('div');
    Object.assign(confirmOverlay.style, {
      position: 'fixed',
      top: '0',
      left: '0',
      width: '100vw',
      height: '100vh',
      background: 'rgba(0,0,0,0.45)',
      display: 'flex',
      justifyContent: 'center',
      alignItems: 'center',
      zIndex: '1200'
    });

    const cardC = document.createElement('div');
    Object.assign(cardC.style, {
      background: '#fff',
      padding: '14px',
      borderRadius: '8px',
      boxShadow: '0 8px 28px rgba(0,0,0,0.18)',
      width: 'min(90vw, 720px)',
      boxSizing: 'border-box'
    });

    const heading = document.createElement('div');
    heading.textContent = `Delete ${items.length} file(s)? This action cannot be undone.`;
    Object.assign(heading.style, { fontWeight: '600', marginBottom: '8px' });

    const listPre = document.createElement('pre');
    listPre.textContent = items.map(it => it.relPath).join('\n');
    Object.assign(listPre.style, { whiteSpace: 'pre-wrap', fontFamily: 'monospace', background: '#fafafa', padding: '8px', borderRadius: '6px', border: '1px solid #eee', maxHeight: '240px', overflow: 'auto', marginBottom: '8px' });

    const btnRow = document.createElement('div');
    Object.assign(btnRow.style, { display: 'flex', gap: '8px', justifyContent: 'flex-end' });

    const cancelBtn = document.createElement('button');
    cancelBtn.textContent = 'Cancel';
    Object.assign(cancelBtn.style, { padding: '8px 12px', borderRadius: '6px', border: '1px solid #d6dde6', background: '#fff', cursor: 'pointer' });

    const delBtn = document.createElement('button');
    delBtn.textContent = 'Delete';
    Object.assign(delBtn.style, { padding: '8px 12px', borderRadius: '6px', border: '1px solid #d9534f', background: '#ffecec', cursor: 'pointer' });

    btnRow.appendChild(cancelBtn);
    btnRow.appendChild(delBtn);
    cardC.appendChild(heading);
    cardC.appendChild(listPre);
    cardC.appendChild(btnRow);
    confirmOverlay.appendChild(cardC);
    document.body.appendChild(confirmOverlay);

    cancelBtn.addEventListener('click', () => {
      if (confirmOverlay.parentNode) document.body.removeChild(confirmOverlay);
    });

    delBtn.addEventListener('click', async () => {
      try {
        const toDelete = items.map(i => i.fullPath);
        const deleted = [];
        const failed = [];

        for (const fp of toDelete) {
          try {
            await fs.unlink(fp);
            deleted.push(fp);
          } catch (err) {
            failed.push({ fp, err });
          }
        }

        // update results and selection
        const deletedSet = new Set(deleted);
        lastResults = lastResults.filter(r => !deletedSet.has(r.fullPath));
        for (const d of deleted) selectedFiles.delete(d);

        updateSelectionUI();
        renderResults(lastResults);

        let msg = `Deleted ${deleted.length} file(s).`;
        if (failed.length) msg += ` ${failed.length} failed.`;
        statusDiv.textContent = msg;
      } catch (err) {
        console.error('Delete failed', err);
        statusDiv.textContent = `Delete failed: ${err.message || String(err)}`;
      } finally {
        if (confirmOverlay.parentNode) document.body.removeChild(confirmOverlay);
      }
    });
  });

  // Rename single selected file
  btnRename.addEventListener('click', async () => {
    const items = Array.from(selectedFiles.values());
    if (items.length !== 1) { statusDiv.textContent = 'Please select exactly one file to rename.'; return; }
    const target = items[0];

    const overlayR = document.createElement('div');
    Object.assign(overlayR.style, {
      position: 'fixed',
      top: '0',
      left: '0',
      width: '100vw',
      height: '100vh',
      background: 'rgba(0,0,0,0.45)',
      display: 'flex',
      justifyContent: 'center',
      alignItems: 'center',
      zIndex: '1250'
    });

    const cardR = document.createElement('div');
    Object.assign(cardR.style, {
      background: '#fff',
      padding: '14px',
      borderRadius: '8px',
      boxShadow: '0 8px 28px rgba(0,0,0,0.18)',
      width: 'min(90vw, 580px)',
      boxSizing: 'border-box'
    });

    const heading = document.createElement('div');
    heading.textContent = `Rename: ${target.relPath}`;
    Object.assign(heading.style, { fontWeight: '600', marginBottom: '8px' });

    const info = document.createElement('div');
    info.textContent = 'Enter new name (you can include relative subfolder segments) — rename will be performed within the project.';
    Object.assign(info.style, { fontSize: '13px', color: '#333', marginBottom: '8px' });

    const input = document.createElement('input');
    input.type = 'text';
    input.value = target.name;
    input.placeholder = 'new-file-name.ext or path/from/current/folder';
    Object.assign(input.style, { width: '100%', padding: '8px', boxSizing: 'border-box', marginBottom: '10px' });

    const btnRow = document.createElement('div');
    Object.assign(btnRow.style, { display: 'flex', gap: '8px', justifyContent: 'flex-end' });

    const cancelBtn = document.createElement('button');
    cancelBtn.textContent = 'Cancel';
    Object.assign(cancelBtn.style, { padding: '8px 12px', borderRadius: '6px', border: '1px solid #d6dde6', background: '#fff', cursor: 'pointer' });

    const confirmBtn = document.createElement('button');
    confirmBtn.textContent = 'Rename';
    Object.assign(confirmBtn.style, { padding: '8px 12px', borderRadius: '6px', border: '1px solid #bcd4ff', background: '#eef6ff', cursor: 'pointer' });

    btnRow.appendChild(cancelBtn);
    btnRow.appendChild(confirmBtn);
    cardR.appendChild(heading);
    cardR.appendChild(info);
    cardR.appendChild(input);
    cardR.appendChild(btnRow);
    overlayR.appendChild(cardR);
    document.body.appendChild(overlayR);
    input.focus();
    input.select();

    cancelBtn.addEventListener('click', () => {
      if (overlayR.parentNode) document.body.removeChild(overlayR);
    });

    confirmBtn.addEventListener('click', async () => {
      const newName = (input.value || '').trim();
      if (!newName) {
        statusDiv.textContent = 'Please provide a new name.';
        return;
      }
      try {
        const currentDir = path.dirname(target.fullPath);
        const newFull = path.resolve(currentDir, newName);
        const relToProject = path.relative(projectRoot, newFull);
        if (relToProject.startsWith('..')) {
          statusDiv.textContent = 'New path would move file outside of project root — aborting.';
          return;
        }
        // check existence
        try {
          await fs.stat(newFull);
          statusDiv.textContent = 'Target path already exists.';
          return;
        } catch (e) {
          // does not exist - OK
        }

        await fs.rename(target.fullPath, newFull);

        // update lastResults and selection
        for (const r of lastResults) {
          if (r.fullPath === target.fullPath) {
            r.fullPath = newFull;
            r.relPath = path.relative(projectRoot, newFull);
            r.name = path.basename(newFull);
          }
        }
        selectedFiles.clear();
        const updated = lastResults.find(r => r.fullPath === newFull);
        if (updated) selectedFiles.set(newFull, updated);

        updateSelectionUI();
        renderResults(lastResults);

        statusDiv.textContent = `Renamed to ${path.relative(projectRoot, newFull)}`;
      } catch (err) {
        console.error('Rename failed', err);
        statusDiv.textContent = `Rename failed: ${err.message || String(err)}`;
      } finally {
        if (overlayR.parentNode) document.body.removeChild(overlayR);
      }
    });
  });

  // Reveal in OS file manager (single file)
  async function revealInOS(fullPath) {
    const plat = process.platform;
    return new Promise((resolve, reject) => {
      try {
        if (plat === 'darwin') {
          execFile('open', ['-R', fullPath], (err) => { if (err) reject(err); else resolve(); });
        } else if (plat === 'win32') {
          // explorer accepts '/select,<path>'
          execFile('explorer', ['/select,', fullPath], (err) => { if (err) reject(err); else resolve(); });
        } else {
          // linux - open containing folder
          execFile('xdg-open', [path.dirname(fullPath)], (err) => { if (err) reject(err); else resolve(); });
        }
      } catch (err) {
        reject(err);
      }
    });
  }

  btnReveal.addEventListener('click', async () => {
    const items = Array.from(selectedFiles.values());
    if (items.length !== 1) { statusDiv.textContent = 'Please select exactly one file to reveal in folder.'; return; }
    const target = items[0];
    try {
      await revealInOS(target.fullPath);
      statusDiv.textContent = 'Revealed in folder.';
    } catch (err) {
      console.error('Reveal failed', err);
      statusDiv.textContent = `Failed to reveal folder: ${err.message || String(err)}`;
    }
  });

  btnOpenEditor.addEventListener('click', async () => {
    const items = Array.from(selectedFiles.values());
    if (!items.length) {
      statusDiv.textContent = 'No selection.';
      return;
    }
    try {
      for (const item of items) {
        await tabManagement.openFileInNewTab(item.fullPath);
      }
      // Close the search overlay after opening files
      try {
        hideModeMenu();
      } catch (_) {}
      if (document.body.contains(overlay)) {
        document.body.removeChild(overlay);
      }
    } catch (err) {
      console.error('Failed to open file(s) in editor:', err);
      statusDiv.textContent = 'Failed to open file(s) in editor.';
    }
  });

  // ------------------------
  // Monaco-backed file preview / editor
  // - use monacoManager to create a real Monaco editor inside the overlay
  // - decorations highlight full lines that contain the query
  // - clicking a match centers the line in the editor and places the caret at the match
  // - Save/Close behavior with unsaved-change confirmation
  // - removed wrap button (per request)
  // - fix: recompute matches & decorations when model or content changes; use revealRangeInCenter for reliable view positioning
  // ------------------------

  function computeMatchesForText(text, query) {
    const q = (query || '').toLowerCase();
    const lines = text.split(/\r?\n/);
    const matchLines = []; // { lineNumber, occurrences: [{start,end}], snippet }
    if (!q) return matchLines;
    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];
      const lower = (line || '').toLowerCase();
      let idx = lower.indexOf(q);
      const occ = [];
      while (idx !== -1) {
        occ.push({ start: idx, end: idx + q.length });
        idx = lower.indexOf(q, idx + q.length);
      }
      if (occ.length) {
        matchLines.push({
          lineNumber: i + 1,
          occurrences: occ,
          snippet: (line || '').trim().slice(0, 300)
        });
      }
    }
    return matchLines;
  }

  async function openPreviewForFile(fileObj) {
    // create overlay card for preview
    const previewOverlay = document.createElement('div');
    Object.assign(previewOverlay.style, {
      position: 'fixed',
      top: '0',
      left: '0',
      width: '100vw',
      height: '100vh',
      background: 'rgba(0, 0, 0, 0.5)',
      display: 'flex',
      justifyContent: 'center',
      alignItems: 'center',
      zIndex: '1400'
    });

    const previewCard = document.createElement('div');
    Object.assign(previewCard.style, {
      display: 'flex',
      gap: '12px',
      background: '#fff',
      padding: '12px',
      borderRadius: '8px',
      boxShadow: '0 8px 28px rgba(0,0,0,0.18)',
      width: 'min(96vw, 1100px)',
      maxWidth: '1100px',
      maxHeight: '90vh',
      boxSizing: 'border-box',
      overflow: 'hidden'
    });

    // left: editor container
    const editorLeft = document.createElement('div');
    Object.assign(editorLeft.style, { flex: '1', display: 'flex', flexDirection: 'column', gap: '8px', minWidth: '420px', overflow: 'hidden' });

    const header = document.createElement('div');
    header.textContent = `Preview / Edit: ${fileObj.relPath}`;
    Object.assign(header.style, { fontSize: '15px', fontWeight: '600' });

    // toolbar (no wrap button per request)
    const toolbar = document.createElement('div');
    Object.assign(toolbar.style, { display: 'flex', gap: '8px', alignItems: 'center' });

    const copyBtn = document.createElement('button');
    copyBtn.textContent = 'Copy All';
    Object.assign(copyBtn.style, { padding: '6px 10px', fontSize: '12px', borderRadius: '6px', border: '1px solid #d6dde6', background: '#fff', cursor: 'pointer' });

    const goInput = document.createElement('input');
    goInput.type = 'number';
    goInput.placeholder = 'Line #';
    Object.assign(goInput.style, { padding: '6px 8px', fontSize: '12px', width: '80px', borderRadius: '6px', border: '1px solid #d6dde6', boxSizing: 'border-box' });

    const goBtn = document.createElement('button');
    goBtn.textContent = 'Go';
    Object.assign(goBtn.style, { padding: '6px 10px', fontSize: '12px', borderRadius: '6px', border: '1px solid #bcd4ff', background: '#eef6ff', cursor: 'pointer' });

    toolbar.appendChild(copyBtn);
    toolbar.appendChild(goInput);
    toolbar.appendChild(goBtn);

    // container for Monaco editor
    const monacoContainer = document.createElement('div');
    const monacoId = 'sf-monaco-' + Date.now();
    monacoContainer.id = monacoId;
    Object.assign(monacoContainer.style, {
      width: '100%',
      height: '50vh', // 2/4 of viewport height as requested
      border: '1px solid #e6eef7',
      borderRadius: '6px',
      overflow: 'hidden',
      boxSizing: 'border-box',
      background: '#fff'
    });

    const bottomRow = document.createElement('div');
    Object.assign(bottomRow.style, { display: 'flex', gap: '8px', justifyContent: 'flex-end' });

    const closeBtn = document.createElement('button');
    closeBtn.textContent = 'Close';
    Object.assign(closeBtn.style, { padding: '8px 12px', borderRadius: '6px', border: '1px solid #d6dde6', background: '#fff', cursor: 'pointer' });

    const saveBtn = document.createElement('button');
    saveBtn.textContent = 'Save';
    Object.assign(saveBtn.style, { padding: '8px 12px', borderRadius: '6px', border: '1px solid #bcd4ff', background: '#eef6ff', cursor: 'pointer', display: 'none' });

    bottomRow.appendChild(closeBtn);
    bottomRow.appendChild(saveBtn);

    editorLeft.appendChild(header);
    editorLeft.appendChild(toolbar);
    editorLeft.appendChild(monacoContainer);
    editorLeft.appendChild(bottomRow);

    // right: matches / navigation
    const editorRight = document.createElement('div');
    Object.assign(editorRight.style, { width: '320px', minWidth: '260px', display: 'flex', flexDirection: 'column', gap: '8px', overflow: 'auto' });

    const rightTitle = document.createElement('div');
    rightTitle.textContent = 'Matches';
    Object.assign(rightTitle.style, { fontWeight: '600' });

    const matchesList = document.createElement('div');
    Object.assign(matchesList.style, { display: 'flex', flexDirection: 'column', gap: '6px', padding: '6px', border: '1px solid #eee', borderRadius: '6px', background: '#fff', maxHeight: '86vh', overflow: 'auto' });

    const previewStatus = document.createElement('div');
    Object.assign(previewStatus.style, { fontSize: '13px', color: '#333' });

    editorRight.appendChild(rightTitle);
    editorRight.appendChild(previewStatus);
    editorRight.appendChild(matchesList);

    previewCard.appendChild(editorLeft);
    previewCard.appendChild(editorRight);
    previewOverlay.appendChild(previewCard);
    document.body.appendChild(previewOverlay);

    // read file content
    let originalContent = '';
    try {
      originalContent = await fs.readFile(fileObj.fullPath, 'utf8');
    } catch (err) {
      originalContent = '(failed to read file)';
      previewStatus.textContent = 'Failed to read file content.';
    }

    // determine search query for highlighting only if current mode is content
    const highlightQuery = currentMode === 'content' ? ((searchInput && searchInput.value) || '') : '';

    // ensure highlight CSS exists (once)
    const STYLE_ID = 'sf-monaco-deco-style';
    if (!document.getElementById(STYLE_ID)) {
      const style = document.createElement('style');
      style.id = STYLE_ID;
      style.type = 'text/css';
      // decoration classes applied by Monaco (className)
      style.innerHTML = `
        .monaco-editor .sf-line-highlight {
          background: rgba(255, 243, 128, 0.45) !important;
        }
      `;
      document.head.appendChild(style);
    }

    // create Monaco editor in container
    let monaco, editor;
    try {
      const lang = monacoManager.detectLanguageFromPath(fileObj.relPath || fileObj.fullPath);
      const res = await monacoManager.initEditor(monacoId, {
        language: lang,
        theme: 'vs-light',
        value: '', // model will be set by setModelForPath
        automaticLayout: true,
        minimap: { enabled: false }
      });
      monaco = res.monaco;
      editor = res.editor;

      // Use a model backed by file path (so multiple tabs/models re-use) without changing the primary editor
      const model = monacoManager.createModelForPath(fileObj.fullPath, originalContent, lang);
      editor.setModel(model);
    } catch (err) {
      console.error('Failed to initialize Monaco for preview:', err);
      previewStatus.textContent = 'Failed to initialize editor.';
      return;
    }

    // convenience refs
    let model = editor.getModel();

    // compute matches & create decorations
    let decorationIds = [];
    function applyLineDecorations(matchLines) {
      if (!monaco || !editor || !editor.getModel()) return;
      const m = editor.getModel();
      // Build decorations using monaco.Range so they are model-relative
      const decs = matchLines.map(item => {
        const lineNumber = item.lineNumber;
        const startCol = 1;
        let endCol = 1;
        try {
          endCol = m.getLineMaxColumn(lineNumber);
        } catch (e) {
          endCol = 1;
        }
        return {
          range: new monaco.Range(lineNumber, startCol, lineNumber, endCol),
          options: {
            isWholeLine: true,
            className: 'sf-line-highlight'
          }
        };
      });

      try {
        decorationIds = editor.deltaDecorations(decorationIds || [], decs);
      } catch (e) {
        // If model changed under us, clear and reapply next time
        decorationIds = [];
      }
    }

    // initial matches based on current model value
    function recomputeMatchesAndDecorate() {
      try {
        model = editor.getModel();
        if (!model) return [];
        const text = model.getValue();
        const matches = computeMatchesForText(text, highlightQuery);
        applyLineDecorations(matches);
        renderMatchesList(matches);
        return matches;
      } catch (err) {
        console.error('recomputeMatchesAndDecorate failed', err);
        return [];
      }
    }

    let matchLines = recomputeMatchesAndDecorate();

    // Build matches list UI
    function renderMatchesList(matchLinesArr) {
      matchesList.innerHTML = '';
      if (!matchLinesArr || !matchLinesArr.length) {
        const none = document.createElement('div');
        none.textContent = 'No matches found.';
        Object.assign(none.style, { color: '#666', padding: '8px', fontSize: '13px' });
        matchesList.appendChild(none);
        return;
      }
      for (const mItem of matchLinesArr) {
        const row = document.createElement('div');
        Object.assign(row.style, { display: 'flex', alignItems: 'center', gap: '8px', padding: '6px', borderRadius: '6px', background: '#fafafa', cursor: 'pointer', fontSize: '13px', color: '#111' });

        const txt = document.createElement('div');
        txt.textContent = `Line ${mItem.lineNumber}: ${mItem.snippet.length > 200 ? mItem.snippet.slice(0, 200) + '…' : mItem.snippet}`;
        Object.assign(txt.style, { whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' });

        row.appendChild(txt);

        row.addEventListener('click', (e) => {
          // center the line in the editor and place caret at first occurrence using a range reveal
          try {
            const firstOcc = (mItem.occurrences && mItem.occurrences[0]) ? mItem.occurrences[0] : null;
            const startCol = firstOcc ? (firstOcc.start + 1) : 1;
            const endCol = firstOcc ? (firstOcc.end + 1) : (editor.getModel().getLineMaxColumn(mItem.lineNumber));
            const range = new monaco.Range(mItem.lineNumber, startCol, mItem.lineNumber, endCol);

            // reveal and select the exact range - revealRangeInCenter is reliable for centering vertically & horizontally
            editor.revealRangeInCenter(range);
            editor.setSelection(range);
            editor.focus();
          } catch (err) {
            console.error('Failed to navigate to match:', err);
          }
        });

        matchesList.appendChild(row);
      }
    }

    renderMatchesList(matchLines);

    // track dirty state
    let isDirty = false;
    function setDirty(d) {
      isDirty = !!d;
      saveBtn.style.display = isDirty ? '' : 'none';
    }

    // when user edits, update dirty flag and recompute matches live
    const disposeContentChange = editor.onDidChangeModelContent(() => {
      setDirty(true);
      try {
        // recompute from current model (user edits)
        matchLines = recomputeMatchesAndDecorate();
      } catch (e) { /* ignore */ }
    });

    // also listen for model changes (e.g. some external refresh/reload) and recompute decorations
    let disposeModelChange = null;
    try {
      disposeModelChange = editor.onDidChangeModel(() => {
        // model identity changed; reset decoration ids and recompute matches from new model
        try {
          decorationIds = [];
          model = editor.getModel();
          matchLines = recomputeMatchesAndDecorate();
        } catch (e) { /* ignore */ }
      });
    } catch (e) {
      // ignore if onDidChangeModel not available
    }

    // Save logic - write to disk and refresh decorations using current model content
    saveBtn.addEventListener('click', async () => {
      try {
        const currentText = editor.getModel().getValue();
        await fs.writeFile(fileObj.fullPath, currentText, 'utf8');

        // After saving, recompute matches and decorations from the editor model's content.
        // Use a short timeout to allow any external file-watchers to update the model first.
        setTimeout(() => {
          try {
            matchLines = recomputeMatchesAndDecorate();
            setDirty(false);
            previewStatus.textContent = 'Saved.';
            statusDiv.textContent = `Saved ${fileObj.relPath}`;
          } catch (e) {
            console.error('Error after save while recomputing matches:', e);
          }
        }, 80);
      } catch (err) {
        console.error('Failed to save file:', err);
        previewStatus.textContent = `Failed to save: ${err.message || String(err)}`;
      }
    });

    // copy all handler
    copyBtn.addEventListener('click', async () => {
      try {
        await clipboard.writeText(editor.getModel().getValue() || '');
        previewStatus.textContent = 'Copied file content to clipboard.';
      } catch (err) {
        previewStatus.textContent = 'Failed to copy.';
      }
    });

    // Go to line handler (centers)
    goBtn.addEventListener('click', () => {
      const v = parseInt(goInput.value, 10);
      if (!v || v <= 0) {
        previewStatus.textContent = 'Enter a valid line number.';
        return;
      }
      const totalLines = editor.getModel().getLineCount();
      if (v > totalLines) {
        previewStatus.textContent = `File has only ${totalLines} lines.`;
        return;
      }
      try {
        // reveal line in center and move caret to the start of the line
        const lineMaxCol = editor.getModel().getLineMaxColumn(v);
        const range = new monaco.Range(v, 1, v, Math.max(1, Math.min(2, lineMaxCol)));
        editor.revealRangeInCenter(range);
        editor.setPosition({ lineNumber: v, column: 1 });
        editor.focus();
      } catch (err) {
        console.error('Go to line failed:', err);
      }
    });

    // keyboard: Ctrl/Cmd+S -> save, Esc -> close
    function keyHandler(e) {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 's') {
        e.preventDefault();
        if (isDirty) saveBtn.click();
      } else if (e.key === 'Escape') {
        e.preventDefault();
        closeBtn.click();
      }
    }
    previewOverlay.tabIndex = 0;
    previewOverlay.addEventListener('keydown', keyHandler);
    previewOverlay.focus();

    // Close button behavior with unsaved changes confirmation
    closeBtn.addEventListener('click', () => {
      if (isDirty) {
        const confOverlay = document.createElement('div');
        Object.assign(confOverlay.style, {
          position: 'fixed',
          top: '0',
          left: '0',
          width: '100vw',
          height: '100vh',
          background: 'rgba(0,0,0,0.45)',
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          zIndex: '1500'
        });
        const confCard = document.createElement('div');
        Object.assign(confCard.style, { background: '#fff', padding: '12px', borderRadius: '8px', width: 'min(90vw,420px)' });

        const txt = document.createElement('div');
        txt.textContent = 'You have unsaved changes. Save before closing?';
        Object.assign(txt.style, { marginBottom: '10px' });

        const row = document.createElement('div');
        Object.assign(row.style, { display: 'flex', gap: '8px', justifyContent: 'flex-end' });

        const btnCancel = document.createElement('button');
        btnCancel.textContent = 'Cancel';
        Object.assign(btnCancel.style, { padding: '8px 12px', borderRadius: '6px', border: '1px solid #d6dde6', background: '#fff', cursor: 'pointer' });

        const btnDiscard = document.createElement('button');
        btnDiscard.textContent = 'Discard';
        Object.assign(btnDiscard.style, { padding: '8px 12px', borderRadius: '6px', border: '1px solid #d6dde6', background: '#fff', cursor: 'pointer' });

        const btnSaveNow = document.createElement('button');
        btnSaveNow.textContent = 'Save';
        Object.assign(btnSaveNow.style, { padding: '8px 12px', borderRadius: '6px', border: '1px solid #bcd4ff', background: '#eef6ff', cursor: 'pointer' });

        row.appendChild(btnCancel);
        row.appendChild(btnDiscard);
        row.appendChild(btnSaveNow);
        confCard.appendChild(txt);
        confCard.appendChild(row);
        confOverlay.appendChild(confCard);
        document.body.appendChild(confOverlay);

        btnCancel.addEventListener('click', () => {
          if (confOverlay.parentNode) document.body.removeChild(confOverlay);
        });
        btnDiscard.addEventListener('click', () => {
          if (confOverlay.parentNode) document.body.removeChild(confOverlay);
          // cleanup editor and overlay
          try { if (editor) editor.dispose(); } catch (_) {}
          try { if (previewOverlay.parentNode) document.body.removeChild(previewOverlay); } catch (_) {}
        });
        btnSaveNow.addEventListener('click', async () => {
          try {
            const currentText = editor.getModel().getValue();
            await fs.writeFile(fileObj.fullPath, currentText, 'utf8');
            setDirty(false);
            previewStatus.textContent = 'Saved.';
            statusDiv.textContent = `Saved ${fileObj.relPath}`;
          } catch (err) {
            previewStatus.textContent = `Failed to save: ${err.message || String(err)}`;
          } finally {
            if (confOverlay.parentNode) document.body.removeChild(confOverlay);
            if (!isDirty) {
              try { if (editor) editor.dispose(); } catch (_) {}
              if (previewOverlay.parentNode) document.body.removeChild(previewOverlay);
            }
          }
        });
      } else {
        // normal close
        try { if (editor) editor.dispose(); } catch (_) {}
        if (previewOverlay.parentNode) document.body.removeChild(previewOverlay);
      }
    });

    // Clean up when overlay removed
    const previewObs = new MutationObserver(() => {
      if (!document.body.contains(previewOverlay)) {
        // remove decorations if any
        try {
          if (decorationIds && editor && !editor._disposed) {
            editor.deltaDecorations(decorationIds, []);
            decorationIds = [];
          }
        } catch (_) {}
        // dispose change listeners
        try { disposeContentChange && disposeContentChange.dispose && disposeContentChange.dispose(); } catch (_) {}
        try { disposeModelChange && disposeModelChange.dispose && disposeModelChange.dispose(); } catch (_) {}
        previewObs.disconnect();
      }
    });
    previewObs.observe(document.body, { childList: true, subtree: true });

    // initial status
    previewStatus.textContent = 'Loaded.';

    // initial reveal sync (no-op but ensures layout settled)
    setTimeout(() => {
      try { editor.layout(); } catch (_) {}
    }, 30);
  }

  // ------------------------

  // Click handlers
  searchBtn.addEventListener('click', async (e) => {
    e.stopPropagation();
    await performSearch();
  });

  searchInput.addEventListener('keydown', async (e) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      await performSearch();
    }
  });

  // Clicking outside card removes overlay (simple behavior)
  let pointerDownOnOverlay = false;
  let pointerUpOnOverlay = false;
  overlay.addEventListener('pointerdown', (e) => {
    pointerDownOnOverlay = (e.target === overlay);
    pointerUpOnOverlay = false;
  });
  overlay.addEventListener('pointerup', (e) => {
    pointerUpOnOverlay = (e.target === overlay);
  });
  overlay.addEventListener('mousedown', (e) => { pointerDownOnOverlay = (e.target === overlay); pointerUpOnOverlay = false; });
  overlay.addEventListener('mouseup', (e) => { pointerUpOnOverlay = (e.target === overlay); });

  overlay.addEventListener('click', (e) => {
    if (e.target === overlay && pointerDownOnOverlay && pointerUpOnOverlay) {
      if (document.body.contains(overlay)) document.body.removeChild(overlay);
    }
    pointerDownOnOverlay = false;
    pointerUpOnOverlay = false;
  });

  // Prevent clicks inside card from closing
  card.addEventListener('click', (e) => e.stopPropagation());

  // Cleanup when overlay is removed externally
  const observer = new MutationObserver(() => {
    if (!document.body.contains(overlay)) {
      observer.disconnect();
    }
  });
  observer.observe(document.body, { childList: true, subtree: true });

  // initial
  updateTargetInfo();
}

module.exports = { searchFile };