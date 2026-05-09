const { scanDirectoryTree } = require('./scan');
const { renderDirectoryTree } = require('./render');
const { initFolderStates, setFolderState } = require('./state');
const { startSmartRefresh } = require('./smartRefresh');
const { showContextMenu, showFolderPickMenu } = require('./contextMenu');
const { createFile } = require('./buttons/createFile');
const { createFolder } = require('./buttons/createFolder');
const { collapseAll } = require('./buttons/collapseAll');
const { refreshDirectory } = require('./buttons/refresh');
const { revealInFinder } = require('./buttons/revealInFinder');
const { copyPath } = require('./buttons/copyPath');
const fs = require('fs').promises;
const path = require('path');
const { ipcRenderer, clipboard } = require('electron');

const { startClipboardMonitor } = require('./clipboardMonitor'); // <-- new
const { showAutoEdit } = require('./buttons/autoEdit'); // <-- new
const { searchFile } = require('./buttons/searchFile'); // <-- new

// Track the currently selected folder for paste operations
let selectedFolderPath = null;

// Load and maintain the global app config (used for hiddenFiles)
async function loadAppConfig() {
  try {
    const cfg = await ipcRenderer.invoke('get-config');
    window.appConfig = cfg || {};
    const hidden = Array.isArray(cfg && cfg.hiddenFiles) ? cfg.hiddenFiles.slice() : ['.DS_Store'];
    window.hiddenFiles = hidden;
    window.hiddenFilesSet = new Set(hidden.map(n => String(n)));
    console.log('[directory_viewer] loaded appConfig.hiddenFiles:', window.hiddenFiles);
  } catch (err) {
    console.warn('[directory_viewer] failed to load app config, using defaults', err);
    window.appConfig = {};
    window.hiddenFiles = ['.DS_Store'];
    window.hiddenFilesSet = new Set(window.hiddenFiles);
  }
}

// Keep the hiddenFiles set up-to-date when the main process broadcasts config changes
ipcRenderer.on('config-loaded', (event, cfg) => {
  try {
    const hidden = Array.isArray(cfg && cfg.hiddenFiles) ? cfg.hiddenFiles.slice() : ['.DS_Store'];
    window.appConfig = cfg || {};
    window.hiddenFiles = hidden;
    window.hiddenFilesSet = new Set(hidden.map(n => String(n)));
    // re-scan and re-render to apply the new hide list
    (async () => {
      try {
        if (window.projectRoot) {
          const tree = await scanDirectoryTree(window.projectRoot);
          renderDirectoryTree(tree);
        }
      } catch (e) {
        console.error('[directory_viewer] re-render after config-loaded failed', e);
      }
    })();
  } catch (e) {
    console.error('[directory_viewer] error applying broadcasted config', e);
  }
});

async function initDirectoryViewer() {
  const container = document.getElementById('directory-viewer-content');
  if (container) {
    container.innerHTML = '';
  }

  if (!window.projectRoot) {
    window.projectRoot = await ipcRenderer.invoke('get-project-root');
    await initFolderStates(window.projectRoot);
    setFolderState(window.projectRoot, 1);
  }

  // Load global app config early so scan/render can use window.hiddenFiles
  await loadAppConfig();

  const tree = await scanDirectoryTree(window.projectRoot);
  renderDirectoryTree(tree);
  startSmartRefresh(window.projectRoot);

  // Start the clipboard monitor (2s polling). It highlights any displayed items
  // whose name appears in the first 100 characters of the clipboard.
  startClipboardMonitor();
}

function setupEventListeners() {
  document.addEventListener('toggle-folder', async (event) => {
    const { path, state } = event.detail;
    await setFolderState(path, state);
    const tree = await scanDirectoryTree(window.projectRoot);
    renderDirectoryTree(tree);
  });

  // Initialize selected folder to project root
  selectedFolderPath = window.projectRoot;

  // Track folder selection on click
  const directoryViewerContent = document.getElementById('directory-viewer-content');
  directoryViewerContent.addEventListener('click', (e) => {
    const target = e.target.closest('.tree-item');
    if (target) {
      const isDirectory = target.classList.contains('directory');
      if (isDirectory) {
        // Use finalPath for collapsed folders, otherwise use path
        selectedFolderPath = target.dataset.finalPath || target.dataset.path;
      } else {
        // For files, select the parent folder
        selectedFolderPath = path.dirname(target.dataset.path);
      }
    }
  });

  // Handle paste events in directory viewer
  setupDirectoryPasteHandler();

  document.getElementById('create-file-btn').addEventListener('click', async () => {
    await createFile(window.projectRoot);
  });

  document.getElementById('create-folder-btn').addEventListener('click', async () => {
    await createFolder(window.projectRoot);
  });

  document.getElementById('collapse-all-btn').addEventListener('click', async () => {
    await collapseAll(window.projectRoot);
  });

  document.getElementById('refresh-btn').addEventListener('click', async () => {
    await refreshDirectory(window.projectRoot);
  });

  // Ensure there's an Auto Edit button next to the refresh button (if refresh exists),
  // otherwise try to add it into the directory-viewer. The button will be wired to open the Auto Edit popup.
  function ensureAutoEditButton() {
    let btn = document.getElementById('auto-edit-btn');
    if (btn) return btn;

    const refreshBtn = document.getElementById('refresh-btn');

    btn = document.createElement('button');
    btn.id = 'auto-edit-btn';
    btn.title = 'Auto edit';

    // Button styling: match other toolbar buttons (no white background box).
    // Keep minimal inline styling (transparent background, no border) so global CSS/styles for toolbar buttons apply.
    Object.assign(btn.style, {
      display: 'inline-flex',
      alignItems: 'center',
      justifyContent: 'center',
      gap: '6px',
      padding: '6px',
      borderRadius: '4px',
      border: 'none',         // removed visible border
      background: 'transparent', // no white background box
      color: '#000',
      cursor: 'pointer',
      marginRight: '6px',
      boxSizing: 'border-box'
    });

    // Icon image (asset path corrected to directory_viewer)
    const img = document.createElement('img');
    img.alt = 'Auto edit';
    img.draggable = false;
    try {
      const assetPath = path.join(__dirname, '..', '..', 'assets', 'directory_viewer', 'auto-edit.png');
      const normalized = process.platform === 'win32' ? assetPath.replace(/\\/g, '/') : assetPath;
      img.src = `file://${normalized}`;
    } catch (err) {
      // fallback: no image, label will remain
      img.src = '';
    }
    Object.assign(img.style, { width: '18px', height: '18px', display: 'block' });

    // Text label (for accessibility / fallback)
    const label = document.createElement('span');
    label.textContent = ''; // keep empty so only icon shows if present
    Object.assign(label.style, { fontSize: '13px', color: '#000' });

    btn.appendChild(img);
    btn.appendChild(label);

    // Insert after refresh button if possible, otherwise into directory-viewer
    if (refreshBtn && refreshBtn.parentNode) {
      refreshBtn.parentNode.insertBefore(btn, refreshBtn.nextSibling);
    } else {
      const directoryViewer = document.getElementById('directory-viewer');
      if (directoryViewer) {
        // place at start of directory viewer as fallback
        directoryViewer.insertBefore(btn, directoryViewer.firstChild);
      } else if (document.body) {
        document.body.appendChild(btn);
      }
    }

    return btn;
  }

  const autoBtn = ensureAutoEditButton();
  if (autoBtn) {
    autoBtn.addEventListener('click', async (e) => {
      e.stopPropagation();
      try {
        await showAutoEdit(window.projectRoot);
      } catch (err) {
        console.error('Failed to open Auto Edit popup:', err);
      }
    });
  }

  // Ensure there's a Search button next to the Auto Edit button
  function ensureSearchButton() {
    let btn = document.getElementById('search-btn');
    if (btn) return btn;

    const autoEditBtn = document.getElementById('auto-edit-btn');

    btn = document.createElement('button');
    btn.id = 'search-btn';
    btn.title = 'Search files';

    // Button styling: match other toolbar buttons
    Object.assign(btn.style, {
      display: 'inline-flex',
      alignItems: 'center',
      justifyContent: 'center',
      gap: '6px',
      padding: '6px',
      borderRadius: '4px',
      border: 'none',
      background: 'transparent',
      color: '#000',
      cursor: 'pointer',
      marginRight: '6px',
      boxSizing: 'border-box'
    });

    // Icon image
    const img = document.createElement('img');
    img.alt = 'Search files';
    img.draggable = false;
    try {
      const assetPath = path.join(__dirname, '..', '..', 'assets', 'directory_viewer', 'search.png');
      const normalized = process.platform === 'win32' ? assetPath.replace(/\\/g, '/') : assetPath;
      img.src = `file://${normalized}`;
    } catch (err) {
      img.src = '';
    }
    Object.assign(img.style, { width: '18px', height: '18px', display: 'block' });

    // Text label
    const label = document.createElement('span');
    label.textContent = '';
    Object.assign(label.style, { fontSize: '13px', color: '#000' });

    btn.appendChild(img);
    btn.appendChild(label);

    // Insert after auto edit button if possible
    if (autoEditBtn && autoEditBtn.parentNode) {
      autoEditBtn.parentNode.insertBefore(btn, autoEditBtn.nextSibling);
    } else {
      const directoryViewer = document.getElementById('directory-viewer');
      if (directoryViewer) {
        directoryViewer.insertBefore(btn, directoryViewer.firstChild);
      } else if (document.body) {
        document.body.appendChild(btn);
      }
    }

    return btn;
  }

  const searchBtn = ensureSearchButton();
  if (searchBtn) {
    searchBtn.addEventListener('click', async (e) => {
      e.stopPropagation();
      try {
        // Open search with project root (empty relative path = '.')
        await searchFile(window.projectRoot, '');
      } catch (err) {
        console.error('Failed to open Search File popup:', err);
      }
    });
  }


  document.getElementById('directory-viewer').addEventListener('contextmenu', async (e) => {
    e.preventDefault();
    const target = e.target.closest('.tree-item');
    let targetPath = window.projectRoot;
    let isDirectory = true;

    if (target) {
      // Prefer finalPath if present (represents the last folder in a collapsed chain)
      targetPath = target.dataset.finalPath || target.dataset.path;
      isDirectory = target.classList.contains('directory');
    }

    // If the user right-clicked a compressed (collapsed) directory row like "a/b/c",
    // first show a disambiguation menu to choose the intended folder from the chain.
    if (target && isDirectory && target.dataset && target.dataset.collapsed === '1') {
      try {
        const picked = await showFolderPickMenu(window.projectRoot, target, e.clientX, e.clientY);
        if (picked) {
          // Show the normal context menu for the specific folder that was chosen
          showContextMenu(window.projectRoot, picked, true, e.clientX, e.clientY, target);
          return;
        } else {
          // Cancelled by clicking outside
          return;
        }
      } catch (err) {
        console.error('Folder pick failed:', err);
        // fall through to normal menu with default targetPath
      }
    }

    // Pass the clicked element to the context menu so invisible fillers are highlighted correctly.
    showContextMenu(window.projectRoot, targetPath, isDirectory, e.clientX, e.clientY, target);
  });

  // directoryViewerContent already declared above, reuse it
  let draggedItem = null;
  let dragGhostEl = null;
  let dropPreviewEl = null; // legacy inline preview (not used for floating)
  let dropPreviewForPath = null;
  let hoveredFolderEl = null;
  let floatingPreviewEl = null;

  directoryViewerContent.addEventListener('dragstart', (e) => {
    const target = e.target.closest('.tree-item');
    if (target) {
      window.draggedItem = target; // Set global reference
      target.classList.add('dragging');
      const filePath = target.dataset.path;
      e.dataTransfer.setData('text/plain', filePath);
      e.dataTransfer.setData('source', filePath);
      e.dataTransfer.setData('application/x-internal-drag', 'true');
      window.isDirectoryViewDrag = true;
      fs.appendFile('/tmp/events.log', `[${new Date().toISOString()}] Dragstart: ${filePath}\n`);
      e.dataTransfer.effectAllowed = 'move';
      e.dataTransfer.dropEffect = 'move';

      // Create a compact drag image using the visible label text
      try {
        const labelEl = target.querySelector('.name');
        const labelText = labelEl ? (labelEl.textContent || path.basename(filePath)) : path.basename(filePath);
        const ghost = document.createElement('div');
        ghost.textContent = labelText;
        ghost.style.position = 'fixed';
        ghost.style.top = '-1000px';
        ghost.style.left = '-1000px';
        ghost.style.fontFamily = 'Consolas, "Courier New", monospace';
        ghost.style.fontSize = '12px';
        ghost.style.color = '#111827';
        ghost.style.padding = '2px 6px';
        ghost.style.background = '#eef2ff';
        ghost.style.border = '1px solid #c7d2fe';
        ghost.style.borderRadius = '4px';
        ghost.style.boxShadow = '0 1px 2px rgba(0,0,0,0.08)';
        ghost.style.pointerEvents = 'none';
        document.body.appendChild(ghost);
        dragGhostEl = ghost;
        // Offset slightly from cursor
        e.dataTransfer.setDragImage(ghost, 8, 12);
      } catch (_) {}
    }
  });

  directoryViewerContent.addEventListener('dragend', (e) => {
    if (window.draggedItem) {
      window.draggedItem.classList.remove('dragging');
      window.draggedItem = null; // Clear reference
    }
    if (hoveredFolderEl) {
      try { hoveredFolderEl.classList.remove('highlighted'); } catch (_) {}
    }
    hoveredFolderEl = null;
    if (dropPreviewEl && dropPreviewEl.parentNode) {
      dropPreviewEl.parentNode.removeChild(dropPreviewEl);
    }
    dropPreviewEl = null;
    dropPreviewForPath = null;
    if (floatingPreviewEl && floatingPreviewEl.parentNode) {
      floatingPreviewEl.parentNode.removeChild(floatingPreviewEl);
    }
    floatingPreviewEl = null;
    if (dragGhostEl && dragGhostEl.parentNode) {
      dragGhostEl.parentNode.removeChild(dragGhostEl);
    }
    dragGhostEl = null;
    window.isDirectoryViewDrag = false;
    fs.appendFile('/tmp/events.log', `[${new Date().toISOString()}] Dragend\n`);
  });

  directoryViewerContent.addEventListener('dragover', (e) => {
    e.preventDefault();
    e.dataTransfer.dropEffect = 'move';

    const candidate = e.target.closest('.tree-item');

    // Resolve source path from our own drag state first (safer than reading dataTransfer during dragover)
    const sourcePath = (window.draggedItem && window.draggedItem.dataset && window.draggedItem.dataset.path)
      || e.dataTransfer.getData('text/plain')
      || '';

    // Highlight if hovering a folder (ignore invisible fillers)
    if (candidate && candidate.classList.contains('directory') && !candidate.classList.contains('invisible-root-filler')) {
      if (hoveredFolderEl && hoveredFolderEl !== candidate) {
        try { hoveredFolderEl.classList.remove('highlighted'); } catch (_) {}
      }
      hoveredFolderEl = candidate;
      hoveredFolderEl.classList.add('highlighted');

      // Create or update floating preview (only if source exists)
      if (sourcePath) {
        const targetPath = hoveredFolderEl.dataset.finalPath || hoveredFolderEl.dataset.path || window.projectRoot;
        const name = path.basename(sourcePath);
        const rel = path.relative(window.projectRoot, targetPath) || '';
        const previewText = (rel ? (rel + path.sep) : '') + name + ' ?';

        if (!floatingPreviewEl) {
          floatingPreviewEl = document.createElement('div');
          floatingPreviewEl.className = 'floating-drop-preview';
          floatingPreviewEl.style.position = 'fixed';
          floatingPreviewEl.style.zIndex = '2147483000';
          floatingPreviewEl.style.pointerEvents = 'none';
          floatingPreviewEl.style.fontFamily = 'Consolas, "Courier New", monospace';
          floatingPreviewEl.style.fontSize = '12px';
          floatingPreviewEl.style.color = '#4b5563';
          floatingPreviewEl.style.background = 'rgba(255,255,255,0.9)';
          floatingPreviewEl.style.border = '1px solid #e5e7eb';
          floatingPreviewEl.style.borderRadius = '4px';
          floatingPreviewEl.style.padding = '2px 6px';
          floatingPreviewEl.style.boxShadow = '0 1px 2px rgba(0,0,0,0.08)';
          document.body.appendChild(floatingPreviewEl);
        }
        floatingPreviewEl.textContent = previewText;
        // Position slightly below the cursor (and below our drag image)
        const offsetX = 8;
        const offsetY = 22;
        floatingPreviewEl.style.left = (e.clientX + offsetX) + 'px';
        floatingPreviewEl.style.top = (e.clientY + offsetY) + 'px';
      }
    } else {
      if (hoveredFolderEl) {
        try { hoveredFolderEl.classList.remove('highlighted'); } catch (_) {}
      }
      hoveredFolderEl = null;
      if (floatingPreviewEl && floatingPreviewEl.parentNode) {
        floatingPreviewEl.parentNode.removeChild(floatingPreviewEl);
      }
      floatingPreviewEl = null;
    }
  });

  // dragenter is noisy across child elements; highlighting is handled in dragover
  directoryViewerContent.addEventListener('dragenter', (e) => {
    // no-op
  });

  // dragleave is noisy; cleanup happens on drop/dragend or when hover target changes in dragover
  directoryViewerContent.addEventListener('dragleave', (e) => {
    // no-op
  });

  directoryViewerContent.addEventListener('drop', async (e) => {
    e.preventDefault();
    const sourcePath = e.dataTransfer.getData('text/plain');
    const target = e.target.closest('.tree-item');
    let targetPath = window.projectRoot;
    let isDirectory = true;

    if (target) {
      targetPath = target.dataset.finalPath || target.dataset.path;
      isDirectory = target.classList.contains('directory');
    } else if (e.target.id !== 'directory-viewer-content') {
      return;
    }

    // Clean preview and highlight
    if (hoveredFolderEl) {
      try { hoveredFolderEl.classList.remove('highlighted'); } catch (_) {}
    }
    hoveredFolderEl = null;
    if (dropPreviewEl && dropPreviewEl.parentNode) {
      dropPreviewEl.parentNode.removeChild(dropPreviewEl);
    }
    dropPreviewEl = null;
    dropPreviewForPath = null;
    if (floatingPreviewEl && floatingPreviewEl.parentNode) {
      floatingPreviewEl.parentNode.removeChild(floatingPreviewEl);
    }
    floatingPreviewEl = null;

    if (!isDirectory) {
      // Silently ignore drops onto non-directory targets
      return;
    }

    if (sourcePath === targetPath) {
      return;
    }

    if (targetPath !== window.projectRoot && sourcePath.startsWith(targetPath + path.sep)) {
      // Silently ignore attempts to move a folder into its own subdirectory
      return;
    }

    try {
      const newPath = path.join(targetPath, path.basename(sourcePath));
      await fs.access(sourcePath);
      await fs.rename(sourcePath, newPath);
      const tree = await scanDirectoryTree(window.projectRoot);
      document.getElementById('directory-viewer-content').innerHTML = '';
      renderDirectoryTree(tree, [newPath]);
    } catch (err) {
      if (err.code !== 'ENOENT') {
        console.error('Failed to move item:', err);
        fs.appendFile('/tmp/events.log', `[${new Date().toISOString()}] Failed to move item ${sourcePath} to ${targetPath}: ${err.message}\n`);
      }
      const tree = await scanDirectoryTree(window.projectRoot);
      document.getElementById('directory-viewer-content').innerHTML = '';
      renderDirectoryTree(tree);
    }
  });

  // The dblclick handler that opened files has been removed.
  // Single-click preview behavior is handled by the renderer (dispatches 'preview-file').
  // Directory viewer no longer opens files on double-click here; editor will handle preview events.
  // (This keeps behavior consistent and centralized in the editor code.)
  //
  // Rest of drag/drop and context menu behavior remains unchanged.

  const directoryViewer = document.getElementById('directory-viewer');
  directoryViewer.addEventListener('dragover', (e) => {
    if (e.target.id === 'directory-viewer-content') {
      e.preventDefault();
      e.dataTransfer.dropEffect = 'move';
    }
  });

  directoryViewer.addEventListener('dragenter', (e) => {
    if (e.target.id === 'directory-viewer-content') {
      e.target.classList.add('highlighted');
    }
  });

  directoryViewer.addEventListener('dragleave', (e) => {
    if (e.target.id === 'directory-viewer-content') {
      e.target.classList.remove('highlighted');
    }
  });

  directoryViewer.addEventListener('drop', async (e) => {
    if (e.target.id !== 'directory-viewer-content') {
      return;
    }
    e.preventDefault();
    e.target.classList.remove('highlighted');
    if (hoveredFolderEl) {
      try { hoveredFolderEl.classList.remove('highlighted'); } catch (_) {}
    }
    hoveredFolderEl = null;
    if (dropPreviewEl && dropPreviewEl.parentNode) {
      dropPreviewEl.parentNode.removeChild(dropPreviewEl);
    }
    dropPreviewEl = null;
    dropPreviewForPath = null;
    if (floatingPreviewEl && floatingPreviewEl.parentNode) {
      floatingPreviewEl.parentNode.removeChild(floatingPreviewEl);
    }
    floatingPreviewEl = null;
    const sourcePath = e.dataTransfer.getData('text/plain');
    const targetPath = window.projectRoot;

    if (sourcePath === targetPath) {
      return;
    }

    if (path.dirname(sourcePath) === targetPath) {
      return;
    }

    try {
      const newPath = path.join(targetPath, path.basename(sourcePath));
      await fs.access(sourcePath);
      await fs.rename(sourcePath, newPath);
      // removed addNewItem(newPath) — no purple highlight or cooldown anymore
      const tree = await scanDirectoryTree(window.projectRoot);
      document.getElementById('directory-viewer-content').innerHTML = '';
      renderDirectoryTree(tree, [newPath]);
    } catch (err) {
      if (err.code !== 'ENOENT') {
        console.error('Failed to move item:', err);
        fs.appendFile('/tmp/events.log', `[${new Date().toISOString()}] Failed to move item ${sourcePath} to ${targetPath}: ${err.message}\n`);
      }
      const tree = await scanDirectoryTree(window.projectRoot);
      document.getElementById('directory-viewer-content').innerHTML = '';
      renderDirectoryTree(tree);
    }
  });
}

/**
 * Check if a path exists
 */
async function pathExists(filePath) {
  try {
    await fs.access(filePath);
    return true;
  } catch {
    return false;
  }
}

/**
 * Setup paste handler for directory viewer
 * When user pastes files/folders, show confirmation popup and copy them to selected folder
 */
function setupDirectoryPasteHandler() {
  const directoryViewer = document.getElementById('directory-viewer');
  if (!directoryViewer) return;

  directoryViewer.addEventListener('paste', async (e) => {
    e.preventDefault();
    e.stopPropagation();

    // Get pasted files from clipboard via IPC
    const result = await ipcRenderer.invoke('get-pasted-files');
    
    // Build list of items to process
    let itemsToProcess = [];
    
    if (result && result.files && result.files.length > 0) {
      itemsToProcess = result.files;
    } else if (result && result.clipboardData) {
      // Native binary didn't find files, but we have clipboard data
      // Try to extract file/folder paths from public.file-url
      const fileUrl = result.clipboardData['public.file-url'];
      if (fileUrl && fileUrl.startsWith('file://')) {
        try {
          // Decode the file URL to get the path
          const urlPath = decodeURIComponent(fileUrl.replace('file://', ''));
          // Remove trailing slash if present
          const cleanPath = urlPath.endsWith('/') ? urlPath.slice(0, -1) : urlPath;
          
          if (await pathExists(cleanPath)) {
            console.log(`[paste] Extracted path from file-url: ${cleanPath}`);
            itemsToProcess = [{ path: cleanPath, name: path.basename(cleanPath) }];
          }
        } catch (err) {
          console.error(`[paste] Failed to parse file-url:`, err);
        }
      }
      
      // Also check text/uri-list format (can contain multiple file:// URLs)
      if (itemsToProcess.length === 0 && result.clipboardData.availableTypes && 
          result.clipboardData.availableTypes.includes('text/uri-list')) {
        const plainText = result.clipboardData['text/plain'] || '';
        if (plainText.includes('file://')) {
          const urls = plainText.split('\n').filter(line => line.startsWith('file://'));
          for (const url of urls) {
            try {
              const urlPath = decodeURIComponent(url.replace('file://', '').trim());
              const cleanPath = urlPath.endsWith('/') ? urlPath.slice(0, -1) : urlPath;
              if (await pathExists(cleanPath)) {
                itemsToProcess.push({ path: cleanPath, name: path.basename(cleanPath) });
              }
            } catch (_) {}
          }
        }
      }
    }
    
    if (itemsToProcess.length === 0) {
      return;
    }

    const targetFolder = selectedFolderPath || window.projectRoot;

    // Categorize items as files or folders
    const itemsInfo = await Promise.all(itemsToProcess.map(async (item) => {
      try {
        const stat = await fs.stat(item.path);
        return {
          path: item.path,
          name: item.name || path.basename(item.path),
          isDirectory: stat.isDirectory()
        };
      } catch {
        return null;
      }
    }));

    const validItems = itemsInfo.filter(Boolean);
    if (validItems.length === 0) return;

    // Show confirmation popup
    const confirmed = await showPasteConfirmationPopup(validItems, targetFolder);
    if (!confirmed) return;

    // Copy items to target folder
    try {
      for (const item of validItems) {
        const destPath = path.join(targetFolder, item.name);
        
        // Check if destination already exists
        try {
          await fs.access(destPath);
          // Destination exists, skip or handle conflict
          console.warn(`[paste] Destination already exists: ${destPath}`);
          continue;
        } catch {
          // Destination doesn't exist, proceed
        }

        if (item.isDirectory) {
          await copyDirectoryRecursive(item.path, destPath);
        } else {
          await fs.copyFile(item.path, destPath);
        }
      }

      // Refresh directory tree
      const tree = await scanDirectoryTree(window.projectRoot);
      renderDirectoryTree(tree);
      
      console.log(`[paste] Successfully pasted ${validItems.length} item(s) to ${targetFolder}`);
    } catch (err) {
      console.error('[paste] Failed to paste items:', err);
      alert(`Failed to paste: ${err.message}`);
    }
  });

  // Also listen for keyboard paste (Cmd+V / Ctrl+V) when directory viewer is focused
  directoryViewer.addEventListener('keydown', async (e) => {
    if ((e.metaKey || e.ctrlKey) && e.key === 'v') {
      // Trigger paste event
      directoryViewer.dispatchEvent(new ClipboardEvent('paste', {
        bubbles: true,
        cancelable: true
      }));
    }
  });

  // Make directory viewer focusable
  directoryViewer.setAttribute('tabindex', '0');
}

/**
 * Recursively copy a directory
 */
async function copyDirectoryRecursive(src, dest) {
  await fs.mkdir(dest, { recursive: true });
  const entries = await fs.readdir(src, { withFileTypes: true });
  
  for (const entry of entries) {
    const srcPath = path.join(src, entry.name);
    const destPath = path.join(dest, entry.name);
    
    if (entry.isDirectory()) {
      await copyDirectoryRecursive(srcPath, destPath);
    } else {
      await fs.copyFile(srcPath, destPath);
    }
  }
}

/**
 * Show confirmation popup for paste operation
 */
async function showPasteConfirmationPopup(items, targetFolder) {
  return new Promise((resolve) => {
    // Create overlay
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
      zIndex: '10000'
    });

    // Create popup container
    const popup = document.createElement('div');
    Object.assign(popup.style, {
      background: '#fff',
      borderRadius: '10px',
      padding: '20px 24px',
      maxWidth: '500px',
      width: '90%',
      maxHeight: '80vh',
      overflow: 'auto',
      boxShadow: '0 10px 40px rgba(0,0,0,0.2)'
    });

    // Title
    const title = document.createElement('div');
    title.textContent = 'Paste Confirmation';
    Object.assign(title.style, {
      fontSize: '16px',
      fontWeight: '600',
      color: '#111',
      marginBottom: '12px'
    });
    popup.appendChild(title);

    // Build item list description
    const files = items.filter(i => !i.isDirectory);
    const folders = items.filter(i => i.isDirectory);
    
    let itemDescription = '';
    if (files.length > 0 && folders.length > 0) {
      itemDescription = `${files.length} file(s) and ${folders.length} folder(s)`;
    } else if (files.length > 0) {
      itemDescription = files.length === 1 ? `file "${files[0].name}"` : `${files.length} files`;
    } else {
      itemDescription = folders.length === 1 ? `folder "${folders[0].name}"` : `${folders.length} folders`;
    }

    // Message
    const message = document.createElement('div');
    Object.assign(message.style, {
      fontSize: '14px',
      color: '#4b5563',
      marginBottom: '16px',
      lineHeight: '1.5'
    });

    // Get relative path for display
    const relativeTarget = path.relative(window.projectRoot, targetFolder) || '.';
    message.innerHTML = `Do you want to paste ${itemDescription} into folder <strong>"${relativeTarget}"</strong>?`;
    popup.appendChild(message);

    // Item list
    if (items.length > 0) {
      const listContainer = document.createElement('div');
      Object.assign(listContainer.style, {
        background: '#f9fafb',
        border: '1px solid #e5e7eb',
        borderRadius: '6px',
        padding: '10px',
        marginBottom: '16px',
        maxHeight: '150px',
        overflowY: 'auto'
      });

      items.forEach(item => {
        const itemDiv = document.createElement('div');
        Object.assign(itemDiv.style, {
          fontSize: '13px',
          color: '#374151',
          padding: '4px 0',
          display: 'flex',
          alignItems: 'center',
          gap: '6px'
        });
        
        const icon = document.createElement('span');
        icon.textContent = item.isDirectory ? '📁' : '📄';
        itemDiv.appendChild(icon);
        
        const name = document.createElement('span');
        name.textContent = item.name;
        name.style.wordBreak = 'break-all';
        itemDiv.appendChild(name);
        
        listContainer.appendChild(itemDiv);
      });

      popup.appendChild(listContainer);
    }

    // Buttons
    const buttonContainer = document.createElement('div');
    Object.assign(buttonContainer.style, {
      display: 'flex',
      gap: '10px',
      justifyContent: 'flex-end'
    });

    const cancelBtn = document.createElement('button');
    cancelBtn.textContent = 'Cancel';
    Object.assign(cancelBtn.style, {
      padding: '8px 16px',
      borderRadius: '6px',
      fontSize: '13px',
      cursor: 'pointer',
      border: 'none',
      background: '#f3f4f6',
      color: '#374151'
    });
    cancelBtn.addEventListener('click', () => {
      overlay.remove();
      resolve(false);
    });

    const confirmBtn = document.createElement('button');
    confirmBtn.textContent = 'Paste';
    Object.assign(confirmBtn.style, {
      padding: '8px 16px',
      borderRadius: '6px',
      fontSize: '13px',
      cursor: 'pointer',
      border: 'none',
      background: '#3b82f6',
      color: '#fff'
    });
    confirmBtn.addEventListener('click', () => {
      overlay.remove();
      resolve(true);
    });

    buttonContainer.appendChild(cancelBtn);
    buttonContainer.appendChild(confirmBtn);
    popup.appendChild(buttonContainer);

    overlay.appendChild(popup);
    document.body.appendChild(overlay);

    // Close on overlay click
    overlay.addEventListener('click', (e) => {
      if (e.target === overlay) {
        overlay.remove();
        resolve(false);
      }
    });

    // Close on Escape
    const onKeyDown = (e) => {
      if (e.key === 'Escape') {
        overlay.remove();
        window.removeEventListener('keydown', onKeyDown);
        resolve(false);
      }
    };
    window.addEventListener('keydown', onKeyDown);

    // Focus confirm button
    confirmBtn.focus();
  });
}

module.exports = { initDirectoryViewer, setupEventListeners };