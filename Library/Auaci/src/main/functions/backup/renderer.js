// src/main/functions/backup/renderer.js
// Renderer code for backup window with directory expand/collapse, descendant-checkbox propagation,
// blacklist/whitelist rules, progress visibility control, and empty-state for restore list.

// Safely obtain ipcRenderer. Prefer window.__electron.ipcRenderer if provided.
const ipcRenderer = (typeof window !== 'undefined' && window.__electron && window.__electron.ipcRenderer)
  ? window.__electron.ipcRenderer
  : require('electron').ipcRenderer;

let projectRoot = null;
let cache = null;
let tree = null;
let selectedBackupItem = null;

// Unique id counter for wiring label -> checkbox and aria-controls
let nodeIdCounter = 0;
// Token to keep track of the latest tree render so older scans can be cancelled
let currentTreeRenderToken = 0;

// Active background scans that can be cancelled
let activeBackgroundScans = new Map();

// File size tracking for selected items
let selectedFilesSize = 0;
let selectedFilesCount = 0;

function $(sel) { return document.querySelector(sel); }
function $all(sel) { return Array.from(document.querySelectorAll(sel)); }

// Control loading state for the tree and the backup button. While loading the
// project tree we disable the "Create Backup" button so the user cannot start
// a backup until everything is ready.
function setTreeLoading(isLoading) {
  const container = $('#tree-container');
  if (container) {
    container.style.opacity = isLoading ? '0.6' : '';
    container.style.pointerEvents = isLoading ? 'none' : '';
    container.setAttribute('aria-busy', isLoading ? 'true' : 'false');
  }

  const btn = $('#create-backup-btn');
  if (btn) {
    btn.disabled = !!isLoading;
  }

  const ind = $('#tree-loading-indicator');
  if (ind) {
    ind.style.display = isLoading ? '' : 'none';
  }
}

function normalizeRel(p) {
  if (!p) return '';
  return String(p).replace(/\\/g, '/').replace(/\/+$/,'');
}

function pathIsUnder(nodeRel, listEntry) {
  if (!listEntry) return false;
  const n = normalizeRel(nodeRel);
  const e = normalizeRel(listEntry);
  if (!e) return false;
  if (n === e) return true;
  return n.startsWith(e + '/');
}

function isMatchedByList(nodeRel, listArray) {
  if (!listArray || !listArray.length) return false;
  for (const entry of listArray) {
    if (pathIsUnder(nodeRel, entry)) return true;
  }
  return false;
}

function isUnderIgnorePrefix(nodeRel) {
  if (!cache || !Array.isArray(cache.ignoreListPrefixes)) return false;
  const n = normalizeRel(nodeRel);
  for (const entry of cache.ignoreListPrefixes) {
    const e = normalizeRel(entry);
    if (!e) continue;
    if (n === e || n.startsWith(e + '/')) return true;
  }
  return false;
}

/* UI helpers for progress row */
function showProgressRow() {
  const pr = $('#progress-row');
  if (pr) {
    pr.style.display = 'block';
    pr.setAttribute('aria-hidden', 'false');
  }
}
function hideProgressRow() {
  const pr = $('#progress-row');
  if (pr) {
    pr.style.display = 'none';
    pr.setAttribute('aria-hidden', 'true');
    $('#progress-fill').style.width = '0%';
    $('#progress-text').innerText = '';
  }
}

function showRestoreProgressRow() {
  const pr = $('#restore-progress-row');
  if (pr) {
    pr.style.display = 'block';
    pr.setAttribute('aria-hidden', 'false');
  }
}

function hideRestoreProgressRow() {
  const pr = $('#restore-progress-row');
  if (pr) {
    pr.style.display = 'none';
    pr.setAttribute('aria-hidden', 'true');
    $('#restore-progress-fill').style.width = '0%';
    $('#restore-progress-text').innerText = '';
  }
}

function setRestoreProgress(percent, text) {
  const p = Math.max(0, Math.min(100, percent || 0));
  $('#restore-progress-fill').style.width = `${p}%`;
  if (text) $('#restore-progress-text').innerText = text;
}

function applyRestoreState(state) {
  const restoreBtn = $('#restore-btn');
  if (!state || !state.state || state.state === 'idle') {
    if (restoreBtn) {
      restoreBtn.disabled = false;
      restoreBtn.classList.remove('loading');
    }
    hideRestoreProgressRow();
    return;
  }

  if ((state.state === 'done' || state.state === 'error') && state.finishedAt) {
    const ageMs = Date.now() - state.finishedAt;
    if (ageMs > 4000) {
      if (restoreBtn) {
        restoreBtn.disabled = false;
        restoreBtn.classList.remove('loading');
      }
      hideRestoreProgressRow();
      return;
    }
  }

  if (state.state === 'running') {
    if (restoreBtn) {
      restoreBtn.disabled = true;
      restoreBtn.classList.add('loading');
    }
    showRestoreProgressRow();
    setRestoreProgress(20, state.message || 'Restoring backup...');
    return;
  }

  if (restoreBtn) {
    restoreBtn.disabled = false;
    restoreBtn.classList.remove('loading');
  }

  showRestoreProgressRow();
  if (state.state === 'done') {
    setRestoreProgress(100, state.message || 'Restore complete.');
    setTimeout(() => hideRestoreProgressRow(), 1200);
  } else if (state.state === 'error') {
    const errMsg = state.error ? `Restore failed: ${state.error}` : 'Restore failed.';
    setRestoreProgress(100, errMsg);
    setTimeout(() => hideRestoreProgressRow(), 2000);
  }
}

async function syncRestoreStatus() {
  try {
    const status = await ipcRenderer.invoke('backup-restore-status');
    applyRestoreState(status);
  } catch (_) {}
}

async function init() {
  projectRoot = await ipcRenderer.invoke('get-project-root');

  cache = await ipcRenderer.invoke('backup-load-cache', projectRoot);
  // If cache lacks list (first run), populate with defaults & persist
  if (!cache.list || cache.list.length === 0) {
    const defs = await ipcRenderer.invoke('backup-get-defaults');
    cache.list = defs.list.slice();
    cache.preserveOnRestore = defs.preserveOnRestore ? defs.preserveOnRestore.slice() : [];
    cache.mode = cache.mode || defs.mode;
    await ipcRenderer.invoke('backup-save-cache', projectRoot, cache);
  }
  if (!Array.isArray(cache.ignoreListPrefixes)) {
    cache.ignoreListPrefixes = [];
  }

  setupTabs();
  setupCloseButton();

  // Set the backup-folder input to the resolved absolute path for clarity
  const resolved = await ipcRenderer.invoke('backup-resolve-path', projectRoot, cache.backupFolder || '../backup');
  $('#backup-folder').value = resolved;
  $('#restore-folder').value = resolved;
  cache.backupFolder = cache.backupFolder || '../backup';
  await ipcRenderer.invoke('backup-save-cache', projectRoot, cache);

  // Set mode buttons based on cache.mode
  setModeButtons();

  renderListItems();
  renderPreserveList();

  await refreshTree();

  // Bind controls
  $('#add-list-item').addEventListener('click', onAddListItem);
  $('#list-input').addEventListener('keydown', (e) => { if (e.key === 'Enter') onAddListItem(); });

  $('#create-backup-btn').addEventListener('click', onCreateBackup);
  $('#backup-name').value = '';

  $('#backup-folder').addEventListener('change', async (e) => {
    cache.backupFolder = e.target.value.trim() || '../backup';
    await ipcRenderer.invoke('backup-save-cache', projectRoot, cache);
  });

  // Quick choices menu when clicking the folder inputs (toggle between outside/inside workspace)
  const ensureFolderMenuStyles = () => {
    if (document.getElementById('backup-folder-menu-styles')) return;
    const s = document.createElement('style');
    s.id = 'backup-folder-menu-styles';
    s.textContent = `
      .backup-folder-menu { position: fixed; z-index: 2147483647; background:#fff; border:1px solid #e5e7eb; border-radius:6px; box-shadow:0 12px 36px rgba(0,0,0,0.12); padding:6px 0; min-width: 280px; }
      .backup-folder-menu .opt { padding:8px 12px; cursor:pointer; display:flex; flex-direction:column; }
      .backup-folder-menu .opt:hover { background:#f3f4f6; }
      .backup-folder-menu .title { font-weight:600; color:#111827; }
      .backup-folder-menu .path { color:#6b7280; font-size:12px; word-break:break-all; }
    `;
    document.head.appendChild(s);
  };

  async function showFolderChoices(anchorInput) {
    ensureFolderMenuStyles();
    // Remove existing
    try { const old = document.querySelector('.backup-folder-menu'); if (old) old.remove(); } catch (_) {}

    const menu = document.createElement('div');
    menu.className = 'backup-folder-menu';

    const outsideAbs = await ipcRenderer.invoke('backup-resolve-path', projectRoot, '../backup');
    const insideAbs = await ipcRenderer.invoke('backup-resolve-path', projectRoot, './backup');

    const makeOpt = (title, abs, rel) => {
      const div = document.createElement('div');
      div.className = 'opt';
      const t = document.createElement('div'); t.className = 'title'; t.textContent = title;
      const p = document.createElement('div'); p.className = 'path'; p.textContent = abs;
      div.appendChild(t); div.appendChild(p);
      div.addEventListener('click', async () => {
        try {
          // Update both inputs to keep UI consistent
          $('#backup-folder').value = abs;
          $('#restore-folder').value = abs;
          cache.backupFolder = rel; // store relative choice in cache
          await ipcRenderer.invoke('backup-save-cache', projectRoot, cache);
          await refreshBackupList();
        } finally {
          try { menu.remove(); } catch (_) {}
        }
      });
      return div;
    };

    menu.appendChild(makeOpt('Outside workspace (../backup)', outsideAbs, '../backup'));
    menu.appendChild(makeOpt('Inside workspace (./backup)', insideAbs, './backup'));

    document.body.appendChild(menu);

    // Position under the input
    const rect = anchorInput.getBoundingClientRect();
    menu.style.left = Math.round(rect.left) + 'px';
    menu.style.top = Math.round(rect.bottom + 6) + 'px';

    // Dismiss
    setTimeout(() => {
      const close = (e) => {
        if (!menu.contains(e.target)) {
          try { menu.remove(); } catch (_) {}
          window.removeEventListener('mousedown', close, true);
          window.removeEventListener('keydown', onKey, true);
        }
      };
      const onKey = (e) => { if (e.key === 'Escape') close(e); };
      window.addEventListener('mousedown', close, true);
      window.addEventListener('keydown', onKey, true);
    }, 0);
  }

  ['backup-folder','restore-folder'].forEach(id => {
    const el = document.getElementById(id);
    if (el) {
      el.addEventListener('focus', () => showFolderChoices(el));
      el.addEventListener('click', () => showFolderChoices(el));
    }
  });

  $('#btn-blacklist').addEventListener('click', async () => {
    cache.mode = 'blacklist';
    await ipcRenderer.invoke('backup-save-cache', projectRoot, cache);
    setModeButtons();
    await refreshTree();
    renderListItems();
  });

  $('#btn-whitelist').addEventListener('click', async () => {
    cache.mode = 'whitelist';
    await ipcRenderer.invoke('backup-save-cache', projectRoot, cache);
    setModeButtons();
    await refreshTree();
    renderListItems();
  });

  $('#add-preserve').addEventListener('click', async () => {
    const input = $('#preserve-input');
    const v = input.value.trim();
    if (!v) return;
    if (!cache.preserveOnRestore) cache.preserveOnRestore = [];
    if (!cache.preserveOnRestore.includes(v)) {
      cache.preserveOnRestore.push(v);
      input.value = '';
      await ipcRenderer.invoke('backup-save-cache', projectRoot, cache);
      renderPreserveList();
    }
  });

  $('#refresh-list').addEventListener('click', refreshBackupList);
  await refreshBackupList();

  await syncRestoreStatus();

  // progress updates from main
  ipcRenderer.on('backup-progress', (e, data) => {
    const p = Math.max(0, Math.min(100, data.percent || 0));
    $('#progress-fill').style.width = `${p}%`;
    $('#progress-text').innerText = (data.total && data.processed) ? `${data.processed}/${data.total}` : `${p}%`;
  });

  ipcRenderer.on('backup-restore-state', (e, data) => {
    applyRestoreState(data);
  });

  // Hide progress by default
  hideProgressRow();
  hideRestoreProgressRow();
}

function setModeButtons() {
  const mode = cache.mode || 'blacklist';
  $('#btn-blacklist').classList.toggle('mode-active', mode === 'blacklist');
  $('#btn-whitelist').classList.toggle('mode-active', mode === 'whitelist');
  $('#list-type').innerText = (mode === 'blacklist') ? 'Blacklist' : 'Whitelist';
}

async function refreshTree() {
  // Start a new logical render; any older in-flight renders should not
  // mutate the UI once this one begins.
  const token = ++currentTreeRenderToken;
  setTreeLoading(true);

  // Cancel any active background scans
  cancelAllBackgroundScans();

  const res = await ipcRenderer.invoke('backup-scan-project-first-layer', projectRoot);
  if (token !== currentTreeRenderToken) {
    // A newer refresh started while we were waiting; ignore these results.
    return;
  }
  if (res && res.error) {
    console.error('scan error', res.error);
    setTreeLoading(false);
    return;
  }

  tree = res;
  $('#root-title').innerText = tree.rootName || 'Project';
  const container = $('#tree-container');
  container.innerHTML = '';
  nodeIdCounter = 0;

  // Build nested tree incrementally so we don't block the UI thread. We
  // resolve the Promise only when the whole tree has been rendered, at
  // which point the backup button becomes available again.
  await new Promise((resolve) => {
    buildTreeDomIncremental(container, tree.tree || [], token, () => {
      if (token !== currentTreeRenderToken) {
        resolve();
        return;
      }
      updateTreeCount();
      setTreeLoading(false);
      
      // Start background scanning of directories
      startBackgroundDirectoryScanning(tree.tree || []);
      
      resolve();
    });
  });
}

// Recursively build tree nodes. Directories get a disclosure control and a child container
function buildTreeDom(parentEl, nodes) {
  for (const node of nodes) {
    const wrapper = document.createElement('div');
    wrapper.className = 'tree-node';

    const leftRow = document.createElement('div');
    leftRow.className = 'tree-row';
    leftRow.style.display = 'flex';
    leftRow.style.alignItems = 'center';
    leftRow.style.gap = '8px';

    // Unique ids for label -> checkbox linking and aria-controls
    const cbId = `cb-${++nodeIdCounter}`;
    const childrenId = `children-${nodeIdCounter}`;

    // Disclosure button (only meaningful for directories)
    const disclosure = document.createElement('button');
    disclosure.className = 'disclosure';
    disclosure.type = 'button';
    disclosure.setAttribute('aria-expanded', 'false');

    // Use a fixed-size SVG chevron (right arrow) and rotate it when expanded so glyph size is constant
    const chevronSvg = '<svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><path d="M9 6l6 6-6 6" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>';
    disclosure.innerHTML = chevronSvg;

    // Checkbox
    const checkbox = document.createElement('input');
    checkbox.type = 'checkbox';
    checkbox.id = cbId;
    checkbox.dataset.rel = node.relPath;
    checkbox.dataset.type = node.type;

    // Determine matched state by checking cache.list prefixes for descendants
    const matched = isMatchedByList(node.relPath, cache.list || []);

    // Determine checked & disabled according to mode
    let isChecked = true;
    if (cache.selections && Object.prototype.hasOwnProperty.call(cache.selections, node.relPath)) {
      isChecked = !!cache.selections[node.relPath];
    } else {
      isChecked = true; // default
    }

    let disabled = false;
    if (cache.mode === 'blacklist' && matched) {
      disabled = true;
      isChecked = false;
    } else if (cache.mode === 'whitelist' && !matched) {
      disabled = true;
      isChecked = false;
    }

    checkbox.checked = isChecked;
    checkbox.disabled = disabled;
    if (disabled) leftRow.classList.add('node-disabled');
    else leftRow.classList.remove('node-disabled');

    // Label
    const label = document.createElement('label');
    label.htmlFor = cbId;
    label.className = 'tree-label';
    label.style.cursor = 'pointer';
    label.textContent = node.name;
    label.title = node.relPath;

    // Add elements to left row
    leftRow.appendChild(disclosure);
    leftRow.appendChild(checkbox);
    leftRow.appendChild(label);

    wrapper.appendChild(leftRow);
    parentEl.appendChild(wrapper);

    // If directory: create child container (collapsed by default) and wire disclosure
    if (node.type === 'dir' && Array.isArray(node.children) && node.children.length) {
      const childWrap = document.createElement('div');
      childWrap.className = 'tree-children collapsed';
      childWrap.id = childrenId;
      childWrap.style.marginLeft = '14px';
      wrapper.appendChild(childWrap);

      disclosure.setAttribute('aria-controls', childWrap.id);
      disclosure.setAttribute('aria-expanded', 'false');

      // Recursively build children inside childWrap (they will begin collapsed)
      buildTreeDom(childWrap, node.children);

      // Toggle handler — rotate the svg via .expanded class so size remains constant
      disclosure.addEventListener('click', (e) => {
        e.stopPropagation();
        const willExpand = childWrap.classList.toggle('expanded');
        if (willExpand) {
          childWrap.classList.remove('collapsed');
          disclosure.classList.add('expanded');
          disclosure.setAttribute('aria-expanded', 'true');
        } else {
          childWrap.classList.remove('expanded');
          childWrap.classList.add('collapsed');
          disclosure.classList.remove('expanded');
          disclosure.setAttribute('aria-expanded', 'false');
        }
      });

      // Double-click the row toggles folder (convenience)
      leftRow.addEventListener('dblclick', (e) => {
        disclosure.click();
      });
    } else {
      // not a directory -> hide disclosure control but keep layout consistent
      disclosure.style.visibility = 'hidden';
      disclosure.setAttribute('aria-hidden', 'true');
    }

    // Checkbox change handler: update cache and (for directories) propagate to descendants
    checkbox.addEventListener('change', async (e) => {
      cache.selections = cache.selections || {};
      cache.selections[node.relPath] = !!e.target.checked;

      if (node.type === 'dir') {
        // find descendant checkboxes inside the child container and apply the same state
        const childContainer = wrapper.querySelector('.tree-children');
        if (childContainer) {
          const descendantCheckboxes = Array.from(childContainer.querySelectorAll('input[type="checkbox"]'));
          for (const dcb of descendantCheckboxes) {
            if (!dcb.disabled) {
              dcb.checked = e.target.checked;
              cache.selections[dcb.dataset.rel] = !!e.target.checked;
            }
          }
        }
      }

      await ipcRenderer.invoke('backup-save-cache', projectRoot, cache);
      updateTreeCount();
    });

    // Make clicking the label toggle checkbox (label.for links the input)
    label.addEventListener('click', (e) => {
      if (checkbox.disabled) {
        e.preventDefault();
        return;
      }
      // label click toggles checkbox — no extra logic needed here
    });
  }
}

function updateTreeCount() {
  const checkboxes = Array.from(document.querySelectorAll('#tree-container input[type=checkbox]'));
  const selectable = checkboxes.filter(cb => !cb.disabled);
  const selectedCount = selectable.filter(cb => cb.checked).length;
  const totalSelectable = selectable.length;
  
  // Calculate total size of selected files
  calculateSelectedFilesSize();
  
  const sizeText = selectedFilesSize > 0 ? `, ${selectedFilesSize} KB` : '';
  $('#tree-count').innerText = `0 ${selectedCount}/${totalSelectable} selected${sizeText}`;
}

// Incrementally build tree nodes in small batches. This keeps the backup
// window and the main window responsive even for very large projects.
function buildTreeDomIncremental(parentEl, nodes, token, done) {
  const BATCH_SIZE = 40;
  const queue = [{ parentEl, nodes: Array.isArray(nodes) ? nodes.slice() : [], index: 0 }];

  function processBatch() {
    // If a newer refreshTree() started, stop touching the DOM.
    if (token !== currentTreeRenderToken) {
      if (typeof done === 'function') done();
      return;
    }

    let processed = 0;
    while (queue.length && processed < BATCH_SIZE) {
      const frame = queue[0];
      const { parentEl, nodes } = frame;
      if (!nodes || frame.index >= nodes.length) {
        queue.shift();
        continue;
      }

      const node = nodes[frame.index++];
      const childWrap = createTreeNodeElement(node, parentEl);

      if (node.type === 'dir' && Array.isArray(node.children) && node.children.length && childWrap) {
        queue.push({ parentEl: childWrap, nodes: node.children.slice(), index: 0 });
      }

      processed += 1;
    }

    if (queue.length) {
      window.requestAnimationFrame(processBatch);
    } else if (typeof done === 'function') {
      done();
    }
  }

  window.requestAnimationFrame(processBatch);
}

// Load a lazily-skipped directory's children on demand, building its
// subtree incrementally and updating selection counts.
async function loadSubtreeForNode(node, childContainer) {
  if (!node || node.type !== 'dir' || !childContainer) return;
  if (childContainer.dataset.loaded === '1') return;

  const token = ++currentTreeRenderToken;
  setTreeLoading(true);

  // Cancel any background scan for this directory
  const scanToken = activeBackgroundScans.get(node.relPath);
  if (scanToken) {
    await ipcRenderer.invoke('backup-cancel-scan', scanToken);
    activeBackgroundScans.delete(node.relPath);
  }

  // Small inline loading hint inside this folder
  childContainer.innerHTML = '';
  const loading = document.createElement('div');
  loading.className = 'muted small';
  loading.textContent = 'Loading folder contents...';
  childContainer.appendChild(loading);

  const res = await ipcRenderer.invoke('backup-scan-directory-contents', projectRoot, node.relPath);
  if (token !== currentTreeRenderToken) {
    setTreeLoading(false);
    return;
  }
  if (!res || res.error) {
    console.error('backup-scan-directory-contents error', res && res.error);
    setTreeLoading(false);
    return;
  }

  childContainer.innerHTML = '';
  node.children = Array.isArray(res.children) ? res.children : [];
  node.lazy = false;
  childContainer.dataset.loaded = '1';

  await new Promise((resolve) => {
    buildTreeDomIncremental(childContainer, node.children, token, () => {
      if (token !== currentTreeRenderToken) {
        resolve();
        return;
      }
      updateTreeCount();
      setTreeLoading(false);
      resolve();
    });
  });
}

// Cancel all active background scans
function cancelAllBackgroundScans() {
  for (const [relPath, scanToken] of activeBackgroundScans.entries()) {
    ipcRenderer.invoke('backup-cancel-scan', scanToken).catch(() => {});
  }
  activeBackgroundScans.clear();
}

// Start background scanning of directories to populate their contents
function startBackgroundDirectoryScanning(nodes) {
  for (const node of nodes) {
    if (node.type === 'dir' && node.lazy) {
      startBackgroundScanForDirectory(node);
    }
  }
}

// Start background scan for a specific directory
async function startBackgroundScanForDirectory(node) {
  const scanToken = Date.now() + Math.random();
  activeBackgroundScans.set(node.relPath, scanToken);

  try {
    const res = await ipcRenderer.invoke('backup-scan-directory-contents', projectRoot, node.relPath);
    
    // Check if scan was cancelled or if this is still the active scan
    if (!activeBackgroundScans.has(node.relPath) || activeBackgroundScans.get(node.relPath) !== scanToken) {
      return;
    }

    if (!res || res.error || res.cancelled) {
      activeBackgroundScans.delete(node.relPath);
      return;
    }

    // Update the node with scanned contents
    node.children = Array.isArray(res.children) ? res.children : [];
    node.lazy = false;
    
    // Update UI if the node is currently visible
    const wrapper = document.querySelector(`[data-rel="${node.relPath}"]`);
    if (wrapper && wrapper.querySelector('.tree-children')) {
      const childContainer = wrapper.querySelector('.tree-children');
      if (childContainer && childContainer.dataset.loaded !== '1') {
        childContainer.innerHTML = '';
        await new Promise((resolve) => {
          buildTreeDomIncremental(childContainer, node.children, currentTreeRenderToken, () => {
            updateTreeCount();
            resolve();
          });
        });
      }
    }
    
    activeBackgroundScans.delete(node.relPath);
  } catch (err) {
    activeBackgroundScans.delete(node.relPath);
  }
}

// Calculate file sizes for selected files
async function calculateSelectedFilesSize() {
  selectedFilesSize = 0;
  selectedFilesCount = 0;
  
  const checkboxes = Array.from(document.querySelectorAll('#tree-container input[type=checkbox]'));
  const selectedCheckboxes = checkboxes.filter(cb => cb.checked && !cb.disabled);
  
  // For now, we'll use a simple approximation since we don't have actual file sizes
  // In a real implementation, we would need to stat each file
  for (const cb of selectedCheckboxes) {
    if (cb.dataset.type === 'file') {
      selectedFilesCount++;
      // Estimate file size based on extension or use a default value
      selectedFilesSize += estimateFileSize(cb.dataset.rel);
    }
  }
}

// Estimate file size based on file extension
function estimateFileSize(filePath) {
  const ext = filePath.split('.').pop().toLowerCase();
  const sizeEstimates = {
    'js': 5,      // JavaScript files ~5KB
    'ts': 5,      // TypeScript files ~5KB  
    'json': 2,    // JSON files ~2KB
    'css': 3,     // CSS files ~3KB
    'html': 3,    // HTML files ~3KB
    'md': 2,      // Markdown files ~2KB
    'txt': 1,     // Text files ~1KB
    'png': 50,    // Images ~50KB
    'jpg': 100,   // JPEG images ~100KB
    'gif': 30,    // GIF images ~30KB
    'svg': 5      // SVG files ~5KB
  };
  
  return sizeEstimates[ext] || 2; // Default 2KB for unknown file types
}

// When the user clicks a disabled folder, treat that as an explicit request
// to enable it: we record an override prefix so future scans ignore the
// blacklist/whitelist for this subtree, then load and expand its children.
async function enableAndLoadNode(node, wrapper) {
  if (!node || node.type !== 'dir' || !wrapper) return;

  cache.ignoreListPrefixes = cache.ignoreListPrefixes || [];
  const norm = normalizeRel(node.relPath);
  if (norm && !cache.ignoreListPrefixes.includes(norm)) {
    cache.ignoreListPrefixes.push(norm);
    await ipcRenderer.invoke('backup-save-cache', projectRoot, cache);
  }

  const checkbox = wrapper.querySelector('input[type="checkbox"]');
  const row = wrapper.querySelector('.tree-row');
  if (checkbox) {
    checkbox.disabled = false;
  }
  if (row) {
    row.classList.remove('node-disabled');
  }

  const childWrap = wrapper.querySelector('.tree-children');
  const disclosure = wrapper.querySelector('.disclosure');

  if (childWrap && disclosure) {
    childWrap.classList.add('expanded');
    childWrap.classList.remove('collapsed');
    disclosure.classList.add('expanded');
    disclosure.setAttribute('aria-expanded', 'true');
  }

  if (childWrap) {
    await loadSubtreeForNode(node, childWrap);
  }
}

// Create DOM for a single tree node; returns its child container (if the
// node is a directory) so the caller can enqueue its children.
function createTreeNodeElement(node, parentEl) {
  const wrapper = document.createElement('div');
  wrapper.className = 'tree-node';
  wrapper.dataset.rel = node.relPath || '';
  wrapper.dataset.type = node.type || '';
  if (node.lazy) wrapper.dataset.lazy = '1';

  const leftRow = document.createElement('div');
  leftRow.className = 'tree-row';
  leftRow.style.display = 'flex';
  leftRow.style.alignItems = 'center';
  leftRow.style.gap = '8px';

  // Unique ids for label -> checkbox linking and aria-controls
  const cbId = `cb-${++nodeIdCounter}`;
  const childrenId = `children-${nodeIdCounter}`;

  // Disclosure button (only meaningful for directories)
  const disclosure = document.createElement('button');
  disclosure.className = 'disclosure';
  disclosure.type = 'button';
  disclosure.setAttribute('aria-expanded', 'false');

  // Use a fixed-size SVG chevron (right arrow) and rotate it when expanded so glyph size is constant
  const chevronSvg = '<svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><path d="M9 6l6 6-6 6" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>';
  disclosure.innerHTML = chevronSvg;

  // Checkbox
  const checkbox = document.createElement('input');
  checkbox.type = 'checkbox';
  checkbox.id = cbId;
  checkbox.dataset.rel = node.relPath;
  checkbox.dataset.type = node.type;

  // Determine matched state by checking cache.list prefixes for descendants
  const matched = isMatchedByList(node.relPath, cache.list || []);

  // Determine checked & disabled according to mode
  let isChecked = true;
  if (cache.selections && Object.prototype.hasOwnProperty.call(cache.selections, node.relPath)) {
    isChecked = !!cache.selections[node.relPath];
  } else {
    isChecked = true; // default
  }

  const underOverride = isUnderIgnorePrefix(node.relPath);

  let disabled = false;
  if (!underOverride) {
    if (cache.mode === 'blacklist' && matched) {
      disabled = true;
      isChecked = false;
    } else if (cache.mode === 'whitelist' && !matched) {
      disabled = true;
      isChecked = false;
    }
  }

  checkbox.checked = isChecked;
  checkbox.disabled = disabled;
  if (disabled) leftRow.classList.add('node-disabled');
  else leftRow.classList.remove('node-disabled');

  // Label
  const label = document.createElement('label');
  label.htmlFor = cbId;
  label.className = 'tree-label';
  label.style.cursor = 'pointer';
  label.textContent = node.name;
  label.title = node.relPath;

  // Add elements to left row
  leftRow.appendChild(disclosure);
  leftRow.appendChild(checkbox);
  leftRow.appendChild(label);

  wrapper.appendChild(leftRow);
  parentEl.appendChild(wrapper);

  let childWrap = null;

  // If directory: create child container (collapsed by default) and wire disclosure
  if (node.type === 'dir') {
    childWrap = document.createElement('div');
    childWrap.className = 'tree-children collapsed';
    childWrap.id = childrenId;
    childWrap.style.marginLeft = '14px';
    wrapper.appendChild(childWrap);

    disclosure.setAttribute('aria-controls', childWrap.id);
    disclosure.setAttribute('aria-expanded', 'false');

    // Toggle handler — rotate the svg via .expanded class so size remains constant
    disclosure.addEventListener('click', (e) => {
      e.stopPropagation();
      const willExpand = childWrap.classList.toggle('expanded');
      if (willExpand) {
        childWrap.classList.remove('collapsed');
        disclosure.classList.add('expanded');
        disclosure.setAttribute('aria-expanded', 'true');
      } else {
        childWrap.classList.remove('expanded');
        childWrap.classList.add('collapsed');
        disclosure.classList.remove('expanded');
        disclosure.setAttribute('aria-expanded', 'false');
      }
    });

    // Double-click the row toggles folder (convenience)
    leftRow.addEventListener('dblclick', () => {
      disclosure.click();
    });
  } else {
    // not a directory -> hide disclosure control but keep layout consistent
    disclosure.style.visibility = 'hidden';
    disclosure.setAttribute('aria-hidden', 'true');
  }

  // Checkbox change handler: update cache and (for directories) propagate to descendants
  checkbox.addEventListener('change', async (e) => {
    cache.selections = cache.selections || {};
    cache.selections[node.relPath] = !!e.target.checked;

    if (node.type === 'dir') {
      // Cancel background scan if disabling the folder
      if (!e.target.checked && activeBackgroundScans.has(node.relPath)) {
        const scanToken = activeBackgroundScans.get(node.relPath);
        await ipcRenderer.invoke('backup-cancel-scan', scanToken);
        activeBackgroundScans.delete(node.relPath);
      }
      
      // find descendant checkboxes inside the child container and apply the same state
      const childContainer = wrapper.querySelector('.tree-children');
      if (childContainer) {
        const descendantCheckboxes = Array.from(childContainer.querySelectorAll('input[type="checkbox"]'));
        for (const dcb of descendantCheckboxes) {
          if (!dcb.disabled) {
            dcb.checked = e.target.checked;
            cache.selections[dcb.dataset.rel] = !!e.target.checked;
          }
        }
      }
    }

    await ipcRenderer.invoke('backup-save-cache', projectRoot, cache);
    updateTreeCount();
  });

  // Make clicking the label toggle checkbox (label.for links the input)
  label.addEventListener('click', async (e) => {
    if (checkbox.disabled) {
      e.preventDefault();
      if (node.type === 'dir') {
        await enableAndLoadNode(node, wrapper);
      }
      return;
    }
    // label click toggles checkbox — no extra logic needed here
  });

  return childWrap;
}

function renderListItems() {
  const container = $('#list-items');
  container.innerHTML = '';
  const list = cache.list || [];
  for (const item of list) {
    const row = document.createElement('div');
    row.className = 'item';
    const left = document.createElement('div');
    left.textContent = item;
    left.title = item;
    const right = document.createElement('div');
    const btn = document.createElement('button');
    btn.className = 'small-btn';
    btn.textContent = 'Remove';
    btn.addEventListener('click', async () => {
      cache.list = cache.list.filter(x => x !== item);
      await ipcRenderer.invoke('backup-save-cache', projectRoot, cache);
      await refreshTree();
      renderListItems();
    });
    right.appendChild(btn);
    row.appendChild(left);
    row.appendChild(right);
    container.appendChild(row);
  }
  $('#list-type').innerText = (cache.mode === 'whitelist') ? 'Whitelist' : 'Blacklist';
}

function renderPreserveList() {
  const container = $('#preserve-list');
  container.innerHTML = '';
  for (const item of cache.preserveOnRestore || []) {
    const row = document.createElement('div');
    row.className = 'item';
    const left = document.createElement('div');
    left.textContent = item;
    const right = document.createElement('div');
    const btn = document.createElement('button');
    btn.className = 'small-btn';
    btn.textContent = 'Remove';
    btn.addEventListener('click', async () => {
      cache.preserveOnRestore = cache.preserveOnRestore.filter(x => x !== item);
      await ipcRenderer.invoke('backup-save-cache', projectRoot, cache);
      renderPreserveList();
    });
    right.appendChild(btn);
    row.appendChild(left);
    row.appendChild(right);
    container.appendChild(row);
  }
}

async function onAddListItem() {
  const input = $('#list-input');
  const v = input.value.trim();
  if (!v) return;
  if (!cache.list) cache.list = [];
  if (!cache.list.includes(v)) {
    cache.list.push(v);
    await ipcRenderer.invoke('backup-save-cache', projectRoot, cache);
    input.value = '';
    renderListItems();
    await refreshTree();
  }
}
async function onCreateBackup() {
  const checkboxes = Array.from(document.querySelectorAll('#tree-container input[type=checkbox]'));
  const selected = [];
  const deselected = [];
  for (const cb of checkboxes) {
    if (cb.disabled) continue;
    if (cb.checked) selected.push(cb.dataset.rel);
    else deselected.push(cb.dataset.rel);
  }
  if (!selected.length) {
    alert('No files selected to backup.');
    return;
  }

  const folderInput = $('#backup-folder').value.trim() || cache.backupFolder || '../backup';
  const nameInput = $('#backup-name').value.trim();

  // persist selections for any nodes we scanned
  cache.selections = cache.selections || {};
  for (const cb of Array.from(document.querySelectorAll('#tree-container input[type=checkbox]'))) {
    cache.selections[cb.dataset.rel] = !!cb.checked;
  }
  cache.backupFolder = folderInput;
  await ipcRenderer.invoke('backup-save-cache', projectRoot, cache);

  const createBtn = $('#create-backup-btn');
  createBtn.disabled = true;
  createBtn.classList.add('loading');

  // show progress row when creation starts
  showProgressRow();
  $('#progress-fill').style.width = '0%';
  $('#progress-text').innerText = 'Preparing...';

  const resp = await ipcRenderer.invoke('backup-create', projectRoot, { selectedRelPaths: selected, deselectedRelPaths: deselected, backupFolderInput: folderInput, backupName: nameInput });

  createBtn.disabled = false;
  createBtn.classList.remove('loading');

  if (resp && resp.error) {
    alert('Failed to create backup: ' + resp.error);
    $('#progress-text').innerText = 'Failed';
    // hide progress after a short delay
    setTimeout(() => hideProgressRow(), 1200);
    return;
  }

  $('#progress-text').innerText = 'Backup complete: ' + (resp && resp.name ? resp.name : '');
  $('#progress-fill').style.width = '100%';

  // keep progress visible briefly, then hide
  setTimeout(() => hideProgressRow(), 1200);

  // Reveal in file manager if possible
  try { await ipcRenderer.invoke('backup-reveal-in-finder', resp.path); } catch (e) {}

  // refresh backups list (in case folder is same)
  await refreshBackupList();
}

async function refreshBackupList() {
  // Determine folder input (restore input takes precedence)
  const folderInput = $('#restore-folder').value.trim() || $('#backup-folder').value.trim() || '../backup';
  const res = await ipcRenderer.invoke('backup-list-backups', projectRoot, folderInput);

  // If main returned a canonical folder, use it and persist into cache
  if (res && res.folder) {
    $('#restore-folder').value = res.folder;
    $('#backup-folder').value = res.folder;
    cache.backupFolder = res.folder;
    await ipcRenderer.invoke('backup-save-cache', projectRoot, cache);
  }

  const list = (res && res.backups) ? res.backups : [];
  const container = $('#backup-list');
  container.innerHTML = '';

  // If there are no backups, show friendly empty state message + action
  if (!list.length) {
    const empty = document.createElement('div');
    empty.className = 'empty-message muted';
    empty.style.padding = '18px 12px';
    empty.style.textAlign = 'center';
    empty.style.lineHeight = '1.4';
    empty.innerText = "Hmm you don't have any backups yet... Maybe create one first?";

    container.appendChild(empty);

    // Add a small action button that jumps to the Create tab
    const actionRow = document.createElement('div');
    actionRow.style.display = 'flex';
    actionRow.style.justifyContent = 'center';
    actionRow.style.marginTop = '10px';

    const goCreate = document.createElement('button');
    goCreate.className = 'primary-btn';
    goCreate.textContent = 'Create Backup';
    goCreate.addEventListener('click', () => {
      const tabCreate = $('#tab-create');
      if (tabCreate) tabCreate.click();
    });

    actionRow.appendChild(goCreate);
    container.appendChild(actionRow);

    // Clear any selection & disable restore action
    selectedBackupItem = null;
    $all('#backup-list .backup').forEach(n => n.classList.remove('selected'));
    const restoreBtn = $('#restore-btn');
    if (restoreBtn) restoreBtn.disabled = true;

    return;
  }

  // There are backups -> enable restore button
  const restoreBtn = $('#restore-btn');
  if (restoreBtn) restoreBtn.disabled = false;

  for (const b of list) {
    const item = document.createElement('div');
    item.className = 'backup';
    item.dataset.full = b.fullPath;

    const left = document.createElement('div');
    left.textContent = `${b.name} (${(b.size/1024/1024).toFixed(2)} MB)`;
    left.title = b.fullPath;
    left.style.flex = '1';

    const right = document.createElement('div');
    right.style.display = 'flex';
    right.style.gap = '8px';
    right.style.alignItems = 'center';

    const selectBtn = document.createElement('button');
    selectBtn.className = 'small-btn';
    selectBtn.textContent = 'Select';
    selectBtn.addEventListener('click', () => {
      $all('#backup-list .backup').forEach(n => n.classList.remove('selected'));
      item.classList.add('selected');
      selectedBackupItem = b;
      // enable restore when an item is selected
      if (restoreBtn) restoreBtn.disabled = false;
    });

    const revealBtn = document.createElement('button');
    revealBtn.className = 'small-btn';
    revealBtn.textContent = 'Reveal';
    revealBtn.addEventListener('click', async () => {
      try { await ipcRenderer.invoke('backup-reveal-in-finder', b.fullPath); } catch (e) {}
    });

    right.appendChild(selectBtn);
    right.appendChild(revealBtn);

    item.appendChild(left);
    item.appendChild(right);
    container.appendChild(item);
  }
}

async function onRestoreSelected() {
  if (!selectedBackupItem) {
    alert('Please select a backup to restore.');
    return;
  }

  // Show the restore options modal
  showRestoreModal();
}

function showRestoreModal() {
  const modal = $('#restore-modal');
  modal.style.display = 'flex';
  // Reset to default selection
  $('#restore-full').checked = true;
}

function hideRestoreModal() {
  const modal = $('#restore-modal');
  modal.style.display = 'none';
}

async function proceedWithRestore() {
  const restoreMode = document.querySelector('input[name="restore-mode"]:checked').value;
  hideRestoreModal();

  const preserve = cache.preserveOnRestore || ['.auaci', '.venv', 'venv', 'node_modules'];

  applyRestoreState({ state: 'running', message: 'Restoring backup...' });

  const resp = await ipcRenderer.invoke('backup-restore', projectRoot, {
    backupFullPath: selectedBackupItem.fullPath,
    preserveOnRestore: preserve,
    restoreMode: restoreMode
  });

  if (resp && resp.error) {
    alert('Restore failed: ' + resp.error);
    await syncRestoreStatus();
    return;
  } else {
    alert('Restore complete.');
    await syncRestoreStatus();
  }
}

function setupTabs() {
  const tabCreate = $('#tab-create');
  const tabRestore = $('#tab-restore');
  const pageCreate = $('#page-create');
  const pageRestore = $('#page-restore');

  function setActive(tabName) {
    if (tabName === 'create') {
      tabCreate.classList.add('active'); tabCreate.setAttribute('aria-selected', 'true');
      tabRestore.classList.remove('active'); tabRestore.setAttribute('aria-selected', 'false');
      pageCreate.classList.add('active'); pageCreate.setAttribute('aria-hidden', 'false'); pageCreate.style.display = '';
      pageRestore.classList.remove('active'); pageRestore.setAttribute('aria-hidden', 'true'); pageRestore.style.display = 'none';
    } else {
      tabCreate.classList.remove('active'); tabCreate.setAttribute('aria-selected', 'false');
      tabRestore.classList.add('active'); tabRestore.setAttribute('aria-selected', 'true');
      pageCreate.classList.remove('active'); pageCreate.setAttribute('aria-hidden', 'true'); pageCreate.style.display = 'none';
      pageRestore.classList.add('active'); pageRestore.setAttribute('aria-hidden', 'false'); pageRestore.style.display = '';
    }
  }

  // ensure initial hidden state
  $('#page-create').style.display = '';
  $('#page-restore').style.display = 'none';

  tabCreate.addEventListener('click', () => setActive('create'));
  tabRestore.addEventListener('click', () => setActive('restore'));
}

function setupCloseButton() {
  $('#close-btn').addEventListener('click', () => {
    window.close();
  });
}

document.addEventListener('DOMContentLoaded', () => {
  init();

  // wire restore button
  $('#restore-btn').addEventListener('click', onRestoreSelected);

  // wire modal buttons
  $('#modal-close').addEventListener('click', hideRestoreModal);
  $('#modal-cancel').addEventListener('click', hideRestoreModal);
  $('#modal-proceed').addEventListener('click', proceedWithRestore);

  // close modal when clicking overlay
  $('#restore-modal').addEventListener('click', (e) => {
    if (e.target.id === 'restore-modal') {
      hideRestoreModal();
    }
  });

  // drag & drop support for restore folder input
  const restoreInput = $('#restore-folder');
  restoreInput.addEventListener('dragover', (e) => { e.preventDefault(); });
  restoreInput.addEventListener('drop', (e) => {
    e.preventDefault();
    if (e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files.length) {
      const f = e.dataTransfer.files[0];
      restoreInput.value = f.path;
      refreshBackupList();
    }
  });

  window.addEventListener('focus', () => {
    syncRestoreStatus();
  });

  document.addEventListener('visibilitychange', () => {
    if (!document.hidden) syncRestoreStatus();
  });
});