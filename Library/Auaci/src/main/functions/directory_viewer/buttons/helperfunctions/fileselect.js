// helperfunctions/fileselect.js
const fs = require('fs').promises;
const path = require('path');

/**
 * showFileSelector(options) -> Promise<result>
 *
 * options: {
 *   projectRoot: string (required),
 *   baseFolder: string (required) - absolute path of folder being displayed,
 *   displayBase: string (optional, default '.') - text label for root in tree display,
 *   files: Array of { fullPath, relPath, relNormalized, relProject } (optional, default []),
 *   excludedBinary: Array<string> (optional),
 *   excludedImage: Array<string> (optional),
 *   excludedUnreadable: Array<string> (optional),
 *   defaultFilename: string (optional, default 'projectinfo.txt'),
 *   defaultHideDeselected: boolean (optional, default true) <-- fileselect auto-enables hide-deselected
 *   defaultUseRelative: boolean (optional, default true),
 *   defaultIncludeTree: boolean (optional, default false),
 *   dialogTitle: string (optional) - overrides the header/title for the dialog
 *   mode: 'create' | 'select' (optional, default 'create') - 'select' hides filename UI and treats the dialog as selection-only
 *   createButtonText: string (optional) - override create button text in 'select' mode
 * }
 *
 * Returns a promise that resolves to:
 *  - { action: 'create', outFilename, includeTree, useRelative, selectedFullPaths, selectedRelPaths, selectedRelProjects }
 *  - { action: 'cancel' }
 *
 * The function renders a modal UI into document.body. It persists user deselections
 * under <projectRoot>/.auaci/deselected.json so subsequent calls will restore unchecked files.
 *
 * Behavior notes:
 *  - By default the UI hides deselected files (hideDeselected = true).
 *  - Folders containing 'node_modules' are excluded from the folder list entirely.
 *  - Common build/vendor/cache folders (dist, build, out, target, .git, vendor, venv, __pycache__, etc.)
 *    are auto-deselected (so they are hidden by default because hideDeselected is enabled).
 */
async function showFileSelector(options = {}) {
  const {
    projectRoot,
    baseFolder,
    displayBase = '.',
    files: filesInput = [],
    excludedBinary = [],
    excludedImage = [],
    excludedUnreadable = [],
    defaultFilename = 'projectinfo.txt',
    defaultHideDeselected = true, // enforce default true (can still be overridden by caller)
    defaultUseRelative = true,
    defaultIncludeTree = false,
    dialogTitle = 'Txtfilelize — select files to combine',
    mode = 'create', // 'create' (default) shows filename input and behaves like original; 'select' hides filename UI and returns selection
    createButtonText = null
  } = options;

  if (!projectRoot || !baseFolder) {
    throw new Error('projectRoot and baseFolder are required');
  }

  // Folders we always omit from the folder list (completely hidden)
  const ALWAYS_EXCLUDE_FOLDERS = new Set(['node_modules']);
  // Folders we auto-deselect (they appear unchecked and will be hidden initially because hideDeselected=true)
  const AUTO_DESELECT_FOLDERS = new Set([
    'dist', 'build', 'out', 'target', '.git', 'vendor', 'venv', '.venv',
    '__pycache__', '.cache', 'coverage', '.parcel-cache', 'node_modules'
  ]);

  function pathHasAnySegment(p, segSet) {
    if (!p || p === '.') return false;
    const parts = String(p).replace(/\\/g, '/').split('/').filter(Boolean);
    for (const part of parts) {
      if (segSet.has(part)) return true;
    }
    return false;
  }

  // selection cache paths
  const selectionCacheDir = path.join(projectRoot, '.auaci');
  const selectionCacheFile = path.join(selectionCacheDir, 'deselected.json');

  // persistence helpers/state
  let deselectedCache = new Set();
  let saveTimer = null;

  async function ensureAuaciDir() {
    try {
      await fs.mkdir(selectionCacheDir, { recursive: true });
    } catch (err) {
      // ignore
    }
  }

  async function loadDeselectedCache() {
    try {
      const txt = await fs.readFile(selectionCacheFile, 'utf8');
      const data = JSON.parse(txt);
      if (Array.isArray(data)) {
        deselectedCache = new Set(data.map(p => String(p).replace(/\\/g, '/')));
      } else if (data && Array.isArray(data.deselected)) {
        deselectedCache = new Set(data.deselected.map(p => String(p).replace(/\\/g, '/')));
      } else if (data && typeof data === 'object') {
        deselectedCache = new Set(Object.keys(data).filter(k => data[k]).map(p => String(p).replace(/\\/g, '/')));
      } else {
        deselectedCache = new Set();
      }
    } catch (err) {
      deselectedCache = new Set();
    }
  }

  async function flushSaveDeselected() {
    if (saveTimer) {
      clearTimeout(saveTimer);
      saveTimer = null;
    }
    try {
      await ensureAuaciDir();
      const arr = Array.from(deselectedCache).sort();
      await fs.writeFile(selectionCacheFile, JSON.stringify({ deselected: arr }, null, 2), 'utf8');
    } catch (err) {
      console.error('Failed to save deselected cache:', err);
    }
  }

  function scheduleSaveDeselected(delay = 300) {
    if (saveTimer) clearTimeout(saveTimer);
    saveTimer = setTimeout(() => {
      flushSaveDeselected().catch(err => console.error(err));
    }, delay);
  }

  // Make a shallow copy of files and normalize fields
  const files = (Array.isArray(filesInput) ? filesInput : []).map(f => ({
    fullPath: f.fullPath,
    relPath: f.relPath,
    relNormalized: String(f.relNormalized || f.relPath || '').replace(/\\/g, '/'),
    relProject: String(f.relProject || f.relNormalized || f.relPath || '').replace(/\\/g, '/')
  }));

  // sort files for deterministic display
  files.sort((a, b) => a.relPath.localeCompare(b.relPath, undefined, { numeric: true }));

  // build folder set (ancestors of discovered files), omit ALWAYS_EXCLUDE_FOLDERS entirely
  const foldersSet = new Set();
  for (const f of files) {
    const rel = f.relNormalized;
    // skip building folders for always-excluded paths (like node_modules)
    if (pathHasAnySegment(rel, ALWAYS_EXCLUDE_FOLDERS)) {
      continue;
    }

    const parts = rel.split('/');
    if (parts.length === 1) {
      foldersSet.add('.'); // root
    } else {
      for (let i = 0; i < parts.length - 1; i++) {
        const p = parts.slice(0, i + 1).join('/') || '.';
        // skip any ancestor folder that contains always-excluded segments
        if (!pathHasAnySegment(p, ALWAYS_EXCLUDE_FOLDERS)) {
          foldersSet.add(p === '' ? '.' : p);
        }
      }
    }
  }
  const folders = Array.from(foldersSet).sort((a, b) => a.localeCompare(b, undefined, { numeric: true }));

  // default UI state: ensure hideDeselected is enabled by default
  let hideDeselected = Boolean(defaultHideDeselected);

  // load persisted deselections (so checkboxes reflect saved choices)
  await loadDeselectedCache();

  // Determine selection mode
  const isSelectionMode = String(mode || '').toLowerCase() === 'select';

  // --- UI scaffold ---
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
    zIndex: '14000'
  });

  const card = document.createElement('div');
  Object.assign(card.style, {
    display: 'flex',
    gap: '12px',
    background: '#fff',
    padding: '16px',
    borderRadius: '8px',
    boxShadow: '0 8px 28px rgba(0,0,0,0.12)',
    width: 'min(92vw, 960px)',
    maxWidth: '960px',
    boxSizing: 'border-box'
  });

  // LEFT: file list (top half) + folder list (bottom half)
  const left = document.createElement('div');
  Object.assign(left.style, {
    display: 'flex',
    flexDirection: 'column',
    gap: '8px',
    flex: '1',
    minWidth: '360px'
  });

  const title = document.createElement('div');
  title.textContent = dialogTitle || 'Txtfilelize — select files to combine';
  Object.assign(title.style, { fontSize: '15px', fontWeight: '600', color: '#111' });

  const info = document.createElement('div');
  info.textContent = `Base folder: ${baseFolder}`;
  Object.assign(info.style, { fontSize: '12px', color: '#444', background: '#f7f9fb', padding: '8px', borderRadius: '6px', border: '1px solid #eef4ff', wordBreak: 'break-all' });

  // controls row
  const controlsRow = document.createElement('div');
  Object.assign(controlsRow.style, { display: 'flex', gap: '8px', alignItems: 'center' });

  const selectAllBtn = document.createElement('button');
  selectAllBtn.textContent = 'Select all';
  Object.assign(selectAllBtn.style, { padding: '6px 10px', borderRadius: '6px', border: '1px solid #d6dde6', background: '#f7f9fb', cursor: 'pointer' });

  const deselectAllBtn = document.createElement('button');
  deselectAllBtn.textContent = 'Deselect all';
  Object.assign(deselectAllBtn.style, { padding: '6px 10px', borderRadius: '6px', border: '1px solid #d6dde6', background: '#f7f9fb', cursor: 'pointer' });

  const invertBtn = document.createElement('button');
  invertBtn.textContent = 'Invert';
  Object.assign(invertBtn.style, { padding: '6px 10px', borderRadius: '6px', border: '1px solid #d6dde6', background: '#f7f9fb', cursor: 'pointer' });

  const hideDeselectedBtn = document.createElement('button');
  Object.assign(hideDeselectedBtn.style, { padding: '6px 10px', borderRadius: '6px', border: '1px solid #d6dde6', background: '#fff', cursor: 'pointer' });

  controlsRow.appendChild(selectAllBtn);
  controlsRow.appendChild(deselectAllBtn);
  controlsRow.appendChild(invertBtn);
  controlsRow.appendChild(hideDeselectedBtn);

  // file list container (TOP half)
  const fileList = document.createElement('div');
  Object.assign(fileList.style, {
    height: '240px',
    overflowY: 'auto',
    borderRadius: '6px',
    border: '1px solid #e6edf5',
    background: '#fff',
    display: 'flex',
    flexDirection: 'column',
    gap: '0',
    padding: '6px',
    boxSizing: 'border-box'
  });

  // folder list container (BOTTOM half)
  const folderTitle = document.createElement('div');
  folderTitle.textContent = 'Folders (deselect to exclude their files)';
  Object.assign(folderTitle.style, { fontSize: '13px', fontWeight: '600', color: '#111' });

  const folderList = document.createElement('div');
  Object.assign(folderList.style, {
    height: '240px',
    overflowY: 'auto',
    borderRadius: '6px',
    border: '1px solid #e6edf5',
    background: '#fff',
    display: 'flex',
    flexDirection: 'column',
    gap: '0',
    padding: '6px',
    boxSizing: 'border-box'
  });

  // excluded summary & list
  const excludedSummary = document.createElement('div');
  Object.assign(excludedSummary.style, { fontSize: '12px', color: '#666' });

  const showExcludedBtn = document.createElement('button');
  showExcludedBtn.textContent = 'Show excluded';
  Object.assign(showExcludedBtn.style, { padding: '6px 10px', borderRadius: '6px', border: '1px solid #d6dde6', background: '#fff', cursor: 'pointer', marginLeft: '8px' });

  const excludedListDiv = document.createElement('div');
  Object.assign(excludedListDiv.style, { maxHeight: '200px', overflowY: 'auto', display: 'none', border: '1px solid #f1f5f9', padding: '8px', borderRadius: '6px', background: '#fff' });

  // status / footer
  const statusDiv = document.createElement('div');
  Object.assign(statusDiv.style, { minHeight: '18px', color: '#333', fontSize: '13px' });

  left.appendChild(title);
  left.appendChild(info);
  left.appendChild(controlsRow);
  left.appendChild(fileList);
  left.appendChild(folderTitle);
  left.appendChild(folderList);
  left.appendChild(excludedSummary);
  left.appendChild(showExcludedBtn);
  left.appendChild(excludedListDiv);
  left.appendChild(statusDiv);

  // RIGHT: actions + filename input + include-tree option
  const right = document.createElement('div');
  Object.assign(right.style, {
    width: '320px',
    minWidth: '240px',
    display: 'flex',
    flexDirection: 'column',
    gap: '8px',
    boxSizing: 'border-box'
  });

  const rightTitle = document.createElement('div');
  rightTitle.textContent = 'Actions';
  Object.assign(rightTitle.style, { fontSize: '14px', fontWeight: '600' });

  const filenameLabel = document.createElement('div');
  filenameLabel.textContent = 'Output filename:';
  Object.assign(filenameLabel.style, { fontSize: '12px', color: '#444' });

  const filenameInput = document.createElement('input');
  filenameInput.type = 'text';
  filenameInput.value = defaultFilename || 'projectinfo.txt';
  Object.assign(filenameInput.style, { padding: '8px', borderRadius: '6px', border: '1px solid #d6dde6', background: '#fff', outline: 'none' });

  const suggestionDiv = document.createElement('div');
  Object.assign(suggestionDiv.style, { fontSize: '12px', color: '#0b66ff', cursor: 'pointer', display: 'none', marginTop: '6px' });
  suggestionDiv.title = 'Click to use suggested filename';

  // include tree checkbox
  const includeTreeRow = document.createElement('label');
  Object.assign(includeTreeRow.style, { display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer', fontSize: '13px' });
  const includeTreeCheckbox = document.createElement('input');
  includeTreeCheckbox.type = 'checkbox';
  includeTreeCheckbox.checked = defaultIncludeTree;
  includeTreeRow.appendChild(includeTreeCheckbox);
  const includeTreeLabel = document.createElement('div');
  includeTreeLabel.textContent = 'Include file structure tree for selected files';
  includeTreeRow.appendChild(includeTreeLabel);

  // use relative paths checkbox
  const useRelativeRow = document.createElement('label');
  Object.assign(useRelativeRow.style, { display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer', fontSize: '13px' });
  const useRelativeCheckbox = document.createElement('input');
  useRelativeCheckbox.type = 'checkbox';
  useRelativeCheckbox.checked = defaultUseRelative;
  useRelativeRow.appendChild(useRelativeCheckbox);
  const useRelativeLabel = document.createElement('div');
  useRelativeLabel.textContent = 'Use relative paths instead of full path (default enabled)';
  Object.assign(useRelativeLabel.style, { color: '#444' });
  useRelativeRow.appendChild(useRelativeLabel);

  const createBtn = document.createElement('button');
  Object.assign(createBtn.style, {
    padding: '10px',
    borderRadius: '6px',
    border: '1px solid #0b66ff',
    background: 'linear-gradient(180deg, #0b66ff, #075fe6)',
    color: '#fff',
    cursor: 'pointer'
  });

  const cancelBtn = document.createElement('button');
  cancelBtn.textContent = 'Cancel';
  Object.assign(cancelBtn.style, {
    padding: '8px',
    borderRadius: '6px',
    border: '1px solid #d6dde6',
    background: '#f7f9fb',
    cursor: 'pointer'
  });

  const note = document.createElement('div');
  note.textContent = 'Hidden files (starting with ".") and .DS_Store are already excluded.';
  Object.assign(note.style, { fontSize: '12px', color: '#666' });

  // Append right-side elements conditionally (hide filename controls in selection mode)
  right.appendChild(rightTitle);
  if (!isSelectionMode) {
    right.appendChild(filenameLabel);
    right.appendChild(filenameInput);
    right.appendChild(suggestionDiv);
  }
  right.appendChild(includeTreeRow);
  right.appendChild(useRelativeRow);
  right.appendChild(createBtn);
  right.appendChild(cancelBtn);
  right.appendChild(note);

  card.appendChild(left);
  card.appendChild(right);
  overlay.appendChild(card);
  document.body.appendChild(overlay);

  // --- rendering / behavior helpers ---

  function updateDeselectedCacheFromUI() {
    const fileCbs = Array.from(fileList.querySelectorAll('input[type="checkbox"].tf-checkbox'));
    const newSet = new Set();
    for (const cb of fileCbs) {
      const relproj = cb.dataset.relproj || cb.dataset.relproject || cb.dataset.rel;
      if (!relproj) continue;
      const n = String(relproj).replace(/\\/g, '/');
      if (!cb.checked) newSet.add(n);
    }
    deselectedCache = newSet;
    scheduleSaveDeselected();
  }

  // render folder list
  function renderFolderList() {
    folderList.innerHTML = '';
    if (!folders.length) {
      const none = document.createElement('div');
      none.textContent = '(No folders found)';
      Object.assign(none.style, { padding: '8px', color: '#666' });
      folderList.appendChild(none);
      return;
    }

    for (const folder of folders) {
      // skip any folder that should be always hidden (already filtered earlier, but double-check)
      if (pathHasAnySegment(folder, ALWAYS_EXCLUDE_FOLDERS)) continue;

      const folderNormalized = String(folder).replace(/\\/g, '/');
      const row = document.createElement('div');
      Object.assign(row.style, { display: 'flex', alignItems: 'center', gap: '8px', padding: '6px 8px', borderBottom: '1px solid #f1f5f9', cursor: 'pointer' });

      const cb = document.createElement('input');
      cb.type = 'checkbox';
      cb.className = 'tf-folder-checkbox';
      cb.dataset.folder = folderNormalized;

      // auto-deselect folders that look like build/vendor/cache directories
      const isAutoDeselect = (folderNormalized !== '.') && folderNormalized.split('/').some(seg => AUTO_DESELECT_FOLDERS.has(seg));

      // Determine if any file under this folder is currently selected (i.e. NOT in deselectedCache)
      let anySelected = false;
      for (const f of files) {
        const relNorm = f.relNormalized;
        const under = (folderNormalized === '.') ? true : (relNorm === folderNormalized || relNorm.startsWith(folderNormalized + '/'));
        if (under) {
          const relProj = f.relProject;
          // Skip files in always-excluded segments
          if (pathHasAnySegment(relProj, ALWAYS_EXCLUDE_FOLDERS)) continue;
          if (!deselectedCache.has(relProj)) { anySelected = true; break; }
        }
      }

      // default checked state: selected unless auto-deselected; but if anySelected present, prefer that
      let initialChecked = !isAutoDeselect;
      if (anySelected) initialChecked = true;
      cb.checked = initialChecked;

      const lbl = document.createElement('div');
      lbl.textContent = folderNormalized === '.' ? '.' : folderNormalized;
      Object.assign(lbl.style, { fontSize: '13px', color: '#111', wordBreak: 'break-all' });

      row.appendChild(cb);
      row.appendChild(lbl);

      row.addEventListener('click', (e) => {
        if (e.target === cb) return;
        cb.checked = !cb.checked;
        updateFileStates();
      });

      cb.addEventListener('change', (e) => {
        e.stopPropagation();
        updateFileStates();
      });

      folderList.appendChild(row);
    }
  }

  // render file list
  function renderFileList() {
    fileList.innerHTML = '';
    if (!files.length) {
      const none = document.createElement('div');
      none.textContent = '(No supported text files found)';
      Object.assign(none.style, { padding: '8px', color: '#666' });
      fileList.appendChild(none);
      createBtn.disabled = true;
      return;
    }

    for (const { fullPath, relPath, relNormalized, relProject } of files) {
      const row = document.createElement('div');
      Object.assign(row.style, { display: 'flex', alignItems: 'center', gap: '8px', padding: '6px 8px', borderBottom: '1px solid #f1f5f9', cursor: 'pointer' });

      const cb = document.createElement('input');
      cb.type = 'checkbox';
      cb.className = 'tf-checkbox';
      cb.dataset.fullpath = fullPath;
      cb.dataset.rel = relNormalized;
      cb.dataset.relproj = relProject;

      // initialize checked state based on persisted cache and auto-deselect rules
      const isAlwaysExcludedFile = pathHasAnySegment(relNormalized, ALWAYS_EXCLUDE_FOLDERS);
      const isAutoDeselectFile = pathHasAnySegment(relNormalized, AUTO_DESELECT_FOLDERS);

      if (deselectedCache.has(relProject)) {
        cb.checked = false;
      } else if (isAlwaysExcludedFile) {
        cb.checked = false;
      } else if (isAutoDeselectFile) {
        cb.checked = false;
      } else {
        cb.checked = true;
      }

      const lbl = document.createElement('div');
      lbl.textContent = relNormalized;
      Object.assign(lbl.style, { fontSize: '13px', color: '#111', wordBreak: 'break-all' });

      row.appendChild(cb);
      row.appendChild(lbl);

      row.addEventListener('click', (e) => {
        if (e.target === cb) return;
        if (cb.disabled) return;
        cb.checked = !cb.checked;
        applyHideDeselected();
        updateDeselectedCacheFromUI();
      });

      cb.addEventListener('change', (e) => {
        e.stopPropagation();
        applyHideDeselected();
        updateDeselectedCacheFromUI();
      });

      fileList.appendChild(row);
    }
  }

  // update file and folder checkbox states based on folder selections (with cascade)
  function updateFileStates(persist = true) {
    const folderCbs = Array.from(folderList.querySelectorAll('input[type="checkbox"].tf-folder-checkbox'));
    const folderMap = new Map(folderCbs.map(cb => [cb.dataset.folder.replace(/\\/g, '/'), cb]));
    const rootCb = folderMap.get('.');

    for (const cb of folderCbs) {
      const folder = cb.dataset.folder.replace(/\\/g, '/');
      let shouldDisableFolder = false;

      if (rootCb && !rootCb.checked && folder !== '.') {
        shouldDisableFolder = true;
      } else {
        const parts = folder.split('/');
        for (let i = 0; i < parts.length - 1; i++) {
          const parent = parts.slice(0, i + 1).join('/');
          const parentCb = folderMap.get(parent);
          if (parentCb && !parentCb.checked) {
            shouldDisableFolder = true;
            break;
          }
        }
      }

      const row = cb.parentElement;
      if (shouldDisableFolder) {
        if (typeof cb.dataset.prevChecked === 'undefined') {
          cb.dataset.prevChecked = cb.checked ? '1' : '0';
        }
        cb.checked = false;
        cb.disabled = true;
        if (row) row.style.opacity = '0.5';
        if (row) row.style.cursor = 'not-allowed';
      } else {
        if (typeof cb.dataset.prevChecked !== 'undefined') {
          cb.checked = cb.dataset.prevChecked === '1';
          delete cb.dataset.prevChecked;
        }
        cb.disabled = false;
        if (row) row.style.opacity = '1';
        if (row) row.style.cursor = 'pointer';
      }
    }

    const uncheckedFolders = Array.from(folderList.querySelectorAll('input[type="checkbox"].tf-folder-checkbox')).filter(cb => !cb.checked).map(cb => cb.dataset.folder.replace(/\\/g, '/'));

    const fileCbs = Array.from(fileList.querySelectorAll('input[type="checkbox"].tf-checkbox'));
    for (const cb of fileCbs) {
      const rel = (cb.dataset.rel || '').replace(/\\/g, '/');
      let shouldDisable = false;
      for (const folder of uncheckedFolders) {
        if (folder === '.') {
          shouldDisable = true;
          break;
        }
        const matchPrefix = folder + '/';
        if (rel === folder || rel.startsWith(matchPrefix)) {
          shouldDisable = true;
          break;
        }
      }

      const row = cb.parentElement;
      if (shouldDisable) {
        if (typeof cb.dataset.prevChecked === 'undefined') {
          cb.dataset.prevChecked = cb.checked ? '1' : '0';
        }
        cb.checked = false;
        cb.disabled = true;
        if (row) row.style.opacity = '0.5';
        if (row) row.style.cursor = 'not-allowed';
      } else {
        if (typeof cb.dataset.prevChecked !== 'undefined') {
          cb.checked = cb.dataset.prevChecked === '1';
          delete cb.dataset.prevChecked;
        }
        cb.disabled = false;
        if (row) row.style.opacity = '1';
        if (row) row.style.cursor = 'pointer';
      }
    }

    const enabledCount = fileList.querySelectorAll('input[type="checkbox"].tf-checkbox:not(:disabled)').length;
    createBtn.disabled = enabledCount === 0;

    applyHideDeselected();

    if (persist) {
      updateDeselectedCacheFromUI();
    }
  }

  // show/hide rows that are currently deselected
  function applyHideDeselected() {
    const folderCbs = Array.from(folderList.querySelectorAll('input[type="checkbox"].tf-folder-checkbox'));
    for (const cb of folderCbs) {
      const row = cb.parentElement;
      if (!row) continue;
      if (hideDeselected && !cb.checked) {
        row.style.display = 'none';
      } else {
        row.style.display = '';
      }
    }

    const fileCbs = Array.from(fileList.querySelectorAll('input[type="checkbox"].tf-checkbox'));
    for (const cb of fileCbs) {
      const row = cb.parentElement;
      if (!row) continue;
      if (hideDeselected && !cb.checked) {
        row.style.display = 'none';
      } else {
        row.style.display = '';
      }
    }
  }

  function renderExcluded() {
    const totalExcluded = excludedBinary.length + excludedImage.length + excludedUnreadable.length;
    excludedSummary.textContent = `${files.length} supported file(s). ${totalExcluded} excluded (${excludedBinary.length} binary, ${excludedImage.length} image, ${excludedUnreadable.length} unreadable).`;
    excludedListDiv.innerHTML = '';

    if (totalExcluded === 0) {
      excludedListDiv.textContent = '(No excluded files)';
      return;
    }

    if (excludedBinary.length) {
      const h = document.createElement('div');
      h.textContent = 'Binary (excluded):';
      Object.assign(h.style, { fontWeight: '600', marginTop: '6px', fontSize: '13px' });
      excludedListDiv.appendChild(h);
      excludedBinary.forEach(n => {
        const el = document.createElement('div');
        el.textContent = n;
        Object.assign(el.style, { fontSize: '12px', color: '#333', marginLeft: '6px' });
        excludedListDiv.appendChild(el);
      });
    }

    if (excludedImage.length) {
      const h = document.createElement('div');
      h.textContent = 'Images (excluded):';
      Object.assign(h.style, { fontWeight: '600', marginTop: '6px', fontSize: '13px' });
      excludedListDiv.appendChild(h);
      excludedImage.forEach(n => {
        const el = document.createElement('div');
        el.textContent = n;
        Object.assign(el.style, { fontSize: '12px', color: '#333', marginLeft: '6px' });
        excludedListDiv.appendChild(el);
      });
    }

    if (excludedUnreadable.length) {
      const h = document.createElement('div');
      h.textContent = 'Unreadable (excluded):';
      Object.assign(h.style, { fontWeight: '600', marginTop: '6px', fontSize: '13px' });
      excludedListDiv.appendChild(h);
      excludedUnreadable.forEach(n => {
        const el = document.createElement('div');
        el.textContent = n;
        Object.assign(el.style, { fontSize: '12px', color: '#333', marginLeft: '6px' });
        excludedListDiv.appendChild(el);
      });
    }
  }

  showExcludedBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    if (excludedListDiv.style.display === 'none') {
      excludedListDiv.style.display = 'block';
      showExcludedBtn.textContent = 'Hide excluded';
    } else {
      excludedListDiv.style.display = 'none';
      showExcludedBtn.textContent = 'Show excluded';
    }
  });

  // select/deselect/invert (operate only on enabled file checkboxes)
  selectAllBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    const cbs = fileList.querySelectorAll('input[type="checkbox"].tf-checkbox:not(:disabled)');
    cbs.forEach(cb => cb.checked = true);
    applyHideDeselected();
    updateDeselectedCacheFromUI();
  });
  deselectAllBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    const cbs = fileList.querySelectorAll('input[type="checkbox"].tf-checkbox:not(:disabled)');
    cbs.forEach(cb => cb.checked = false);
    applyHideDeselected();
    updateDeselectedCacheFromUI();
  });
  invertBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    const cbs = fileList.querySelectorAll('input[type="checkbox"].tf-checkbox:not(:disabled)');
    cbs.forEach(cb => cb.checked = !cb.checked);
    applyHideDeselected();
    updateDeselectedCacheFromUI();
  });

  // hide deselected toggle
  hideDeselectedBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    hideDeselected = !hideDeselected;
    hideDeselectedBtn.textContent = hideDeselected ? 'Show deselected' : 'Hide deselected';
    applyHideDeselected();
  });
  hideDeselectedBtn.textContent = hideDeselected ? 'Show deselected' : 'Hide deselected';
  applyHideDeselected();

  // filename suggestion behavior (only in create mode)
  function computeSuggestedFilename() {
    const baseNameRaw = path.basename(baseFolder) || 'project';
    const safe = String(baseNameRaw).replace(/[\/\\:*?"<>|]/g, '').replace(/\s+/g, '_') || 'project';
    return `${safe}.txt`;
  }
  function showSuggestion() {
    const s = computeSuggestedFilename();
    suggestionDiv.textContent = `Suggested: ${s}`;
    suggestionDiv.style.display = 'block';
  }
  function hideSuggestion() {
    suggestionDiv.style.display = 'none';
  }

  if (!isSelectionMode) {
    filenameInput.addEventListener('focus', () => {
      showSuggestion();
    });
    filenameInput.addEventListener('input', () => {
      showSuggestion();
      createBtn.textContent = `Create .auaci/tmp/${filenameInput.value || defaultFilename}`;
    });
    filenameInput.addEventListener('blur', () => {
      setTimeout(() => {
        hideSuggestion();
      }, 150);
    });
    suggestionDiv.addEventListener('click', (e) => {
      e.stopPropagation();
      filenameInput.value = computeSuggestedFilename();
      hideSuggestion();
      filenameInput.focus();
      createBtn.textContent = `Create .auaci/tmp/${filenameInput.value || defaultFilename}`;
    });
  }

  // set initial create button text depending on mode
  function updateCreateBtnText() {
    if (isSelectionMode) {
      createBtn.textContent = createButtonText || 'Use selection';
    } else {
      createBtn.textContent = `Create .auaci/tmp/${filenameInput.value || defaultFilename}`;
    }
  }
  updateCreateBtnText();

  // initial render
  renderFolderList();
  renderFileList();
  updateFileStates(false);
  renderExcluded();

  // create / cancel actions
  function closeOverlay() {
    try {
      if (document.body.contains(overlay)) document.body.removeChild(overlay);
    } catch (err) {
      // ignore
    }
  }

  // Promise to return result to caller
  return new Promise((resolve) => {
    createBtn.addEventListener('click', async (e) => {
      e.stopPropagation();
      createBtn.disabled = true;
      cancelBtn.disabled = true;
      statusDiv.textContent = isSelectionMode ? 'Processing...' : 'Creating...';

      try {
        const checkedBoxes = Array.from(fileList.querySelectorAll('input[type="checkbox"].tf-checkbox')).filter(cb => cb.checked && !cb.disabled);
        if (!checkedBoxes.length) {
          statusDiv.textContent = 'No files selected.';
          createBtn.disabled = false;
          cancelBtn.disabled = false;
          return;
        }

        // In create mode we validate the filename; in selection mode we skip filename handling.
        let outFilename = null;
        if (!isSelectionMode) {
          // Validate filename input
          let outRaw = String(filenameInput.value || '').trim();
          if (!outRaw) {
            statusDiv.textContent = 'Please provide an output filename.';
            createBtn.disabled = false;
            cancelBtn.disabled = false;
            return;
          }
          outRaw = path.basename(outRaw);
          if (!path.extname(outRaw)) outRaw = `${outRaw}.txt`;
          outFilename = outRaw;
        }

        const selectedFullPaths = checkedBoxes.map(cb => cb.dataset.fullpath);
        const selectedRelPaths = checkedBoxes.map(cb => cb.dataset.rel);
        const selectedRelProjects = checkedBoxes.map(cb => cb.dataset.relproj);

        // Ensure cache saved before resolving
        try {
          await flushSaveDeselected();
        } catch (err) {
          // ignore
        }

        closeOverlay();

        const result = {
          action: 'create',
          includeTree: Boolean(includeTreeCheckbox.checked),
          useRelative: Boolean(useRelativeCheckbox.checked),
          selectedFullPaths,
          selectedRelPaths,
          selectedRelProjects
        };
        if (!isSelectionMode) result.outFilename = outFilename;

        resolve(result);
      } catch (err) {
        console.error('Failed in create action:', err);
        statusDiv.textContent = `Failed: ${err.message || String(err)}`;
        createBtn.disabled = false;
        cancelBtn.disabled = false;
      }
    });

    cancelBtn.addEventListener('click', async (e) => {
      e.stopPropagation();
      try {
        await flushSaveDeselected();
      } catch (err) {
        // ignore
      }
      closeOverlay();
      resolve({ action: 'cancel' });
    });

    // close when clicking outside
    overlay.addEventListener('click', async (e) => {
      if (e.target === overlay) {
        try {
          await flushSaveDeselected();
        } catch (err) {
          // ignore
        }
        closeOverlay();
        resolve({ action: 'cancel' });
      }
    });
    card.addEventListener('click', (e) => e.stopPropagation());
  });
}

module.exports = { showFileSelector };