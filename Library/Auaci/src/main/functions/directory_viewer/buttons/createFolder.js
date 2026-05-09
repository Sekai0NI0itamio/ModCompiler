const fs = require('fs').promises;
const path = require('path');
const { scanDirectoryTree } = require('../scan');
const { renderDirectoryTree } = require('../render'); // removed addNewItem usage
const { createPopupWithTolerance } = require('../popupUtils');

const algorithmic = require('./cff/algotihmic');
const gptParser = require('./cff/gpt-based');

async function createFolder(projectRoot, relativePath = '') {
  // overlay backdrop
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

  // card container
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
    boxSizing: 'border-box'
  });

  // LEFT column
  const left = document.createElement('div');
  Object.assign(left.style, {
    display: 'flex',
    flexDirection: 'column',
    gap: '10px',
    flex: '1',
    minWidth: '360px'
  });

  const input = document.createElement('input');
  input.type = 'text';
  input.placeholder = 'Enter folder path (e.g., src/utils or myfolder)';
  Object.assign(input.style, {
    padding: '10px',
    flex: '1',
    fontSize: '14px',
    borderRadius: '6px',
    border: '1px solid #d6dde6',
    background: '#fff',
    outline: 'none',
    boxSizing: 'border-box'
  });

  // NEW: target path display (one-line info about where the folder(s) will be created)
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
  // helper to compute displayed target path
  function updateTargetInfo() {
    const val = input.value.trim();
    // For tree creation, base path may include inputSegments; show base when appropriate.
    const inputSegments = val ? val.split(/[\\/]+/).filter(Boolean) : [];
    const baseSub = (createAtInputPath && inputSegments.length) ? path.join(...inputSegments) : '';
    const basePath = path.join(projectRoot, relativePath, baseSub);
    if (!val) {
      // show folder where creations will be made if no explicit input provided
      targetInfo.style.display = 'block';
      targetInfo.textContent = `The following folder directory will be created in this folder: ${path.join(projectRoot, relativePath)}`;
    } else {
      targetInfo.style.display = 'block';
      targetInfo.textContent = `The following folder directory will be created in this folder: ${basePath}`;
    }
  }

  // Info row and expand
  const infoRow = document.createElement('div');
  Object.assign(infoRow.style, {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: '8px'
  });

  const createProjectText = document.createElement('div');
  createProjectText.textContent = 'Create project structure';
  Object.assign(createProjectText.style, {
    fontSize: '13px',
    color: '#333'
  });

  const expandBtn = document.createElement('button');
  let optionsExpanded = false;
  function updateExpandBtn() {
    expandBtn.textContent = optionsExpanded ? 'Hide options ▲' : 'Show options ▼';
    Object.assign(expandBtn.style, {
      padding: '6px 10px',
      fontSize: '13px',
      borderRadius: '6px',
      border: '1px solid #d6dde6',
      background: optionsExpanded ? '#eef6ff' : '#f7f9fb',
      color: '#000',
      cursor: 'pointer'
    });
  }
  expandBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    optionsExpanded = !optionsExpanded;
    updateExpandBtn();
    optionsDiv.style.display = optionsExpanded ? 'flex' : 'none';
    updatePreviewBtnVisibility();
    if (!optionsExpanded) {
      right.style.display = 'none';
    } else if (previewContent.textContent) {
      right.style.display = 'block';
    }
  });
  updateExpandBtn();

  infoRow.appendChild(createProjectText);
  infoRow.appendChild(expandBtn);

  // suggestions under input
  const suggestionsContainer = document.createElement('div');
  Object.assign(suggestionsContainer.style, {
    maxHeight: '180px',
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

  // options container (hidden by default)
  const optionsDiv = document.createElement('div');
  Object.assign(optionsDiv.style, {
    display: 'none',
    flexDirection: 'column',
    gap: '10px',
    width: '100%'
  });

  // create toggle (label + small on/off)
  const createToggleRow = document.createElement('div');
  Object.assign(createToggleRow.style, {
    display: 'flex',
    alignItems: 'center',
    gap: '8px'
  });
  const createToggleLabel = document.createElement('div');
  createToggleLabel.textContent = 'Create project structorial using file path';
  Object.assign(createToggleLabel.style, {
    fontSize: '13px',
    color: '#333'
  });
  const projectToggleBtn = document.createElement('button');
  let createAtInputPath = true;
  function updateProjectToggle() {
    projectToggleBtn.textContent = createAtInputPath ? 'on' : 'off';
    Object.assign(projectToggleBtn.style, {
      padding: '6px 10px',
      fontSize: '13px',
      borderRadius: '6px',
      border: '1px solid #d6dde6',
      background: createAtInputPath ? '#eef6ff' : '#f7f9fb',
      color: '#000',
      cursor: 'pointer',
      minWidth: '56px'
    });
  }
  projectToggleBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    createAtInputPath = !createAtInputPath;
    updateProjectToggle();
    input.focus();
    updateTargetInfo();
  });
  updateProjectToggle();
  createToggleRow.appendChild(createToggleLabel);
  createToggleRow.appendChild(projectToggleBtn);

  // parser selector row
  const parserRow = document.createElement('div');
  Object.assign(parserRow.style, {
    display: 'flex',
    gap: '8px',
    alignItems: 'center'
  });
  const parserLabel = document.createElement('div');
  parserLabel.textContent = 'Project parse:';
  Object.assign(parserLabel.style, { fontSize: '13px', color: '#333', marginRight: '8px' });

  const algorithmicBtn = document.createElement('button');
  const gptBtn = document.createElement('button');
  let selectedParser = 'algorithmic';
  function updateParserButtons() {
    algorithmicBtn.textContent = 'algorithmic';
    gptBtn.textContent = 'gpt';
    Object.assign(algorithmicBtn.style, {
      padding: '6px 10px',
      borderRadius: '6px',
      border: selectedParser === 'algorithmic' ? '1px solid #bcd4ff' : '1px solid #d6dde6',
      background: selectedParser === 'algorithmic' ? '#eef6ff' : '#f7f9fb',
      color: '#000',
      cursor: 'pointer'
    });
    Object.assign(gptBtn.style, {
      padding: '6px 10px',
      borderRadius: '6px',
      border: selectedParser === 'gpt' ? '1px solid #bcd4ff' : '1px solid #d6dde6',
      background: selectedParser === 'gpt' ? '#eef6ff' : '#f7f9fb',
      color: '#000',
      cursor: 'pointer'
    });
  }
  algorithmicBtn.addEventListener('click', (e) => { e.stopPropagation(); selectedParser = 'algorithmic'; updateParserButtons(); input.focus(); });
  gptBtn.addEventListener('click', (e) => { e.stopPropagation(); selectedParser = 'gpt'; updateParserButtons(); input.focus(); });
  updateParserButtons();
  parserRow.appendChild(parserLabel);
  parserRow.appendChild(algorithmicBtn);
  parserRow.appendChild(gptBtn);

  // tree input inside options
  const label = document.createElement('div');
  label.textContent = 'Paste tree structure below:';
  Object.assign(label.style, { fontSize: '13px', color: '#333' });

  const treeInput = document.createElement('textarea');
  Object.assign(treeInput, {
    placeholder: 'Paste project tree (e.g.\n├── folder\n│   └── file.txt)'
  });
  Object.assign(treeInput.style, {
    padding: '10px',
    width: '100%',
    height: '160px',
    fontSize: '13px',
    borderRadius: '6px',
    border: '1px solid #d6dde6',
    outline: 'none',
    resize: 'vertical',
    fontFamily: 'monospace',
    background: '#fff',
    boxSizing: 'border-box'
  });

  // preview button
  const previewBtn = document.createElement('button');
  Object.assign(previewBtn.style, {
    padding: '6px 10px',
    fontSize: '13px',
    borderRadius: '6px',
    border: '1px solid #bcd4ff',
    background: '#eef6ff',
    color: '#000',
    cursor: 'pointer'
  });
  previewBtn.textContent = 'Preview';
  previewBtn.style.display = 'none';

  function updatePreviewBtnVisibility() {
    const show = optionsExpanded && treeInput.value.trim() !== '';
    previewBtn.style.display = show ? 'inline-block' : 'none';
  }

  // status area
  const statusDiv = document.createElement('div');
  Object.assign(statusDiv.style, { minHeight: '18px', color: '#b02a2a', fontSize: '13px' });

  // RIGHT: preview panel
  const right = document.createElement('div');
  Object.assign(right.style, {
    width: '480px',
    minWidth: '320px',
    height: '360px',
    border: '1px solid #e0e6ee',
    borderRadius: '6px',
    overflow: 'auto',
    background: '#fafafa',
    padding: '10px',
    boxSizing: 'border-box',
    display: 'none',
    whiteSpace: 'pre',
    fontFamily: 'monospace',
    fontSize: '13px',
    position: 'relative'
  });

  const previewTitle = document.createElement('div');
  previewTitle.textContent = 'Preview (relative paths)';
  Object.assign(previewTitle.style, { fontWeight: '600', marginBottom: '6px' });

  const closePreviewBtn = document.createElement('button');
  closePreviewBtn.innerHTML = '✕';
  Object.assign(closePreviewBtn.style, {
    position: 'absolute',
    top: '8px',
    right: '8px',
    padding: '4px 8px',
    fontSize: '13px',
    borderRadius: '6px',
    border: '1px solid #d6dde6',
    background: '#f7f9fb',
    color: '#000',
    cursor: 'pointer',
    lineHeight: '1'
  });
  closePreviewBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    // abort any preview GPT request
    if (processingAbortController) {
      try { processingAbortController.abort(); } catch (err) {}
      processingAbortController = null;
    }
    try { gptParser.abortCurrent(); } catch (e) {}
    right.style.display = 'none';
    previewContent.textContent = '';
    previewControls.style.display = 'none';
    rightFooter.style.display = 'none';
    isProcessing = false;
    blockOverlayClose = false;
  });

  const previewContent = document.createElement('div');
  Object.assign(previewContent.style, { whiteSpace: 'pre', fontFamily: 'monospace', fontSize: '13px', marginTop: '6px' });

  const previewControls = document.createElement('div');
  Object.assign(previewControls.style, { display: 'none', marginTop: '4px', gap: '8px' });
  const loadingText = document.createElement('div');
  loadingText.textContent = 'Processing...';
  Object.assign(loadingText.style, { fontSize: '13px', color: '#333' });
  previewControls.appendChild(loadingText);

  // right footer (abort moved below preview) — but we'll not show abort here per new requirement
  const rightFooter = document.createElement('div');
  Object.assign(rightFooter.style, { display: 'none', marginTop: '8px', gap: '8px', justifyContent: 'flex-start' });

  right.appendChild(previewTitle);
  right.appendChild(closePreviewBtn);
  right.appendChild(previewContent);
  right.appendChild(previewControls);
  right.appendChild(rightFooter);

  // GPT processing modal (centered small modal that appears when GPT call is ongoing and was initiated)
  const gptModal = document.createElement('div');
  Object.assign(gptModal.style, {
    position: 'fixed',
    top: '50%',
    left: '50%',
    transform: 'translate(-50%, -50%)',
    minWidth: '320px',
    padding: '12px',
    borderRadius: '8px',
    background: '#fff',
    boxShadow: '0 8px 28px rgba(0,0,0,0.18)',
    zIndex: '1100',
    display: 'none',
    flexDirection: 'column',
    gap: '10px',
    boxSizing: 'border-box'
  });

  const gptModalText = document.createElement('div');
  gptModalText.textContent = 'GPT is processing...';
  Object.assign(gptModalText.style, { fontSize: '14px', color: '#111' });

  const gptAbortBtn = document.createElement('button');
  gptAbortBtn.textContent = 'Abort';
  Object.assign(gptAbortBtn.style, {
    padding: '8px 12px',
    fontSize: '13px',
    borderRadius: '6px',
    border: '1px solid #f1c0c0',
    background: '#fff4f4',
    color: '#000',
    cursor: 'pointer',
    alignSelf: 'flex-start'
  });

  gptModal.appendChild(gptModalText);
  gptModal.appendChild(gptAbortBtn);

  // assemble left
  left.appendChild(input);
  left.appendChild(targetInfo); // <-- add target info here
  left.appendChild(infoRow);
  left.appendChild(suggestionsContainer);
  optionsDiv.appendChild(createToggleRow);
  optionsDiv.appendChild(parserRow);
  optionsDiv.appendChild(label);
  optionsDiv.appendChild(treeInput);
  optionsDiv.appendChild(previewBtn);
  left.appendChild(optionsDiv);
  left.appendChild(statusDiv);

  card.appendChild(left);
  card.appendChild(right);
  overlay.appendChild(card);
  document.body.appendChild(overlay);
  document.body.appendChild(gptModal);
  input.focus();

  // autocomplete state
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
      el.className = 'cf-folder-suggestion';
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
      el.addEventListener('pointerdown', (e) => { e.preventDefault(); e.stopPropagation(); acceptSuggestion(idx); });
      el.addEventListener('mousedown', (e) => { if (e.button === 0) { e.preventDefault(); e.stopPropagation(); acceptSuggestion(idx); } });
      el.addEventListener('mouseover', () => setSelectedIndex(idx));
      el.addEventListener('mouseout', () => setSelectedIndex(-1));
      suggestionsContainer.appendChild(el);
    });
    suggestionsContainer.style.display = 'flex';
  }

  function setSelectedIndex(idx) {
    const items = suggestionsContainer.querySelectorAll('.cf-folder-suggestion');
    selectedIndex = idx;
    items.forEach((it, i) => {
      if (i === idx) Object.assign(it.style, { background: '#eef6ff' });
      else Object.assign(it.style, { background: '#fff' });
    });
  }

  function acceptSuggestion(idx) {
    if (!currentSuggestions || !currentSuggestions[idx]) return;
    const item = currentSuggestions[idx];
    const val = input.value;
    const lastSlash = Math.max(val.lastIndexOf('/'), val.lastIndexOf('\\'));
    const sep = lastSlash >= 0 && val[lastSlash] === '\\' ? '\\' : '/';
    const left = lastSlash >= 0 ? val.slice(0, lastSlash) + sep : '';
    input.value = left + item.name + (item.isDir ? sep : '');
    input.focus();
    clearSuggestions();
    updateTargetInfo();
  }

  // split input
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

  // Attempt autocomplete. explicit === true means user pressed Tab (explicit request to fill).
  // If explicit is false we only show suggestions (even for a single match) and do not auto-insert.
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

    entries = entries.filter(e => e.isDir);
    const fragLower = fragment.toLowerCase();
    let matches = entries.filter(e => e.name.toLowerCase().startsWith(fragLower));
    if (!matches.length && fragment === '') matches = entries;
    if (!matches.length) { clearSuggestions(); return; }

    // Only auto-fill when the user explicitly requested it (Tab). Otherwise just show suggestions.
    if (matches.length === 1 && explicit) {
      const single = matches[0];
      const left = dirSegments.length ? dirSegments.join(sep) + sep : '';
      input.value = left + single.name + sep;
      input.focus();
      clearSuggestions();
      updateTargetInfo();
      return;
    }

    // Show suggestions (including when there's exactly 1 match but user did not press Tab)
    currentSuggestions = matches;
    renderSuggestions(matches, sep);
    setSelectedIndex(-1);
  }

  // preview logic
  let lastPreviewPaths = null;
  let isProcessing = false;
  let processingAbortController = null;
  let blockOverlayClose = false;

  async function doPreview() {
    statusDiv.textContent = '';
    previewContent.textContent = '';
    right.style.display = 'block';
    previewControls.style.display = 'none';
    rightFooter.style.display = 'none';
    previewContent.textContent = 'Parsing...';

    const txt = treeInput.value.trim();
    if (!txt) {
      previewContent.textContent = '';
      return;
    }

    try {
      if (selectedParser === 'algorithmic') {
        const relPaths = algorithmic.parseTree(txt);
        lastPreviewPaths = relPaths.slice();
        previewContent.textContent = relPaths.join('\n') || '(no paths)';
        previewControls.style.display = 'none';
        rightFooter.style.display = 'none';
        return;
      }

      // GPT path: check cache; if cached, show cached; if not cached, show GPT modal and run
      const cached = (function () {
        // gptParser.parseWithCache returns fromCache flag when called; to inspect cache without calling remote, call parseWithCache and it will return fromCache true if cached.
        return null;
      })();

      // We must call parseWithCache — if cached, it will return quickly; if not cached, it will start a network request.
      isProcessing = true;
      processingAbortController = new AbortController();

      // Show GPT modal (central) during processing
      gptModalText.textContent = 'GPT is processing (preview)...';
      gptModal.style.display = 'flex';
      previewControls.style.display = 'block';
      loadingText.textContent = 'Parsing with GPT...';
      rightFooter.style.display = 'flex';

      try {
        const res = await gptParser.parseWithCache(txt, { signal: processingAbortController.signal, timeout: 120000 });
        lastPreviewPaths = res.paths.slice();
        previewContent.textContent = lastPreviewPaths.join('\n') || '(no paths returned)';
        previewControls.style.display = 'none';
        rightFooter.style.display = 'none';
        gptModal.style.display = 'none';
      } catch (err) {
        if (err && err.message && err.message.includes('aborted')) {
          previewContent.textContent = '(GPT request aborted)';
          gptModal.style.display = 'none';
        } else {
          previewContent.textContent = `Error parsing with GPT: ${err.message || String(err)}`;
          gptModal.style.display = 'none';
        }
        previewControls.style.display = 'none';
        rightFooter.style.display = 'none';
      } finally {
        isProcessing = false;
        processingAbortController = null;
      }
    } catch (err) {
      previewContent.textContent = `Preview error: ${err.message || String(err)}`;
      previewControls.style.display = 'none';
      rightFooter.style.display = 'none';
      processingAbortController = null;
      isProcessing = false;
      gptModal.style.display = 'none';
    }
  }

  previewBtn.addEventListener('click', async (e) => {
    e.stopPropagation();
    await doPreview();
  });

  // GPT modal abort behavior
  gptAbortBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    if (processingAbortController) {
      try { processingAbortController.abort(); } catch (err) {}
      processingAbortController = null;
    }
    try { gptParser.abortCurrent(); } catch (e) {}
    gptModal.style.display = 'none';
    previewControls.style.display = 'none';
    rightFooter.style.display = 'none';
    isProcessing = false;
    blockOverlayClose = false;
    statusDiv.textContent = 'GPT request aborted.';
  });

  // Create / Confirm (triggered by Enter)
  async function handleConfirm() {
    statusDiv.textContent = '';

    const folderName = input.value.trim();
    const treeText = treeInput.value.trim();

    try {
      if (folderName && treeText) {
        alert('Please use either the single folder name OR the tree input, not both.');
        return;
      }

      // Single folder creation
      if (folderName && !treeText) {
        const segments = folderName.split(/[\\/]+/).filter(Boolean);
        const fullPath = path.join(projectRoot, relativePath, ...segments);
        await fs.mkdir(fullPath, { recursive: true });
        const tree = await scanDirectoryTree(projectRoot);
        document.getElementById('directory-viewer-content').innerHTML = '';
        renderDirectoryTree(tree, [fullPath]);
        if (document.body.contains(overlay)) document.body.removeChild(overlay);
        return;
      }

      if (!treeText) {
        alert('Please enter a folder name or paste a tree structure.');
        return;
      }

      // Tree creation path
      const inputVal = input.value.trim();
      const inputSegments = inputVal ? inputVal.split(/[\\/]+/).filter(Boolean) : [];
      const baseSub = (createAtInputPath && inputSegments.length) ? path.join(...inputSegments) : '';
      const basePath = path.join(projectRoot, relativePath, baseSub);

      if (selectedParser === 'algorithmic') {
        let relPaths;
        try {
          relPaths = algorithmic.parseTree(treeText);
        } catch (err) {
          statusDiv.textContent = `Algorithmic parsing failed: ${err.message || String(err)}`;
          return;
        }
        try {
          await algorithmic.createFromPaths(basePath, relPaths);
          const tree = await scanDirectoryTree(projectRoot);
          document.getElementById('directory-viewer-content').innerHTML = '';
          renderDirectoryTree(tree, []);
          if (document.body.contains(overlay)) document.body.removeChild(overlay);
          return;
        } catch (err) {
          statusDiv.textContent = `Creation failed: ${err.message || String(err)}`;
          return;
        }
      }

      // GPT path for creation:
      if (selectedParser === 'gpt') {
        // parseWithCache: if cached returns quickly; if not cached it will run. For both cases we will show the GPT modal if request is performed.
        let usedCached = false;

        // Try to get cached result without showing modal by calling parseWithCache and inspecting fromCache
        processingAbortController = new AbortController();
        let res;
        try {
          // Show modal only if the parseWithCache will actually perform network (we cannot know until it runs).
          // We'll show the modal immediately to indicate activity; parseWithCache will return quickly if cached.
          gptModalText.textContent = 'GPT is processing...';
          gptModal.style.display = 'flex';
          isProcessing = true;
          blockOverlayClose = true;

          res = await gptParser.parseWithCache(treeText, { signal: processingAbortController.signal, timeout: 120000 });
          usedCached = res.fromCache || false;
        } catch (err) {
          gptModal.style.display = 'none';
          isProcessing = false;
          blockOverlayClose = false;
          processingAbortController = null;
          if (err && err.message && err.message.includes('aborted')) {
            statusDiv.textContent = 'GPT request aborted.';
          } else {
            statusDiv.textContent = `GPT parsing failed: ${err.message || String(err)}`;
          }
          return;
        }

        // if we reached here we have res.paths
        const relPaths = (res && Array.isArray(res.paths)) ? res.paths.slice() : [];
        if (!relPaths.length) {
          statusDiv.textContent = 'GPT returned no paths to create.';
          gptModal.style.display = 'none';
          isProcessing = false;
          blockOverlayClose = false;
          processingAbortController = null;
          return;
        }

        // Create files/folders
        try {
          await algorithmic.createFromPaths(basePath, relPaths);
          const tree = await scanDirectoryTree(projectRoot);
          document.getElementById('directory-viewer-content').innerHTML = '';
          renderDirectoryTree(tree, []);
          if (document.body.contains(overlay)) document.body.removeChild(overlay);
        } catch (err) {
          statusDiv.textContent = `Creation failed: ${err.message || String(err)}`;
        } finally {
          gptModal.style.display = 'none';
          isProcessing = false;
          blockOverlayClose = false;
          processingAbortController = null;
        }
      }
    } catch (err) {
      console.error('Failed to create folder / tree:', err);
      statusDiv.textContent = 'An unexpected error occurred.';
      isProcessing = false;
      blockOverlayClose = false;
      processingAbortController = null;
    }
  }

  // input key handling
  input.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      handleConfirm();
    } else if (e.key === 'Tab') {
      e.preventDefault();
      // explicit request to autocomplete/fill
      attemptAutocomplete(true).catch(() => {});
    } else if ((e.key === 'ArrowDown' || e.key === 'ArrowUp') && suggestionsContainer.style.display !== 'none') {
      e.preventDefault();
      const items = suggestionsContainer.querySelectorAll('.cf-folder-suggestion');
      if (!items.length) return;
      if (e.key === 'ArrowDown') {
        const next = selectedIndex + 1 >= items.length ? 0 : selectedIndex + 1;
        setSelectedIndex(next);
      } else {
        const prev = selectedIndex - 1 < 0 ? items.length - 1 : selectedIndex - 1;
        setSelectedIndex(prev);
      }
    }
  });

  // tree input Enter without shift triggers create
  treeInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleConfirm();
    }
  });

  // debounce autocomplete
  let acTimer = null;
  input.addEventListener('input', () => {
    if (acTimer) clearTimeout(acTimer);
    // non-explicit: only show suggestions, don't auto-fill single matches
    acTimer = setTimeout(() => { attemptAutocomplete(false).catch(() => {}); }, 160);
    updateTargetInfo();
  });

  // hide suggestions on blur
  input.addEventListener('blur', () => { setTimeout(() => clearSuggestions(), 120); });

  // preview visibility update when tree changes
  treeInput.addEventListener('input', () => {
    updatePreviewBtnVisibility();
    updateTargetInfo();
  });

  // clicking preview runs doPreview
  previewBtn.addEventListener('click', async (e) => { e.stopPropagation(); await doPreview(); });

  // Set up tolerance zone for the createFolder popup
  const closeCreateFolderPopup = () => {
    if (!blockOverlayClose && !isProcessing) {
      if (document.body.contains(overlay)) document.body.removeChild(overlay);
      if (document.body.contains(gptModal)) document.body.removeChild(gptModal);
    }
  };
  
  const cleanupTolerance = createPopupWithTolerance(overlay, card, closeCreateFolderPopup, 30);

  // clean up if overlay removed externally
  const observer = new MutationObserver(() => {
    if (!document.body.contains(overlay)) {
      try { gptParser.abortCurrent(); } catch (e) {}
      if (cleanupTolerance) cleanupTolerance();
      observer.disconnect();
    }
  });
  observer.observe(document.body, { childList: true });

  // select all preview content on click
  previewContent.addEventListener('click', () => {
    try {
      const range = document.createRange();
      range.selectNodeContents(previewContent);
      const sel = window.getSelection();
      sel.removeAllRanges();
      sel.addRange(range);
    } catch (e) {}
  });

  // show initial info
  updateTargetInfo();
}

module.exports = { createFolder };